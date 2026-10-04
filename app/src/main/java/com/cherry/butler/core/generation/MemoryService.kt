package com.cherry.butler.core.generation

import android.util.Log
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.TagStreamParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
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
 */
@Singleton
class MemoryService @Inject constructor(
    private val db: ButlerDatabase,
    private val chatRemote: ChatRemoteSource,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val prefs: MemoryPrefs,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Long, Job>()

    /** A summary being written, per chat; gone once it is saved or failed. */
    data class Run(val chatId: Long, val text: String, val error: ApiError? = null, val done: Boolean = false)

    private val _runs = MutableStateFlow<Map<Long, Run>>(emptyMap())
    val runs: StateFlow<Map<Long, Run>> = _runs.asStateFlow()

    fun isRunning(chatId: Long): Boolean = running[chatId]?.isActive == true

    /** Writes a fresh summary of the whole chat. A second call while one runs is ignored. */
    fun summarize(chatId: Long) {
        if (isRunning(chatId)) return
        running[chatId] = scope.launch {
            _runs.update { it + (chatId to Run(chatId, "")) }
            try {
                val text = generateSummary(chatId)
                save(chatId, text)
                _runs.update { it + (chatId to Run(chatId, text, done = true)) }
            } catch (e: ApiError) {
                _runs.update { it + (chatId to Run(chatId, it[chatId]?.text.orEmpty(), error = e)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _runs.update { it - chatId }
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "summary failed", e)
                _runs.update { it + (chatId to Run(chatId, it[chatId]?.text.orEmpty(), error = ApiError.Unknown(e))) }
            } finally {
                running.remove(chatId)
            }
        }
    }

    fun cancel(chatId: Long) {
        running.remove(chatId)?.cancel()
        _runs.update { it - chatId }
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

    /**
     * After a reply lands: with both switches on, summarize again once enough of the chat
     * sits past the current summary.
     */
    suspend fun maybeAutoSummarize(chatId: Long) {
        if (!prefs.autoSummarize.value || !prefs.replacesHistory.value || isRunning(chatId)) return
        val chat = chatDao.get(chatId) ?: return
        val confirmed = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        val coveredUpTo = chat.summaryChatId
        val since = if (coveredUpTo == null) confirmed.size else confirmed.count { (it.serverId ?: 0) > coveredUpTo }
        if (since >= MemoryPrefs.AUTO_EVERY) summarize(chatId)
    }

    private suspend fun generateSummary(chatId: Long): String {
        val chat = chatDao.get(chatId) ?: throw ApiError.Unknown(IllegalStateException("unknown chat $chatId"))
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        if (history.isEmpty()) throw ApiError.Api(code = 400, janitorCode = "BUTLER_EMPTY_CHAT", serverMessage = "There is nothing to summarize yet.")
        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, history)
        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = chat.summary,
            summaryChatId = chat.summaryChatId,
            history = history,
            profile = EnvelopeProfile(
                id = profile.id, name = profile.name, userName = profile.userName,
                userAppearance = played.appearance,
            ),
            userConfig = profileRepository.userConfig(profile),
            mode = GenerateMode.SummaryFull,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            memoryReplacesHistory = prefs.replacesHistory.value,
        )
        val text = StringBuilder()
        val parser = TagStreamParser(listOf("think", "thinking"))
        var inThink = false
        var done = false
        transport.generate(envelope, profileRepository.proxyTarget(profile)).collect { event ->
            when (event) {
                is GenerationEvent.Delta -> for (e in parser.feed(event.text)) {
                    when (e) {
                        is TagStreamParser.Event.Open -> inThink = true
                        is TagStreamParser.Event.Close -> inThink = false
                        is TagStreamParser.Event.Text -> if (!inThink) text.append(e.text)
                    }
                }
                GenerationEvent.Done -> done = true
                else -> Unit // reasoning and meta are not part of a summary
            }
            _runs.update { it + (chatId to Run(chatId, text.toString())) }
        }
        for (e in parser.finish()) if (e is TagStreamParser.Event.Text && !inThink) text.append(e.text)
        val summary = text.toString().trim()
        if (summary.isEmpty()) throw ApiError.Server(code = 502, upstreamStatus = null)
        if (!done) Log.w(TAG, "summary stream ended without a terminator; keeping ${summary.length} chars")
        return summary
    }

    private suspend fun save(chatId: Long, summary: String) {
        val lastId = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).lastOrNull { it.serverId != null }?.serverId
        chatRemote.patchChat(chatId, buildJsonObject {
            put("summary", summary)
            if (lastId != null) put("summary_chat_id", lastId) else put("summary_chat_id", JsonNull)
        })
        chatDao.setSummary(chatId, summary, lastId)
    }

    private companion object {
        const val TAG = "MemoryService"
    }
}
