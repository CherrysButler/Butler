package com.cherry.butler.core.generation

import com.cherry.butler.core.data.WriterPrefs
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.TagStreamParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Write for me" and "Enhance my draft": the user's next line, written by the model.
 *
 * One request for both, as the website sends it: `generateMode: SUGGESTION`,
 * `suggestionMode: "write"`, and the draft as a trailing user line, empty to write from
 * scratch or holding the text to rewrite. Janitor picks and words the instruction itself.
 * On JLLM it answers over the socket; for a proxy it hands back the assembled prompt and the
 * normal proxy path sends it on. Both captured on the website 2026-10-05; Butler's proxy
 * leg of this is not yet exercised on a device.
 *
 * Janitor's own instruction asks for a 4-8 sentence line that "pushes the scene forward":
 * a one-line action came back as a ten-paragraph scene in the third person, and from scratch
 * it sometimes wrote the *character's* line. So the trailing line also carries Butler's
 * guidance ([SCRATCH], [REWRITE]), which Janitor wraps in its template; tried on JLLM
 * 2026-10-05, it holds first person and the draft's length. The same line goes to a proxy,
 * so both paths behave alike.
 *
 * Nothing is posted or stored on Janitor. The text lives here, per chat, until the
 * composer takes it or puts the draft back.
 */
