package com.cherry.butler.core.generation

import com.cherry.butler.core.data.WriterPrefs
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.Perspective
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.ThinkSplitter
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
 * guidance ([SuggestionPrompt]), which Janitor wraps in its template; tried on JLLM
 * 2026-10-05, it holds the asked person and the draft's length. The same line goes to a
 * proxy, so both paths behave alike. What the Write panel asked for rides on that line too,
 * as a note for this one request.
 *
 * Nothing is posted or stored on Janitor. The text (and the thought, when the model thinks)
 * lives here, per chat, in the Write panel until it is used or put away; a used line stays
 * known so the composer can undo it.
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

    /**
     * What the Write panel asked: its quick picks (by label), the user's own words, and whose
     * voice (null: the draft's own).
     */
    data class Ask(val picks: List<String> = emptyList(), val words: String = "", val perspective: Perspective? = null)

    sealed interface State {
        /** What was in the composer when it was asked; Undo puts this back, Again writes from it. */
        val original: String
        val ask: Ask

        /** A line used before this one was asked and still in the composer; put away, this goes back. */
        val earlier: Used? get() = null

        /** [thinking] grows first when the model thinks; [thoughtMs] is how long, once the line began. */
        data class Writing(
            override val original: String,
            override val ask: Ask,
            val started: Long,
            val text: String = "",
            val thinking: String = "",
            val thoughtMs: Long? = null,
            override val earlier: Used? = null,
        ) : State

        /** Written, in the panel, not yet used. */
        data class Ready(
            override val original: String,
            override val ask: Ask,
            val started: Long,
            val text: String,
            val thinking: String = "",
            val thoughtMs: Long? = null,
            override val earlier: Used? = null,
        ) : State

        data class Failed(override val original: String, override val ask: Ask, val message: String, override val earlier: Used? = null) : State

        /** Taken into the composer: the strip under it offers Undo and Again until it is edited. */
        data class Used(override val original: String, override val ask: Ask, val text: String) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()

    /**
     * Writes the next line for [draft] (blank: from scratch), as [ask] says. [opening] stands
     * in for the opening line's text when an intro was picked on screen but not yet sent.
     */
    fun write(chatId: Long, draft: String, opening: String? = null, ask: Ask = Ask()) {
        running.remove(chatId)?.cancel()
        val before = _states.value[chatId]
        val earlier = before as? State.Used ?: before?.earlier
        _states.update { it + (chatId to State.Writing(draft, ask, started = System.currentTimeMillis(), earlier = earlier)) }
        running[chatId] = scope.launch {
            var text = ""
            var thinkStart: Long? = null
            var thinkEnd: Long? = null
            var names: (String) -> String = { it }
            val result = runCatching {
                generate(chatId, draft, opening, ask, onNames = { names = it }) { line, thinking ->
                    val at = System.currentTimeMillis()
                    if (thinkStart == null && thinking.isNotEmpty()) thinkStart = at
                    if (thinkStart != null && thinkEnd == null && line.isNotBlank()) thinkEnd = at
                    text = line
                    _states.update { now ->
                        val w = now[chatId] as? State.Writing ?: return@update now
                        now + (chatId to w.copy(
                            text = names(line).trimStart(),
                            thinking = names(thinking).trim(),
                            thoughtMs = thinkStart?.let { s -> thinkEnd?.let { it - s } },
                        ))
                    }
                }
            }
            if (result.exceptionOrNull() is CancellationException) return@launch
            _states.update { now ->
                val w = now[chatId] as? State.Writing ?: return@update now
                // A reply-length limit can end it mid-word; the composer gets whole sentences only.
                val written = endOnSentence(names(text).trim())
                val next = when {
                    result.isFailure -> State.Failed(w.original, w.ask, (result.exceptionOrNull() as? ApiError)?.userSummary() ?: "Couldn't write it.", w.earlier)
                    written.isEmpty() -> State.Failed(w.original, w.ask, "Nothing came back. Try again.", w.earlier)
                    else -> State.Ready(w.original, w.ask, w.started, written, w.thinking, w.thoughtMs, w.earlier)
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
            if (w.text.isBlank()) now.putAway(chatId, w)
            else now + (chatId to State.Ready(w.original, w.ask, w.started, endOnSentence(w.text.trim()), w.thinking, w.thoughtMs, w.earlier))
        }
    }

    /** The written line goes into the composer; it is [State.Used] from here. */
    fun use(chatId: Long): State.Used? {
        val ready = _states.value[chatId] as? State.Ready ?: return null
        val used = State.Used(ready.original, ready.ask, ready.text)
        _states.update { it + (chatId to used) }
        return used
    }

    /** The panel closed: what it wrote and nobody used goes, and a line used before keeps its Undo. */
    fun dismiss(chatId: Long) {
        running.remove(chatId)?.cancel()
        _states.update { now ->
            val s = now[chatId] ?: return@update now
            if (s is State.Used) now else now.putAway(chatId, s)
        }
    }

    fun clear(chatId: Long) {
        running.remove(chatId)?.cancel()
        _states.update { it - chatId }
    }

    private fun Map<Long, State>.putAway(chatId: Long, s: State): Map<Long, State> =
        s.earlier?.let { this + (chatId to it) } ?: (this - chatId)

    private suspend fun generate(
        chatId: Long,
        draft: String,
        opening: String?,
        ask: Ask,
        onNames: ((String) -> String) -> Unit,
        /** The line and the thought so far, whole each time: a stray `</think>` moves text already shown. */
        onProgress: (line: String, thinking: String) -> Unit,
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
            draft = SuggestionPrompt.line(
                draft = draft,
                perspective = ask.perspective,
                guidance = SuggestionPrompt.guidance(ask.picks, ask.words, rewrite = draft.isNotBlank()),
                name = who,
                pronouns = played.persona?.pronouns,
            ),
            persona = played.persona,
            knownPersonas = profileRepository.personas(),
        )

        // A thinking model's reasoning is not part of the line; it is kept apart, to be read. The
        // splitter also catches a close with no open, and an open never closed (ThinkSplitter).
        val line = StringBuilder()
        val thought = StringBuilder()
        val splitter = ThinkSplitter(line, thought)
        transport.generate(envelope, inputs.second).collect { event ->
            when (event) {
                is GenerationEvent.Delta -> splitter.feed(event.text)
                is GenerationEvent.Reasoning -> thought.append(event.text)
                else -> return@collect
            }
            onProgress(line.toString(), thought.toString())
        }
        splitter.finish()
        onProgress(line.toString(), thought.toString())
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
    }
}
