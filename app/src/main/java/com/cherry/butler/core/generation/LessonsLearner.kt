package com.cherry.butler.core.generation

import com.cherry.butler.core.data.Lesson
import com.cherry.butler.core.data.LessonChange
import com.cherry.butler.core.data.LessonDiff
import com.cherry.butler.core.data.LessonsStore
import com.cherry.butler.core.data.OocNotes
import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.data.OpenRouterOptionsStore
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.data.Writer
import com.cherry.butler.core.data.WriterPrefs
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.network.ButlerUserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lessons (beta), /learn: reads the player's corrections since the last time (their OOC notes,
 * and what they said to change when they asked for a new reply), each with a little of the
 * reply it was about; hands them, with the lessons kept so far and anything said after /learn,
 * to the model that writes summaries; and offers what it proposes as changes to tick before
 * anything is kept. Only the player's own words are learned from, never the model's, so its
 * habits can't feed themselves.
 *
 * Proxies only: the ask has no chat, and JLLM is reached through one.
 */
@Singleton
class LessonsLearner @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    db: ButlerDatabase,
    client: OkHttpClient,
    private val json: Json,
    private val store: LessonsStore,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val writers: WriterPrefs,
    private val openRouter: OpenRouterOptionsStore,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val jobDao = db.sendJobDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    private val http = client.newBuilder()
        .apply { interceptors().clear(); networkInterceptors().clear() }
        .addInterceptor(ButlerUserAgent)
        .cookieJar(okhttp3.CookieJar.NO_COOKIES)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    sealed interface State {
        data object Reading : State
        data class Thinking(val corrections: Int) : State
        /** What it proposes; [upTo] is how far it read, kept once the review is saved. */
        data class Review(val changes: List<LessonChange>, val corrections: Int, val upTo: Long) : State
        /** It read everything new and had nothing to change. */
        data class NothingNew(val corrections: Int) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State?>(null)
    /** The /learn under way, for its sheet; null when there's none. */
    val state: StateFlow<State?> = _state.asStateFlow()

    /** One OOC note or retry ask, with the reply it was about. */
    internal data class Correction(val said: String, val reply: String?, val character: String?, val characterId: String?, val at: Long)

    /** /learn, with [focus] (what was written after it) as what to look for. */
    fun learn(focus: String) {
        if (running?.isActive == true) return
        if (!store.on.value) {
            _state.value = State.Failed("Lessons is a beta: switch it on in Settings › Chat › Lessons first.")
            return
        }
        _state.value = State.Reading
        running = scope.launch {
            runCatching {
                val since = store.learnedUpTo
                val corrections = collect(since)
                val upTo = corrections.maxOfOrNull { it.at } ?: since
                if (corrections.isEmpty() && focus.isBlank()) {
                    _state.value = State.NothingNew(0)
                    store.learnedUpTo = maxOf(since, System.currentTimeMillis() - 1)
                    return@runCatching
                }
                _state.value = State.Thinking(corrections.size)
                val proposed = ask(store.lessons.value, corrections, focus)
                val changes = LessonDiff.between(store.lessons.value, proposed)
                _state.value = if (changes.isEmpty()) State.NothingNew(corrections.size).also { store.learnedUpTo = upTo }
                else State.Review(changes, corrections.size, upTo)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                // Only words written for the reader are shown. Anything else's own message is
                // for a programmer at best (and at worst a shortened class name, see OocNotes).
                _state.value = State.Failed((e as? ApiError.Api)?.serverMessage ?: (e as? ApiError)?.let { "The model couldn't be reached." } ?: "Something went wrong. Try again.")
            }
        }
    }

    /** Keeps the ticked [accepted] changes and remembers how far the chats were read. */
    fun keep(accepted: List<LessonChange>) {
        val review = _state.value as? State.Review ?: return
        store.save(LessonDiff.applied(store.lessons.value, accepted))
        store.learnedUpTo = review.upTo
        _state.value = null
    }

    fun close() {
        running?.cancel()
        _state.value = null
    }

    // ---- reading ---------------------------------------------------------------------

    private suspend fun collect(since: Long): List<Correction> = withContext(Dispatchers.IO) {
        // A first /learn looks back a month, not over everything ever written.
        val from = if (since > 0) since else System.currentTimeMillis() - FIRST_LOOK_BACK_MS
        val names = HashMap<Long, Pair<String?, String?>>()
        suspend fun who(chatId: Long) = names.getOrPut(chatId) {
            chatDao.get(chatId)?.let { it.shownName.takeIf { n -> n.isNotBlank() } to it.characterId } ?: (null to null)
        }
        val out = ArrayList<Correction>()
        for (line in messageDao.userLinesSince(from, MAX_LINES)) {
            val notes = OocNotes.from(line.text)
            if (notes.isEmpty()) continue
            val reply = messageDao.replyBefore(line.chatId, line.createdAt)?.text
            val (name, id) = who(line.chatId)
            notes.forEach { out += Correction(it, reply, name, id, line.createdAt) }
        }
        for (job in jobDao.guidedSince(from)) {
            val said = job.guidance?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: continue
            // The reply asked to be redone: the one before this new attempt.
            val reply = messageDao.replyBefore(job.chatId, job.createdAt)?.text
            val (name, id) = who(job.chatId)
            out += Correction("When asking for a new reply: $said", reply, name, id, job.createdAt)
        }
        out.sortedBy { it.at }.takeLast(MAX_CORRECTIONS)
    }

    // ---- asking ----------------------------------------------------------------------

    private suspend fun ask(current: List<Lesson>, corrections: List<Correction>, focus: String): List<Lesson> = withContext(Dispatchers.IO) {
        val profile = profiles.profile()
        val ai = settings.load()
        // The model you chat with (it has to be good at this); else the one for summaries, which
        // is often a small cheap one (a 1B model answered with broken JSON, 2026-11-04); else any.
        val chatProxy = ai.selectedProxy?.takeIf { ai.provider == com.cherry.butler.core.data.Provider.Proxy }
        val memory = (writers.memory.value as? Writer.Proxy).takeIf { chatProxy == null }
        val proxyId = chatProxy?.let { it.clientId ?: it.id } ?: memory?.id ?: ai.selectedProxy?.let { it.clientId ?: it.id }
            ?: ai.proxies.firstOrNull { it.hasKey }?.let { it.clientId ?: it.id }
        val proxy = proxyId?.let { profiles.proxy(profile, it) }
            ?: throw ApiError.Api(0, janitorCode = "NO_PROXY", serverMessage = "/learn needs a proxy to think with: add one in Settings › Model.", retryable = false)
        val model = memory?.model ?: proxy.model ?: ai.proxies.firstOrNull { proxyId in it.ids }?.model.orEmpty()

        var payload = buildJsonObject {
            put("model", model)
            put("stream", false)
            put("temperature", 0.3)
            // Room for a thinking model to think and still write the notes out whole.
            put("max_tokens", 6_000)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", INSTRUCTION) })
                add(buildJsonObject { put("role", "user"); put("content", Prompt.material(current, corrections, focus)) })
            })
        }
        payload = openRouter.get(proxyId).applyTo(payload, OpenRouterOptions.isOpenRouter(proxy.url))
        // Claude's own API takes another request and answers in another shape ([ClaudeApi]).
        val claude = ClaudeApi.isClaude(proxy.url)
        val request = if (claude) ClaudeApi.post(proxy.url, proxy.key, ClaudeApi.body(payload, thinking = false)) else Request.Builder()
            .url(proxy.url)
            .post(payload.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer ${proxy.key}")
            .header("HTTP-Referer", "https://janitorai.com")
            .header("X-Title", "janitor")
            .build()
        val body = try {
            com.cherry.butler.core.network.PlainProxies.client(http, proxy.url).newCall(request).execute().use { r ->
                if (!r.isSuccessful) {
                    val message = when (r.code) {
                        401, 403 -> "The proxy rejected the key."
                        402 -> "The proxy has no balance left."
                        429 -> "The proxy asked for a pause. Try again in a moment."
                        else -> "The proxy answered ${r.code}."
                    }
                    throw ApiError.Api(r.code, janitorCode = "PROXY_ERROR", serverMessage = message, retryable = false)
                }
                r.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw ApiError.Network(ApiError.Network.Kind.Unknown, e)
        }
        // The last answer, kept to look at when it couldn't be read (files/lessons-last-answer.txt).
        runCatching { java.io.File(context.filesDir, "lessons-last-answer.txt").writeText(body) }
        val choice = runCatching {
            json.parseToJsonElement(body).jsonObject.let { if (claude) ClaudeApi.asOpenAi(it) else it }["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull()
        val text = Prompt.content(choice)
        val cut = (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull == "length"
        if (text == null) throw ApiError.Api(200, janitorCode = "BUTLER_NO_LESSONS", serverMessage = if (cut) "The model spent all its room thinking and wrote no notes. Try again, or pick a model that thinks less for summaries." else "The model's answer was empty. Try again.", retryable = false)
        try {
            Prompt.rules(text, current, corrections)
        } catch (e: ApiError.Api) {
            if (cut) throw ApiError.Api(200, janitorCode = "BUTLER_NO_LESSONS", serverMessage = "The model ran out of room before it finished the notes. Try again, or pick a model that thinks less for summaries.", retryable = false)
            throw e
        }
    }

    /** What the model is told and how its answer is read; plain functions, so they can be tested. */
    internal object Prompt {
        fun material(current: List<Lesson>, corrections: List<Correction>, focus: String): String = buildString {
            append("CURRENT NOTES\n")
            if (current.isEmpty()) append("(none yet)\n")
            current.forEach { l ->
                append("- ").append(l.text)
                if (!l.everywhere) append("  [only for ").append(l.characterName ?: "one character").append("]")
                if (!l.on) append("  [switched off by the player: keep it, don't rewrite it]")
                append('\n')
            }
            if (focus.isNotBlank()) append("\nWHAT THE PLAYER WANTS LEARNED THIS TIME\n").append(focus.trim()).append('\n')
            append("\nTHE PLAYER'S CORRECTIONS, OLDEST FIRST\n")
            if (corrections.isEmpty()) append("(none since last time)\n")
            corrections.forEachIndexed { i, c ->
                append("\n").append(i + 1).append(". ")
                c.character?.let { append("In a chat with ").append(it).append(". ") }
                c.reply?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append("The reply it answers (for context only, never learn from it): \"").append(it.take(REPLY_CLIP).replace("\n", " ")).append(if (it.length > REPLY_CLIP) "…\"" else "\"").append('\n')
                }
                append("   The player said: ").append(c.said).append('\n')
            }
        }

        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val lenient = Json { isLenient = true; allowTrailingComma = true; allowComments = true; ignoreUnknownKeys = true }

        /**
         * The notes in the model's answer: JSON `{"notes":[{"text","character"}]}`, found among
         * whatever else it wrote (its thinking, a code fence, words before and after, trailing
         * commas), or, failing that, a plain list of `- note` lines.
         */
        /**
         * What the model wrote, from a chat-completions `choice`: `message.content` as text, or,
         * as some proxies send it, as a list of parts whose text is joined. Null for none.
         */
        fun content(choice: JsonObject?): String? {
            val content = (choice?.get("message") as? JsonObject)?.get("content")
            val text = when (content) {
                is JsonPrimitive -> content.contentOrNull
                is JsonArray -> content.mapNotNull { part ->
                    ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull ?: (part as? JsonPrimitive)?.contentOrNull
                }.joinToString("")
                else -> null
            }
            return text?.takeIf { it.isNotBlank() }
        }

        fun rules(answer: String, current: List<Lesson>, corrections: List<Correction>): List<Lesson> {
            // Its thinking isn't the answer, closed or not: what follows the last close is.
            var clean = answer.replace(Regex("(?is)<think(ing)?>.*?</think(ing)?>"), "")
            clean.lastIndexOf("</think").takeIf { it >= 0 }?.let { at -> clean = clean.substring(clean.indexOf('>', at) + 1) }
            clean = clean.replace(Regex("```[a-zA-Z]*"), "")
            val root = objects(clean).firstNotNullOfOrNull { candidate ->
                runCatching { lenient.parseToJsonElement(candidate) as? JsonObject }.getOrNull()?.takeIf { it["notes"] != null || it["rules"] != null }
            }
            // Broken JSON (a small model's: a key twice, a list never closed): every "text" in it.
            val salvaged = if (root == null) Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(clean)
                .mapNotNull { m -> runCatching { lenient.parseToJsonElement("\"" + m.groupValues[1] + "\"").jsonPrimitive.content }.getOrNull() }
                .map { it.trim().trimEnd(',', ';').trim() }.filter { it.length >= 6 }.toList() else emptyList()
            val listed = if (root == null && salvaged.isEmpty()) clean.lines().mapNotNull { l -> Regex("""^\s*(?:[-*•]|\d+[.)])\s+(.+)$""").find(l)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"") }.filter { it.length >= 6 } else salvaged
            if (root == null && listed.isEmpty()) throw ApiError.Api(200, janitorCode = "BUTLER_NO_LESSONS", serverMessage = "The model's notes couldn't be read. Try again, or with another model.", retryable = false)
            val ids = (corrections.mapNotNull { c -> c.character?.let { it.lowercase() to (c.characterId to c.character) } } +
                current.mapNotNull { l -> l.characterName?.let { it.lowercase() to (l.characterId to l.characterName) } }).toMap()
            val notes = root?.let { (it["notes"] ?: it["rules"]) as? JsonArray } ?: JsonArray(listed.map { JsonPrimitive(it) })
            return notes.mapNotNull { e ->
                val o = e as? JsonObject
                val text = (o?.get("text") ?: (e as? JsonPrimitive))?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val who = o?.get("character")?.takeIf { it !is JsonNull }?.let { (it as? JsonPrimitive)?.contentOrNull }?.trim()?.lowercase()
                val (id, name) = who?.let { ids[it] } ?: (null to null)
                // A note switched off stays off when it comes back unchanged.
                val off = current.any { !it.on && it.text.trim() == text && it.characterId == id }
                Lesson(text = text.take(MAX_RULE), characterId = id, characterName = name, on = !off)
            }.distinctBy { (it.characterId ?: "") + it.text.lowercase() }.take(MAX_RULES)
        }
    }

    companion object {
        /** Every balanced `{…}` in [text], outermost first, strings respected. */
        internal fun objects(text: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                if (text[i] != '{') { i++; continue }
                var depth = 0
                var inString = false
                var escaped = false
                var end = -1
                for (j in i until text.length) {
                    val ch = text[j]
                    if (inString) {
                        when {
                            escaped -> escaped = false
                            ch == '\\' -> escaped = true
                            ch == '"' -> inString = false
                        }
                        continue
                    }
                    when (ch) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> { depth--; if (depth == 0) { end = j; break } }
                    }
                }
                if (end < 0) break
                out += text.substring(i, end + 1)
                i = end + 1
            }
            return out
        }

        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val FIRST_LOOK_BACK_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_LINES = 2_000
        const val MAX_CORRECTIONS = 80
        const val REPLY_CLIP = 280
        const val MAX_RULES = 15
        const val MAX_RULE = 220

        val INSTRUCTION = """
            You keep a short list of standing notes for an AI that roleplays with one player. The notes are sent with every reply the AI writes, so the player stops having to correct the same things again and again.

            You are given the current notes, sometimes what the player wants learned this time, and the player's own out-of-character corrections since you last looked, each with a clip of the reply it answered (context only).

            Update the notes:
            - Learn only lasting preferences about how the AI should write or behave: style, pacing, length, what never to do, how the player likes to play. Ignore directions for one scene or one moment ("make this scene darker", "skip to the morning", "have her leave now").
            - Learn only from what the player said. Never turn something from a reply into a note.
            - Each note is one short, plain instruction to the AI, at most 20 words, like "Never write the player's actions or dialogue."
            - Merge notes that say the same thing. When a correction contradicts a note, the newer correction wins: rewrite or drop the old note.
            - Keep notes that still hold, word for word. Keep notes marked as switched off exactly as they are.
            - A note is for every character unless the corrections make clear it's about one: then set "character" to that character's name as given.
            - At most 15 notes, the most useful first. Fewer is better than vague.

            Answer with JSON only, no other words:
            {"notes":[{"text":"...","character":null}]}
        """.trimIndent()
    }
}