@Singleton
class SuggestionService @Inject constructor(
    db: ButlerDatabase,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val memoryPrefs: MemoryPrefs,
    private val writerPrefs: WriterPrefs,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Long, Job>()

    sealed interface State {
        /** What was in the composer when it was asked; Undo puts this back. */
        val original: String

        data class Writing(override val original: String, val text: String = "") : State
        data class Ready(override val original: String, val text: String) : State
        data class Failed(override val original: String, val message: String) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()

    /**
     * Writes the next line for [draft] (blank: from scratch). [opening] stands in for the
     * opening line's text when an intro was picked on screen but not yet sent.
     */
    fun write(chatId: Long, draft: String, opening: String? = null) {
        running.remove(chatId)?.cancel()
        _states.update { it + (chatId to State.Writing(draft)) }
        running[chatId] = scope.launch {
            val text = StringBuilder()
            var names: (String) -> String = { it }
            val result = runCatching {
                generate(chatId, draft, opening, onNames = { names = it }) { piece ->
                    text.append(piece)
                    _states.update { now ->
                        val w = now[chatId] as? State.Writing ?: return@update now
                        now + (chatId to w.copy(text = names(text.toString()).trimStart()))
                    }
                }
            }
            if (result.exceptionOrNull() is CancellationException) return@launch
            _states.update { now ->
                val w = now[chatId] as? State.Writing ?: return@update now
                // A reply-length limit can end it mid-word; the composer gets whole sentences only.
                val written = endOnSentence(names(text.toString()).trim())
                val next = when {
                    result.isFailure -> State.Failed(w.original, (result.exceptionOrNull() as? ApiError)?.userSummary() ?: "Couldn't write it.")
                    written.isEmpty() -> State.Failed(w.original, "Nothing came back. Try again.")
                    else -> State.Ready(w.original, written)
                }
                now + (chatId to next)
            }
            running.remove(chatId, coroutineContext.job)
        }
    }

    /** Stops writing; what arrived so far is kept, as a stopped reply is. */
    fun stop(chatId: Long) {
        running.remove(chatId)?.cancel()
        _states.update { now ->
            val w = now[chatId] as? State.Writing ?: return@update now
            if (w.text.isBlank()) now - chatId else now + (chatId to State.Ready(w.original, endOnSentence(w.text.trim())))
        }
    }

    fun clear(chatId: Long) {
        running.remove(chatId)?.cancel()
        _states.update { it - chatId }
    }

    private suspend fun generate(
        chatId: Long,
        draft: String,
        opening: String?,
        onNames: ((String) -> String) -> Unit,
        onText: (String) -> Unit,
    ) {
        val chat = chatDao.get(chatId) ?: error("unknown chat $chatId")
        var history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        if (opening != null && history.firstOrNull()?.isBot == true) {
            history = listOf(history.first().copy(text = opening)) + history.drop(1)
        }
        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, history)
        // The user's lines can come from another model than the character's (Settings › Model).
        val inputs = profileRepository.inputsFor(profile, writerPrefs.writer.value)
        // The line lands in the composer as the user's own words, so the macros are filled in.
        val who = played.persona?.name ?: chat.personaName ?: profile.name.ifBlank { profile.userName }
        onNames { it.replace("{{user}}", who, ignoreCase = true).replace("{{char}}", chat.shownName, ignoreCase = true) }

        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = chat.summary,
            summaryChatId = chat.summaryChatId,
            history = history,
            profile = EnvelopeProfile(
                id = profile.id, name = profile.name, userName = profile.userName,
                userAppearance = played.appearance, defaultAppearance = chat.defaultPersonaAppearance ?: profile.appearance.orEmpty(),
            ),
            userConfig = inputs.first,
            mode = GenerateMode.Suggestion,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            memoryReplacesHistory = (memoryPrefs.replacesHistory.value && !chat.summary.isNullOrBlank()).takeIf { it },
            draft = if (draft.isBlank()) SCRATCH else "$draft\n\n$REWRITE",
            persona = played.persona,
            knownPersonas = profileRepository.personas(),
        )

        // A thinking model's reasoning is not part of the line.
        val parser = TagStreamParser(listOf("think", "thinking"))
        var inThink = false
        fun feed(events: Iterable<TagStreamParser.Event>) {
            for (e in events) when (e) {
                is TagStreamParser.Event.Open -> inThink = true
                is TagStreamParser.Event.Close -> inThink = false
                is TagStreamParser.Event.Text -> if (!inThink) onText(e.text)
            }
        }
        transport.generate(envelope, inputs.second).collect { event ->
            if (event is GenerationEvent.Delta) feed(parser.feed(event.text))
        }
        feed(parser.finish())
    }

    companion object {
        private const val ENDS = ".!?…~♡"
        private const val CLOSERS = "\"*'”’)_"

        /**
         * [text] cut back to its last whole sentence, with an asterisk or quote it left open
         * closed again. Text that already ends on a sentence, or has no sentence end at all,
         * comes back as it was.
         */
        fun endOnSentence(text: String): String {
            val t = text.trimEnd()
            if (t.isEmpty()) return t
            var end = t.length
            while (end > 0 && t[end - 1] in CLOSERS) end--
            if (end > 0 && t[end - 1] in ENDS) return t
            val last = t.indexOfLast { it in ENDS }
            if (last < 0) return t
            var cut = last + 1
            while (cut < t.length && t[cut] in CLOSERS) cut++
            val kept = StringBuilder(t.substring(0, cut).trimEnd())
            if (kept.count { it == '*' } % 2 == 1) kept.append('*')
            if (kept.count { it == '"' } % 2 == 1) kept.append('"')
            return kept.toString()
        }

        /** Write for me: the user's next line, theirs and short, never the character's. */
        const val SCRATCH =
            "(Write my next line for me. First person as me, in the story's style, actions in asterisks " +
                "and speech in quotes. React to what just happened and do one clear thing. Two to four " +
                "short sentences, and finish every one: end on a complete sentence. Never write the other " +
                "character's words or reactions.)"

        /** Enhance my draft: the same line, better written, not a longer scene. */
        const val REWRITE =
            "(Rewrite it as my own line, not a scene: first person as me, same tense, keep what I do " +
                "and say and add nothing new, just make it read well. About the same length as my draft, " +
                "a sentence or two longer at most, and finish every sentence: end on a complete one. Never " +
                "write the other character's reaction.)"
    }
}
