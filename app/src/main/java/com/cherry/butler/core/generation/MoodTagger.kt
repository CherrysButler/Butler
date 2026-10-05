package com.cherry.butler.core.generation

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.stream.TagStreamParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Highlights, done after the fact: the reply is written exactly as it would be, then one small
 * follow-up asks the same model which of its sentences carry a mood. Butler wraps those
 * sentences itself, and only where it finds them word for word, so the reply can never be
 * bent to fit a tag (it was, when the instruction rode inside the reply request).
 *
 * On a proxy the follow-up carries only the reply and the question (the payload Janitor
 * assembled is replaced), so it costs about the reply's length in input and a few lines out.
 * On JLLM, where Butler can't shape the prompt, the question rides as one extra line after
 * the chat, in that request only.
 */
@Singleton
class MoodTagger @Inject constructor(
    db: ButlerDatabase,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val memoryPrefs: MemoryPrefs,
    private val addons: PromptAddons,
    private val chatRemote: ChatRemoteSource,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tags the finished reply [botLocalId] in the background, if Highlights is on. Failures are quiet. */
    fun tagLater(chatId: Long, botLocalId: Long) {
        if (!addons.highlights.value || addons.moods.value.isEmpty()) return
        scope.launch { runCatching { tag(chatId, botLocalId) } }
    }

    private suspend fun tag(chatId: Long, botLocalId: Long) {
        val row = messageDao.get(botLocalId) ?: return
        if (row.serverId == null || wordCount(row.text) < MIN_WORDS) return
        val moods = addons.moods.value
        val chat = chatDao.get(chatId) ?: return
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, history)
        val userConfig = profileRepository.userConfig(profile)
        val jllm = userConfig["api"]?.let { (it as? JsonPrimitive)?.contentOrNull }.let { it == null || it == "janitor" }
        val question = question(moods)

        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = chat.summary,
            summaryChatId = chat.summaryChatId,
            history = history,
            profile = EnvelopeProfile(id = profile.id, name = profile.name, userName = profile.userName, userAppearance = played.appearance),
            userConfig = userConfig,
            mode = GenerateMode.New,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            memoryReplacesHistory = (memoryPrefs.replacesHistory.value && !chat.summary.isNullOrBlank()).takeIf { it },
            extraUserLine = if (jllm) "(OOC: Pause the roleplay and do not answer in character. $question The reply is your last message above.)" else null,
        )
        val target = if (jllm) null else profileRepository.proxyTarget(profile)?.then { payload ->
            JsonObject(
                payload + mapOf(
                    "messages" to JsonArray(
                        listOf(
                            buildJsonObject { put("role", "system"); put("content", question) },
                            buildJsonObject { put("role", "user"); put("content", row.text) },
                        ),
                    ),
                    "max_tokens" to JsonPrimitive(400),
                ),
            )
        }
        if (!jllm && target == null) return

        val answer = StringBuilder()
        val parser = TagStreamParser(listOf("think", "thinking"))
        var inThink = false
        transport.generate(envelope, target).collect { event ->
            if (event is GenerationEvent.Delta) for (e in parser.feed(event.text)) when (e) {
                is TagStreamParser.Event.Open -> inThink = true
                is TagStreamParser.Event.Close -> inThink = false
                is TagStreamParser.Event.Text -> if (!inThink) answer.append(e.text)
            }
        }
        for (e in parser.finish()) if (e is TagStreamParser.Event.Text && !inThink) answer.append(e.text)

        val fresh = messageDao.get(botLocalId) ?: return
        val base = fresh.markup ?: fresh.text
        val tagged = apply(base, parse(answer.toString(), moods))
        if (tagged == base) return
        if (addons.stripTags.value) {
            messageDao.setMarkup(botLocalId, tagged)
        } else {
            // Tags go to Janitor too: the reply there is patched with them.
            fresh.serverId?.let { chatRemote.patchMessage(chatId, it, buildJsonObject { put("message", tagged) }) }
            messageDao.finishReply(botLocalId, tagged, null, fresh.thinking, System.currentTimeMillis())
        }
    }

    companion object {
        /** Shorter replies are left alone: a mood in two lines doesn't need marking. */
        const val MIN_WORDS = 60
        const val MAX_PICKS = 4

        fun question(moods: Set<Mood>): String {
            val list = Mood.entries.filter { it in moods }.joinToString(", ") { "${it.tag} (${it.meaning})" }
            return "You are tagging a roleplay reply by mood. Do not rewrite, continue or judge it. From the reply, " +
                "pick at most $MAX_PICKS sentences that clearly carry one of these moods: $list. Copy each sentence " +
                "exactly as written. Answer only with lines like `mood: sentence`. If none fits, answer NONE."
        }

        /** `mood: sentence` lines, kept only for moods that were asked for. */
        fun parse(answer: String, moods: Set<Mood>): List<Pair<Mood, String>> =
            answer.lines().mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) return@mapNotNull null
                val mood = Mood.of(line.substring(0, i).trim().trim('-', '*', '`', ' ').lowercase()) ?: return@mapNotNull null
                val sentence = line.substring(i + 1).trim().trim('`').trim()
                if (mood !in moods || sentence.length < 3) null else mood to sentence
            }.take(MAX_PICKS)

        /**
         * [text] with each pick wrapped in its mood's tags, where the sentence is found as written
         * (or without the quote marks or asterisks the model may have dropped or added) and is not
         * already inside a mood. Anything not found is skipped; the words never change.
         */
        fun apply(text: String, picks: List<Pair<Mood, String>>): String {
            data class Cut(val start: Int, val end: Int, val mood: Mood)
            val cuts = ArrayList<Cut>()
            for ((mood, sentence) in picks) {
                val candidates = listOf(sentence, sentence.trim('"', '“', '”', '*', ' '))
                val (start, len) = candidates.firstNotNullOfOrNull { c ->
                    text.indexOf(c).takeIf { it >= 0 && c.isNotBlank() }?.let { it to c.length }
                } ?: continue
                val end = start + len
                if (cuts.any { start < it.end && end > it.start }) continue
                cuts += Cut(start, end, mood)
            }
            var out = text
            for (c in cuts.sortedByDescending { it.start }) {
                out = out.substring(0, c.start) + "<${c.mood.tag}>" + out.substring(c.start, c.end) + "</${c.mood.tag}>" + out.substring(c.end)
            }
            return out
        }

        private fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
