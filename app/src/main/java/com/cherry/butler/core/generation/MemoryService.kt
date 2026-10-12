package com.cherry.butler.core.generation

import android.util.Log
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.MemoryThoughts
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.ThinkSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat memory: the summary Janitor folds into every prompt.
 *
 * Summarizing is an ordinary generation with `generateMode: SUMMARY_FULL` on whichever
 * transport is active; the text that comes back is the summary, saved with
 * `PATCH /chats/{id} {summary, summary_chat_id}` where `summary_chat_id` is the last
 * message it covers (docs/JANITOR_API.md §19.2). Nothing is written to the transcript.
 *
 * A compact (Compact mode, [Compaction]) is the same generation asked differently: all but the
 * newest messages folded into the summary before it, saved as covering only up to the last
 * message folded, so the newest stay verbatim. One memory run per chat at a time, summary or
 * compact; a run that fails or is stopped saves nothing. What the model thought on the way is
 * kept beside the summary ([MemoryThoughts]). Both are written by the model chosen for memory
 * (Settings › Model, "Summaries use"): the chat's own, or another proxy set or JLLM, so a
 * cheaper or longer-context model can keep the memory while another plays the character.
 */
@Singleton
class MemoryService @Inject constructor(
    private val db: ButlerDatabase,
    private val chatRemote: ChatRemoteSource,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val prefs: MemoryPrefs,
    private val gauge: ContextGauge,
    private val thoughts: MemoryThoughts,
    private val writerPrefs: com.cherry.butler.core.data.WriterPrefs,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Long, Job>()

    enum class Kind { Summary, Compact }

    /** A memory being written, per chat, with the model's thought so far; gone once seen. */
    data class Run(
        val chatId: Long,
        val text: String,
        val thinking: String = "",
        val kind: Kind = Kind.Summary,
        val error: ApiError? = null,
        val done: Boolean = false,
    )

    private val _runs = MutableStateFlow<Map<Long, Run>>(emptyMap())
    val runs: StateFlow<Map<Long, Run>> = _runs.asStateFlow()

    fun isRunning(chatId: Long): Boolean = running[chatId]?.isCompleted == false

    fun isCompacting(chatId: Long): Boolean = isRunning(chatId) && _runs.value[chatId]?.kind == Kind.Compact

    /** Writes a fresh summary of the whole chat. A second call while a memory run is going is ignored. */
    fun summarize(chatId: Long) {
        start(chatId, Kind.Summary) { writeSummary(chatId) }
    }

    /**
     * Folds all but the newest messages into the memory now, if there is enough to fold.
     * A second call while a memory run is going is ignored.
     */
    fun compact(chatId: Long) {
        start(chatId, Kind.Compact) { writeCompact(chatId) }
    }

    /** Waits up to [timeoutMs] for this chat's compact to end, however it ends. */
    suspend fun awaitCompact(chatId: Long, timeoutMs: Long) {
        val job = running[chatId]?.takeIf { isCompacting(chatId) } ?: return
        withTimeoutOrNull(timeoutMs) { job.join() }
    }

    fun cancel(chatId: Long) {
        running.remove(chatId)?.cancel()
        _runs.update { it - chatId }
    }

    /** Before sign-out: nothing goes on writing into a chat that is no longer here. */
    fun cancelAll() {
        running.values.forEach { it.cancel() }
        running.clear()
        _runs.value = emptyMap()
    }

    fun dismiss(chatId: Long) {
        _runs.update { it - chatId }
    }

    /** The user's own words for the summary; the covered range is left as it was. */
    suspend fun saveEdited(chatId: Long, summary: String) {
        val chat = chatDao.get(chatId) ?: return
        chatRemote.patchChat(chatId, buildJsonObject { put("summary", summary) })
        chatDao.setSummary(chatId, summary, chat.summaryChatId)
    }

    /** After a reply lands: a compact when the context is full enough, or else the auto summary. */
    suspend fun afterReply(chatId: Long) {
        if (prefs.compact.value) maybeCompact(chatId)
    }

    /**
     * Compact mode: once the reply just written found the context [MemoryPrefs.compactAt]
     * percent full (Janitor's own reading of it), the chat is compacted in the background, so
     * the next reply is written from the compact. The user can type meanwhile; a send waits
     * for the compact (SendPipeline).
     */
    private fun maybeCompact(chatId: Long) {
        if (!prefs.replacesHistory.value || isRunning(chatId)) return
        val percent = gauge.percent(chatId) ?: return
        if (percent >= prefs.compactAt.value) compact(chatId)
    }

    /**
     * Runs [work] as this chat's memory run unless one is already going. [work] returns the
     * saved summary, or null when it found nothing to do (then no trace is left).
     */
    private fun start(chatId: Long, kind: Kind, work: suspend () -> String?) {
        var started: Job? = null
        running.compute(chatId) { _, existing ->
            if (existing != null && !existing.isCompleted) {
                existing
            } else {
                scope.launch(start = CoroutineStart.LAZY) { run(chatId, kind, work) }.also { started = it }
            }
        }
        started?.start()
    }

    private suspend fun run(chatId: Long, kind: Kind, work: suspend () -> String?) {
        fun failed(e: ApiError) = _runs.update { it + (chatId to (it[chatId]?.copy(error = e) ?: Run(chatId, "", kind = kind, error = e))) }
        try {
            val saved = work()
            _runs.update { if (saved == null) it - chatId else it + (chatId to (it[chatId] ?: Run(chatId, saved, kind = kind)).copy(text = saved, done = true)) }
        } catch (e: ApiError) {
            failed(e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _runs.update { it - chatId }
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "${kind.name.lowercase()} failed", e)
            failed(ApiError.Unknown(e))
        } finally {
            running.remove(chatId, kotlin.coroutines.coroutineContext.job)
        }
    }

    private suspend fun writeSummary(chatId: Long): String {
        _runs.update { it + (chatId to Run(chatId, "", kind = Kind.Summary)) }
        val chat = chatDao.get(chatId) ?: throw ApiError.Unknown(IllegalStateException("unknown chat $chatId"))
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        if (history.isEmpty()) throw ApiError.Api(code = 400, janitorCode = "BUTLER_EMPTY_CHAT", serverMessage = "There is nothing to summarize yet.")
        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, history)
        val (userConfig, target) = profileRepository.inputsFor(profile, writerPrefs.memory.value)
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
            userConfig = userConfig,
            mode = GenerateMode.SummaryFull,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            memoryReplacesHistory = prefs.replacesHistory.value,
            persona = played.persona,
            knownPersonas = profileRepository.personas(),
        )
        val written = write(chatId, Kind.Summary, envelope, target)
        val summary = written.text.trim()
        if (summary.isEmpty()) throw ApiError.Server(code = 502, upstreamStatus = null)
        if (!written.done) Log.w(TAG, "summary stream ended without a terminator; keeping ${summary.length} chars")
        withContext(NonCancellable) {
            val lastId = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).lastOrNull { it.serverId != null }?.serverId
            save(chatId, summary, lastId)
            thoughts.record(chatId, summary, written.thinking, written.thinkingMs)
        }
        return summary
    }

    /**
     * One compact: the messages [Compaction.plan] picks, the previous memory, and Butler's
     * instruction. Janitor gets no summary for this request, so the previous memory reaches the
     * model once, inside the instruction. Saved only when it came back whole and the memory was
     * not changed meanwhile; then it covers up to the last message folded.
     */
    private suspend fun writeCompact(chatId: Long): String? {
        val chat = chatDao.get(chatId) ?: return null
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        val plan = Compaction.plan(history, prefs.compactKeep.value, chat.summaryChatId) ?: return null
        _runs.update { it + (chatId to Run(chatId, "", kind = Kind.Compact)) }

        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, plan.fold)
        val (userConfig, target) = profileRepository.inputsFor(profile, writerPrefs.memory.value)
        val jllm = userConfig["api"]?.let { (it as? JsonPrimitive)?.contentOrNull }.let { it == null || it == "janitor" }
        val instruction = Compaction.prompt(chat.summary)
        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = null,
            summaryChatId = null,
            history = plan.fold,
            profile = EnvelopeProfile(
                id = profile.id, name = profile.name, userName = profile.userName,
                userAppearance = played.appearance, defaultAppearance = chat.defaultPersonaAppearance ?: profile.appearance.orEmpty(),
            ),
            userConfig = userConfig,
            mode = GenerateMode.SummaryFull,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            // On JLLM the instruction rides as one more user line, as Choices asks there.
            extraUserLine = if (jllm) instruction else null,
            persona = played.persona,
            knownPersonas = profileRepository.personas(),
        )
        val proxy = if (jllm) null else target?.then { payload -> askToCompact(payload, instruction) }

        val written = write(chatId, Kind.Compact, envelope, proxy)
        // A compact stands in for every message it folds: one cut short would lose some of them.
        if (!written.done) throw ApiError.Api(code = 0, janitorCode = "BUTLER_COMPACT_CUT", serverMessage = "The compact came back cut short, so it wasn't saved.", retryable = true)
        val summary = Compaction.clean(written.text)
        if (summary.isEmpty()) throw ApiError.Server(code = 502, upstreamStatus = null)

        withContext(NonCancellable) {
            // Edited by hand or refreshed from the website meanwhile: this compact was built on an old memory.
            val now = chatDao.get(chatId)
            if (now == null || now.summary.orEmpty().trim() != chat.summary.orEmpty().trim() || now.summaryChatId != chat.summaryChatId) {
                throw ApiError.Api(code = 0, janitorCode = "BUTLER_MEMORY_CHANGED", serverMessage = "The memory changed while compacting, so the compact wasn't saved.", retryable = true)
            }
            save(chatId, summary, plan.upTo)
            thoughts.record(chatId, summary, written.thinking, written.thinkingMs)
        }
        return summary
    }

    /** The assembled payload plus the compact's instruction, with room for the whole memory. */
    private fun askToCompact(payload: JsonObject, instruction: String): JsonObject {
        val messages = payload["messages"]?.jsonArray.orEmpty() + buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", JsonPrimitive(instruction))
        }
        val cap = runCatching { payload["max_tokens"]?.jsonPrimitive?.intOrNull }.getOrNull()
        val room = if (cap != null && cap < COMPACT_MIN_TOKENS) mapOf("max_tokens" to JsonPrimitive(COMPACT_MIN_TOKENS)) else emptyMap()
        return JsonObject(payload + mapOf("messages" to JsonArray(messages)) + room)
    }

    /** What a memory generation gave: its text, the thought beside it, and whether the stream finished. */
    private class Written(val text: String, val thinking: String, val thinkingMs: Long?, val done: Boolean)

    /** Streams one memory generation into this chat's run, thought and text apart, as a reply's are. */
    private suspend fun write(chatId: Long, kind: Kind, envelope: JsonObject, proxy: ProxyTarget?): Written {
        val text = StringBuilder()
        val thinking = StringBuilder()
        val splitter = ThinkSplitter(text, thinking)
        var done = false
        var thinkStart: Long? = null
        var thinkEnd: Long? = null
        transport.generate(envelope, proxy).collect { event ->
            val now = System.currentTimeMillis()
            when (event) {
                is GenerationEvent.Delta -> {
                    val textBefore = text.length
                    val thoughtBefore = thinking.length
                    splitter.feed(event.text)
                    if (thinkStart == null && thinking.length > thoughtBefore) thinkStart = now
                    if (thinkStart != null && thinkEnd == null && text.length > textBefore && text.substring(textBefore).isNotBlank()) thinkEnd = now
                }
                is GenerationEvent.Reasoning -> {
                    if (thinkStart == null && event.text.isNotEmpty()) thinkStart = now
                    thinking.append(event.text)
                }
                GenerationEvent.Done -> done = true
                // The gauge belongs to the chat's replies, not to this request.
                is GenerationEvent.Meta -> Unit
            }
            _runs.update { it + (chatId to Run(chatId, text.toString(), thinking.toString(), kind)) }
        }
        splitter.finish()
        _runs.update { it + (chatId to Run(chatId, text.toString(), thinking.toString(), kind)) }
        val ms = thinkStart?.let { start -> (thinkEnd ?: System.currentTimeMillis()) - start }
        return Written(text.toString(), thinking.toString(), ms, done)
    }

    private suspend fun save(chatId: Long, summary: String, upTo: Long?) {
        chatRemote.patchChat(chatId, buildJsonObject {
            put("summary", summary)
            if (upTo != null) put("summary_chat_id", upTo) else put("summary_chat_id", JsonNull)
        })
        chatDao.setSummary(chatId, summary, upTo)
    }

    private companion object {
        const val TAG = "MemoryService"
        /** A compact's room to answer when the payload's own cap is lower: a cut compact is not saved. */
        const val COMPACT_MIN_TOKENS = 4_000
    }
}
