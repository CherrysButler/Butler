package com.cherry.butler.core.generation

import android.util.Log
import androidx.room.withTransaction
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.ChatRepository
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.MessageStreamState
import com.cherry.butler.core.data.local.SendJobEntity
import com.cherry.butler.core.data.local.SendJobState
import com.cherry.butler.core.data.remote.ChatRemoteSource
import com.cherry.butler.core.data.remote.dto.PostMessageRequest
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.stream.TagStreamParser
import com.cherry.butler.core.util.IsoTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.cherry.butler.core.background.BackgroundReplies
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outbox engine — ARCHITECTURE.md §8 as code.
 *
 * A send is a durable job that owns the whole sequence the API leaves to the client:
 *
 *     persist ▸ POST user message ▸ generate ▸ POST bot message ▸ PATCH is_main
 *
 * Every step's outcome is on disk before the next begins, so process death resumes at
 * the step that was reached. The rules that make it safe:
 *
 * - **Persist before the network.** [send] writes both rows and the job in one
 *   transaction and returns; the network happens afterwards, on the pipeline's own scope.
 * - **Retry only what is retryable**, with backoff and a persisted `nextAttemptAt` the UI
 *   can count down from. Terminal errors stop and say why.
 * - **Never post twice blindly.** A step whose outcome is unknown re-reads the chat and
 *   adopts a matching server row before it would post again (§8.3).
 * - **Never regenerate over a partial.** A stream that dies after text arrived leaves a
 *   `PARTIAL` row and a terminal job; the recovery is `CONTINUE`, offered by the UI, not a
 *   silent fresh generation that bills the user again (§8.4).
 */
@Singleton
class SendPipeline @Inject constructor(
    private val db: ButlerDatabase,
    private val chatRemote: ChatRemoteSource,
    private val chatRepository: ChatRepository,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val memoryPrefs: MemoryPrefs,
    private val memory: MemoryService,
    private val background: BackgroundReplies,
    private val gauge: ContextGauge,
    private val addons: PromptAddons,
    private val moodTagger: MoodTagger,
) {
    private val messageDao = db.messageDao()
    private val jobDao = db.sendJobDao()
    private val chatDao = db.chatDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = ConcurrentHashMap<Long, Job>()
    private val activeStreams = ConcurrentHashMap<Long, Job>()
    /** When each chat's stream last delivered anything; a frozen app's streams fall silent. */
    private val lastEventAt = ConcurrentHashMap<Long, Long>()
    /** Streams cut by [resumeStalled]: retried as a dropped connection, not "stopped by user". */
    private val stalled = ConcurrentHashMap.newKeySet<Long>()
    /** Bumped by [resumeStalled] so a retry waiting out its backoff goes at once. */
    private val nudges = MutableStateFlow(0)

    /** The in-flight reply per bot row, updated at token rate; Room gets it coalesced. */
    data class LiveReply(
        val text: String,
        val thinking: String,
        val model: String?,
        val contextUsagePercent: Int?,
    )

    private val _live = MutableStateFlow<Map<Long, LiveReply>>(emptyMap())
    val live: StateFlow<Map<Long, LiveReply>> = _live.asStateFlow()

    fun observeJobs(chatId: Long): Flow<List<SendJobEntity>> =
        jobDao.observeActive(chatId, SendJobState.active + SendJobState.FAILED)

    // ---- entry points ---------------------------------------------------------------

    /** Persists the user's words and a placeholder for the reply, then returns. Nothing here touches the network. */
    suspend fun send(chatId: Long, text: String, personaId: String?): Long {
        val chat = chatDao.get(chatId) ?: error("send: unknown chat $chatId")
        val now = System.currentTimeMillis()
        val jobId = db.withTransaction {
            val userLocalId = messageDao.insert(
                MessageEntity(
                    serverId = null, chatId = chatId, isBot = false, isMain = true, text = text,
                    createdAt = now, rating = null, personaId = personaId, generationRequestIds = emptyList(),
                    thinking = null, streamState = null, cachedAt = now,
                ),
            )
            val botLocalId = messageDao.insert(placeholderReply(chatId, now + 1))
            chatDao.touch(chatId, now, text.take(160), delta = 1)
            jobDao.insert(newJob(chat.characterId, chatId, GenerateMode.New, userLocalId, botLocalId, now))
        }
        kick(chatId)
        return jobId
    }

    /** A swipe: a new reply to the same user message. The previous reply stays as an alternate. */
    /** @param guidance a one-off instruction for this reply only (see [GuidedRetry]). */
    suspend fun swipe(chatId: Long, guidance: String? = null): Long {
        val chat = chatDao.get(chatId) ?: error("swipe: unknown chat $chatId")
        val now = System.currentTimeMillis()
        val jobId = db.withTransaction {
            // Replies that were stopped before a word arrived are not alternates; asking
            // again replaces them, so the log never shows two attempts at one turn.
            messageDao.deleteEmptyPartials(chatId)
            // Server rows carry the server's clock; a phone running behind must not sort the
            // new variant above the turn it belongs to.
            val after = (messageDao.observeTranscriptOnce(chatId).lastOrNull()?.createdAt ?: 0L) + 1
            val botLocalId = messageDao.insert(placeholderReply(chatId, maxOf(now, after)))
            jobDao.insert(newJob(chat.characterId, chatId, GenerateMode.Alternative, null, botLocalId, now).copy(guidance = guidance?.trim()?.takeIf { it.isNotEmpty() }))
        }
        kick(chatId)
        return jobId
    }

    /** Asks again after a reply was stopped before a word of it arrived. One row, never two. */
    suspend fun regenerate(chatId: Long, botLocalId: Long): Long {
        val row = messageDao.get(botLocalId)
        if (row != null && row.text.isEmpty() && row.serverId == null) messageDao.delete(botLocalId)
        return swipe(chatId)
    }

    /** Extends an existing reply in place — also the recovery for a `PARTIAL` row. */
    suspend fun continueReply(chatId: Long, botLocalId: Long): Long {
        val chat = chatDao.get(chatId) ?: error("continue: unknown chat $chatId")
        val now = System.currentTimeMillis()
        val jobId = jobDao.insert(newJob(chat.characterId, chatId, GenerateMode.Continue, null, botLocalId, now))
        kick(chatId)
        return jobId
    }

    /**
     * Before lines are deleted: whatever this chat's outbox was doing stops and is dropped,
     * so no job resumes into a row that no longer exists.
     */
    suspend fun abandon(chatId: Long) {
        activeStreams[chatId]?.cancel(CancellationException("abandoned"))
        workers.remove(chatId)?.cancelAndJoin()
        jobDao.cancelAll(chatId, SendJobState.active + SendJobState.FAILED, System.currentTimeMillis())
        val rows = messageDao.observeTranscriptOnce(chatId).map { it.localId }.toSet()
        _live.update { it - rows }
    }

    /** Before sign-out: every stream and worker stops, and nothing resumes into a wiped mirror. */
    suspend fun abandonAll() {
        activeStreams.values.forEach { it.cancel(CancellationException("signed out")) }
        activeStreams.clear()
        workers.values.toList().forEach { it.cancelAndJoin() }
        workers.clear()
        _live.value = emptyMap()
    }

    /** Stops the in-flight generation for a chat. Text already received is kept as a partial. */
    fun stop(chatId: Long) {
        activeStreams[chatId]?.cancel(CancellationException("stopped by user"))
    }

    suspend fun retryNow(jobId: Long) {
        val job = jobDao.get(jobId) ?: return
        if (job.state == SendJobState.WAITING_RETRY || job.state == SendJobState.FAILED) {
            // Both ways into here (a scheduled retry, a hard failure) leave the step in resumeState.
            jobDao.update(
                job.copy(state = SendJobState.WAITING_RETRY, nextAttemptAt = 0, updatedAt = System.currentTimeMillis()),
            )
            kick(job.chatId)
        }
    }

    /** Drops a failed job. The user's message row stays; an empty reply placeholder goes. */
    suspend fun dismiss(jobId: Long) {
        val job = jobDao.get(jobId) ?: return
        db.withTransaction {
            job.botMessageLocalId?.let { id ->
                val row = messageDao.get(id)
                if (row != null && row.serverId == null && row.text.isEmpty()) messageDao.delete(id)
            }
            jobDao.update(job.copy(state = SendJobState.CANCELLED, updatedAt = System.currentTimeMillis()))
        }
    }

    /** On process start: anything mid-flight resumes; anything that was streaming is now partial. */
    fun resumeAll() {
        scope.launch {
            // Done jobs are only history; a day's worth is plenty (failed ones stay: they show a retry).
            runCatching {
                jobDao.pruneFinished(setOf(SendJobState.COMPLETE, SendJobState.CANCELLED), System.currentTimeMillis() - FINISHED_KEEP_MS)
            }
            val jobs = jobDao.allInStates(SendJobState.active)
            for (job in jobs) {
                if (job.state == SendJobState.GENERATING) {
                    val row = job.botMessageLocalId?.let { messageDao.get(it) }
                    job.botMessageLocalId?.let { markPartialIfStreaming(it) }
                    if (row != null && row.text.isNotEmpty()) {
                        // Text arrived before death: do not regenerate over it (§8.4).
                        jobDao.update(job.copy(state = SendJobState.FAILED, lastError = "Interrupted", lastErrorRetryable = false, updatedAt = System.currentTimeMillis()))
                    } else {
                        jobDao.update(job.copy(state = SendJobState.WAITING_RETRY, resumeState = SendJobState.GENERATING, nextAttemptAt = 0, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
            jobs.map { it.chatId }.distinct().forEach { kick(it) }
        }
    }

    /**
     * Butler is back on screen. Some phones freeze it in the background and destroy its
     * connections (Transsion's "Hiber" does, foreground service or not); a stream cut that way
     * can sit silent until a timeout. Anything gone quiet is restarted now, and a retry
     * waiting out its backoff goes immediately.
     */
    fun resumeStalled() {
        val now = System.currentTimeMillis()
        activeStreams.forEach { (chatId, stream) ->
            if (now - (lastEventAt[chatId] ?: now) > STALL_MS) {
                stalled += chatId
                stream.cancel(CancellationException("stalled"))
            }
        }
        nudges.value++
    }

    // ---- the worker -----------------------------------------------------------------

    private fun kick(chatId: Long) {
        workers.compute(chatId) { _, existing ->
            if (existing?.isActive == true) {
                existing
            } else {
                scope.launch { drain(chatId) }.also { job -> job.invokeOnCompletion { background.stopped(chatId) } }
            }
        }
    }

    private suspend fun drain(chatId: Long) {
        while (true) {
            val job = jobDao.firstActive(chatId, SendJobState.active) ?: return
            if (job.state == SendJobState.WAITING_RETRY) {
                val wait = job.nextAttemptAt - System.currentTimeMillis()
                if (wait > 0) {
                    val seen = nudges.value
                    withTimeoutOrNull(wait) { nudges.first { it != seen } }
                }
            }
            runJob(job)
        }
    }

    private suspend fun runJob(initial: SendJobEntity) {
        val step = initial.resumeState ?: firstStep(initial)
        var job = persist(initial.copy(state = step, resumeState = null))
        runCatching { background.started(job) }
        try {
            while (true) {
                job = when (job.state) {
                    SendJobState.POSTING_USER -> postUser(job)
                    SendJobState.GENERATING -> generate(job)
                    SendJobState.POSTING_BOT -> postBot(job)
                    SendJobState.SELECTING -> select(job)
                    else -> {
                        if (job.state == SendJobState.COMPLETE) {
                            runCatching { background.finished(job) }
                            runCatching { memory.maybeAutoSummarize(job.chatId) }
                        }
                        return
                    }
                }
            }
        } catch (e: StoppedByUser) {
            job.botMessageLocalId?.let { markPartialIfStreaming(it) }
            persist(job.copy(state = SendJobState.CANCELLED, lastError = "Stopped", lastErrorRetryable = false))
        } catch (e: CancellationException) {
            // The pipeline's scope is going away; leave the job resumable for next launch.
            persist(job.copy(state = SendJobState.WAITING_RETRY, resumeState = job.state, nextAttemptAt = 0))
            throw e
        } catch (e: ApiError) {
            fail(job, e)
        } catch (e: Throwable) {
            Log.w(TAG, "send job ${job.id} failed unexpectedly", e)
            fail(job, ApiError.Unknown(e))
        }
    }

    private class StoppedByUser : RuntimeException("stopped by user")

    private fun firstStep(job: SendJobEntity): String =
        if (job.userMessageLocalId != null) SendJobState.POSTING_USER else SendJobState.GENERATING

    private suspend fun persist(job: SendJobEntity): SendJobEntity {
        val updated = job.copy(updatedAt = System.currentTimeMillis())
        jobDao.update(updated)
        return updated
    }

    private suspend fun fail(job: SendJobEntity, error: ApiError) {
        // Class and status only: never bodies, which can echo request details.
        Log.w(TAG, "job ${job.id} failed in ${job.state} (attempt ${job.attempt}): ${error.message}", error.cause)
        com.cherry.butler.core.diagnostics.Diagnostics.record("send", "job ${job.id} ${job.state} attempt ${job.attempt}: ${error.javaClass.simpleName} ${error.message.orEmpty().take(120)}")
        val row = job.botMessageLocalId?.let { messageDao.get(it) }
        val partial = job.state == SendJobState.GENERATING && row != null && row.text.isNotEmpty()
        if (partial) {
            // Text arrived and then the stream died: keep it, stop here, offer Continue.
            job.botMessageLocalId?.let { markPartialIfStreaming(it) }
            val failed = persist(job.copy(state = SendJobState.FAILED, lastError = error.userSummary(), lastErrorRetryable = false))
            runCatching { background.failed(failed, error.userSummary(), partial = true, network = error is ApiError.Network) }
            return
        }
        if (job.state == SendJobState.GENERATING) job.botMessageLocalId?.let { unpublish(it) }
        val retryable = error.retryable && job.attempt + 1 < MAX_ATTEMPTS
        if (retryable) {
            if (error is ApiError.Network) runCatching { background.interrupted(job, error.userSummary()) }
            val attempt = job.attempt + 1
            persist(
                job.copy(
                    state = SendJobState.WAITING_RETRY,
                    resumeState = job.state,
                    attempt = attempt,
                    nextAttemptAt = System.currentTimeMillis() + backoffMillis(attempt, (error as? ApiError.RateLimited)?.retryAfterMillis),
                    lastError = error.userSummary(),
                    lastErrorRetryable = true,
                ),
            )
        } else {
            // The step that failed is kept, so a manual retry picks up there (a reply already
            // generated is posted, not generated again).
            val failed = persist(job.copy(state = SendJobState.FAILED, resumeState = job.state, lastError = error.userSummary(), lastErrorRetryable = error.retryable))
            runCatching { background.failed(failed, error.userSummary(), partial = false, network = error is ApiError.Network) }
        }
    }

    // ---- steps ----------------------------------------------------------------------

    private suspend fun postUser(job: SendJobEntity): SendJobEntity {
        val localId = job.userMessageLocalId ?: return persist(job.copy(state = SendJobState.GENERATING))
        val row = messageDao.get(localId) ?: return persist(job.copy(state = SendJobState.GENERATING))
        if (row.serverId == null) {
            // §8.3: a previous attempt may have landed without telling us.
            if (job.attempt > 0) adoptIfLanded(job.chatId, row)
            val fresh = messageDao.get(localId)
            if (fresh?.serverId == null) {
                val posted = chatRemote.postMessage(
                    job.chatId,
                    PostMessageRequest(
                        isBot = false, isMain = true, message = row.text,
                        metadata = buildJsonObject { put("persona_id", row.personaId?.let(::JsonPrimitive) ?: JsonNull) },
                        characterId = job.characterId, chatId = job.chatId,
                    ),
                )
                val serverTime = IsoTime.parseMillis(posted.createdAt) ?: row.createdAt
                db.withTransaction {
                    messageDao.confirm(localId, posted.id, serverTime, System.currentTimeMillis())
                    // Keep the reply placeholder after its question once the server's clock applies.
                    job.botMessageLocalId?.let { messageDao.setLocalCreatedAt(it, serverTime + 1) }
                }
            }
        }
        return persist(job.copy(state = SendJobState.GENERATING))
    }

    private suspend fun generate(job: SendJobEntity): SendJobEntity {
        val botLocalId = job.botMessageLocalId ?: error("generate: job ${job.id} has no reply row")
        val chat0 = chatDao.get(job.chatId) ?: error("generate: unknown chat ${job.chatId}")
        if (!chat0.detailLoaded || chat0.userId.isEmpty()) chatRepository.refreshChat(job.chatId)
        val chat = chatDao.get(job.chatId) ?: chat0
        val mode = GenerateMode.entries.first { it.wire == job.mode }

        var existing = messageDao.get(botLocalId) ?: error("generate: reply row $botLocalId is gone")
        // A partial that never reached the server has to exist there before it can be continued.
        if (mode == GenerateMode.Continue && existing.serverId == null) {
            ensurePosted(job, existing)
            existing = messageDao.get(botLocalId) ?: existing
        }

        val baseHistory = GuidedRetry.apply(historyFor(job.chatId, mode, existing), job.guidance)
        // Butler's specials (Butter mode): on JLLM in the latest user line, on a proxy in the
        // system prompt (below). Never stored.
        val addon = addons.instruction(mode)
        val userConfigNow = profileRepository.userConfig(profileRepository.profile())
        val jllm = userConfigNow["api"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.let { it == null || it == "janitor" }
        val history = if (addon != null && jllm) addons.intoHistory(baseHistory, addon) else baseHistory
        val profile = profileRepository.profile()
        val appearance = profileRepository.playedPersona(chat, history).appearance

        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = chat.summary,
            summaryChatId = chat.summaryChatId,
            history = history,
            profile = EnvelopeProfile(id = profile.id, name = profile.name, userName = profile.userName, userAppearance = appearance),
            userConfig = profileRepository.userConfig(profile),
            mode = mode,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            // Sent on every request while the switch is on and the chat has a summary, as
            // the website does (docs/JANITOR_API.md §27.4); the server then swaps the summarized
            // messages for the summary.
            memoryReplacesHistory = (memoryPrefs.replacesHistory.value && !chat.summary.isNullOrBlank()).takeIf { it },
        )
        val proxy = profileRepository.proxyTarget(profile).let { target ->
            if (addon != null && !jllm) target?.then { payload -> addons.intoPayload(payload, addon) } else target
        }

        // CONTINUE appends to what is already there; everything else starts clean.
        // A continued reply goes on from its tagged copy, so its butter stays whole.
        val text = StringBuilder(if (mode == GenerateMode.Continue) existing.markup ?: existing.text else "")
        val thinking = StringBuilder(if (mode == GenerateMode.Continue) existing.thinking.orEmpty() else "")
        val parser = TagStreamParser(THINK_TAGS)
        var inThink = false
        var requestId: String? = null
        var model: String? = null
        var contextPercent: Int? = null
        var lastWrite = 0L
        var done = false

        messageDao.updateStream(botLocalId, text.toString(), thinking.toString().ifEmpty { null }, MessageStreamState.STREAMING, System.currentTimeMillis())
        publish(botLocalId, text, thinking, model, contextPercent)

        suspend fun flush(force: Boolean) {
            val now = System.currentTimeMillis()
            if (force || now - lastWrite >= PERSIST_INTERVAL_MS) {
                messageDao.updateStream(botLocalId, text.toString(), thinking.toString().ifEmpty { null }, MessageStreamState.STREAMING, now)
                lastWrite = now
            }
        }

        // The stream runs as a child so `stop()` can cancel it alone; a failure inside it
        // propagates out of coroutineScope into runJob's handling, never into the void.
        var stopped = false
        coroutineScope {
            val streamJob = launch {
                lastEventAt[job.chatId] = System.currentTimeMillis()
                transport.generate(envelope, proxy).collect { event ->
                    lastEventAt[job.chatId] = System.currentTimeMillis()
                    when (event) {
                        is GenerationEvent.Meta -> {
                            event.requestId?.let { requestId = it }
                            event.model?.let { model = it }
                            event.contextUsagePercent?.let { contextPercent = it; gauge.record(job.chatId, it) }
                        }
                        is GenerationEvent.Reasoning -> thinking.append(event.text)
                        is GenerationEvent.Delta -> {
                            for (e in parser.feed(event.text)) {
                                when (e) {
                                    is TagStreamParser.Event.Open -> inThink = true
                                    is TagStreamParser.Event.Close -> inThink = false
                                    is TagStreamParser.Event.Text -> if (inThink) thinking.append(e.text) else text.append(e.text)
                                }
                            }
                        }
                        GenerationEvent.Done -> done = true
                    }
                    publish(botLocalId, text, thinking, model, contextPercent)
                    flush(force = false)
                }
                for (e in parser.finish()) if (e is TagStreamParser.Event.Text) text.append(e.text)
            }
            activeStreams[job.chatId] = streamJob
            try {
                streamJob.join()
            } finally {
                activeStreams.remove(job.chatId, streamJob)
                lastEventAt.remove(job.chatId)
            }
            stopped = streamJob.isCancelled
        }
        if (stalled.remove(job.chatId)) throw ApiError.Network(ApiError.Network.Kind.Timeout)
        if (stopped) throw StoppedByUser()
        // A stream that closed without a terminator and without a single token is a failure
        // worth retrying; one that delivered text is a reply.
        if (!done && text.isEmpty()) throw ApiError.Server(code = 502, upstreamStatus = null)

        flush(force = true)
        // Tags off before Janitor sees the reply (if the user chose so), kept here for drawing.
        val (clean, markup) = addons.finish(text.toString())
        messageDao.finishReply(botLocalId, clean, markup, thinking.toString().ifEmpty { null }, System.currentTimeMillis())
        requestId?.let { rid ->
            messageDao.setGenerationRequestIds(botLocalId, (existing.generationRequestIds + rid).distinct())
        }
        unpublish(botLocalId)
        return persist(job.copy(state = SendJobState.POSTING_BOT, generationRequestId = requestId))
    }

    private suspend fun postBot(job: SendJobEntity): SendJobEntity {
        val botLocalId = job.botMessageLocalId ?: return persist(job.copy(state = SendJobState.COMPLETE))
        val row = messageDao.get(botLocalId) ?: return persist(job.copy(state = SendJobState.COMPLETE))
        val mode = GenerateMode.entries.first { it.wire == job.mode }

        if (mode == GenerateMode.Continue && row.serverId != null) {
            // The reply already exists server-side; it is patched with the longer text
            // (docs/JANITOR_API.md §18.4) — the whole object, as the official client sends it.
            chatRemote.patchMessage(job.chatId, row.serverId, fullMessageBody(job, row))
            chatDao.touch(job.chatId, System.currentTimeMillis(), row.text.take(160), delta = 0)
            return persist(job.copy(state = SendJobState.COMPLETE))
        }

        if (row.serverId == null) {
            if (job.attempt > 0) adoptIfLanded(job.chatId, row)
            val fresh = messageDao.get(botLocalId)
            if (fresh?.serverId == null) ensurePosted(job, row)
        }
        chatDao.touch(job.chatId, System.currentTimeMillis(), row.text.take(160), delta = 1)
        return persist(job.copy(state = SendJobState.SELECTING))
    }

    private suspend fun select(job: SendJobEntity): SendJobEntity {
        val botLocalId = job.botMessageLocalId ?: return persist(job.copy(state = SendJobState.COMPLETE))
        val row = messageDao.get(botLocalId) ?: return persist(job.copy(state = SendJobState.COMPLETE))
        val serverId = row.serverId ?: return persist(job.copy(state = SendJobState.COMPLETE))
        chatRemote.patchMessage(job.chatId, serverId, buildJsonObject { put("is_main", true) })
        // One variant of a turn is main. Siblings are the bot rows immediately before this
        // one; the server keeps them main unless told otherwise (see Variants), so it is.
        val transcript = messageDao.observeTranscriptOnce(job.chatId)
        var i = transcript.indexOfFirst { it.localId == botLocalId } - 1
        val siblings = ArrayList<MessageEntity>()
        while (i >= 0 && transcript[i].isBot) siblings += transcript[i--]
        for (sibling in siblings) {
            if (sibling.isMain) {
                // Best effort: a sibling left main server-side is still outranked by the newer
                // reply everywhere Butler reads it.
                sibling.serverId?.let { id ->
                    runCatching { chatRemote.patchMessage(job.chatId, id, buildJsonObject { put("is_main", false) }) }
                }
            }
        }
        db.withTransaction {
            for (sibling in siblings) messageDao.setMain(sibling.localId, false)
            messageDao.setMain(botLocalId, true)
        }
        // A new reply (not a continued one) gets its Highlights now that it is saved and final.
        if (job.mode == GenerateMode.New.wire || job.mode == GenerateMode.Alternative.wire) moodTagger.tagLater(job.chatId, botLocalId)
        return persist(job.copy(state = SendJobState.COMPLETE))
    }

    // ---- helpers --------------------------------------------------------------------

    /** POSTs a bot row and confirms it locally. Shared by the normal path and a continued partial. */
    private suspend fun ensurePosted(job: SendJobEntity, row: MessageEntity) {
        val posted = chatRemote.postMessage(
            job.chatId,
            PostMessageRequest(
                isBot = true, isMain = false, message = if (addons.stripTags.value) com.cherry.butler.core.markdown.SceneTags.strip(row.text) else row.text,
                metadata = buildJsonObject {
                    put("generation_request_ids", buildJsonArray { row.generationRequestIds.forEach { add(JsonPrimitive(it)) } })
                },
                characterId = job.characterId, chatId = job.chatId,
                createdAt = IsoTime.format(row.createdAt), rating = JsonNull,
            ),
        )
        messageDao.confirm(row.localId, posted.id, IsoTime.parseMillis(posted.createdAt) ?: row.createdAt, System.currentTimeMillis())
    }

    private fun fullMessageBody(job: SendJobEntity, row: MessageEntity) = buildJsonObject {
        put("id", row.serverId)
        put("chat_id", job.chatId)
        put("character_id", job.characterId)
        put("created_at", IsoTime.format(row.createdAt))
        put("is_bot", true)
        put("is_main", row.isMain)
        put("message", row.text)
        put("rating", JsonNull)
        put("metadata", buildJsonObject {
            put("generation_request_ids", buildJsonArray { row.generationRequestIds.forEach { add(JsonPrimitive(it)) } })
        })
    }

    /**
     * What the server sees as history. NEW and ALTERNATIVE send everything up to the last
     * user message; CONTINUE sends up to and including the reply being extended. Only
     * confirmed, main rows — alternates the user swiped away are not context.
     */
    private suspend fun historyFor(chatId: Long, mode: GenerateMode, target: MessageEntity): List<MessageEntity> {
        val confirmed = messageDao.observeTranscriptOnce(chatId).filter { it.serverId != null || it.localId == target.localId }
        val all = Variants.collapse(confirmed, prefer = target.localId).filter { it.serverId != null }
        return when (mode) {
            GenerateMode.Continue -> {
                val cut = all.indexOfLast { it.localId == target.localId }
                if (cut >= 0) all.subList(0, cut + 1) else all
            }
            else -> {
                val cut = all.indexOfLast { !it.isBot }
                if (cut >= 0) all.subList(0, cut + 1) else all
            }
        }
    }

    /** §8.3: re-read the chat; if a row matching ours already exists, adopt its id instead of posting. */
    private suspend fun adoptIfLanded(chatId: Long, row: MessageEntity) {
        runCatching { chatRepository.refreshChat(chatId) }
        val match = messageDao.findConfirmedMatch(
            chatId = chatId, isBot = row.isBot, text = row.text,
            from = row.createdAt - ADOPT_WINDOW_MS, to = row.createdAt + ADOPT_WINDOW_MS,
        ) ?: return
        if (match.localId != row.localId) {
            db.withTransaction {
                messageDao.delete(match.localId)
                messageDao.confirm(row.localId, match.serverId!!, match.createdAt, System.currentTimeMillis())
            }
        }
    }

    private suspend fun markPartialIfStreaming(botLocalId: Long) {
        val row = messageDao.get(botLocalId) ?: return
        if (row.streamState == MessageStreamState.STREAMING) {
            // Only a row with nothing in it at all goes; a thought without a reply is kept
            // so a stop never throws away what had already been read.
            if (row.text.isEmpty() && row.thinking.isNullOrEmpty() && row.serverId == null) {
                messageDao.delete(botLocalId)
            } else {
                messageDao.updateStream(botLocalId, row.text, row.thinking, MessageStreamState.PARTIAL, System.currentTimeMillis())
            }
        }
        unpublish(botLocalId)
    }

    private fun newJob(characterId: String, chatId: Long, mode: GenerateMode, userLocalId: Long?, botLocalId: Long?, now: Long) =
        SendJobEntity(
            chatId = chatId, characterId = characterId, userMessageLocalId = userLocalId, botMessageLocalId = botLocalId,
            mode = mode.wire, state = SendJobState.QUEUED, resumeState = null, attempt = 0, nextAttemptAt = 0,
            lastError = null, lastErrorRetryable = false, generationRequestId = null, createdAt = now, updatedAt = now,
        )

    private fun placeholderReply(chatId: Long, at: Long) = MessageEntity(
        serverId = null, chatId = chatId, isBot = true, isMain = false, text = "", createdAt = at,
        rating = null, personaId = null, generationRequestIds = emptyList(), thinking = null,
        streamState = MessageStreamState.STREAMING, cachedAt = at,
    )

    private fun publish(botLocalId: Long, text: StringBuilder, thinking: StringBuilder, model: String?, ctx: Int?) {
        _live.update { it + (botLocalId to LiveReply(text.toString(), thinking.toString(), model, ctx)) }
    }

    private fun unpublish(botLocalId: Long) {
        _live.update { it - botLocalId }
    }

    private fun backoffMillis(attempt: Int, serverHint: Long?): Long {
        if (serverHint != null) return serverHint.coerceIn(1_000L, MAX_BACKOFF_MS)
        return com.cherry.butler.core.network.Backoff.millis(attempt, BASE_BACKOFF_MS, MAX_BACKOFF_MS, jitterFloor = 0.7)
    }

    private companion object {
        /** Silence after which a stream counts as cut when Butler comes back on screen. */
        private const val STALL_MS = 12_000L
        /** How long finished jobs are kept before start-up prunes them. */
        private const val FINISHED_KEEP_MS = 24 * 60 * 60 * 1000L
        const val TAG = "SendPipeline"
        const val MAX_ATTEMPTS = 5
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val PERSIST_INTERVAL_MS = 250L
        const val ADOPT_WINDOW_MS = 10 * 60 * 1000L
        val THINK_TAGS = listOf("think", "thinking")
    }
}

/** One line the UI can show; never the raw exception text. */
fun ApiError.userSummary(): String = when (this) {
    is ApiError.Network -> "No connection"
    is ApiError.RateLimited -> "Rate limited"
    is ApiError.Server -> "Janitor error ($code)"
    is ApiError.Unauthorized -> "Signed out"
    is ApiError.Forbidden -> "Not allowed"
    is ApiError.Serialization -> "Unexpected response"
    is ApiError.Cancelled -> "Stopped"
    is ApiError.Api -> serverMessage?.take(120) ?: janitorCode ?: "Request failed ($code)"
    is ApiError.Unknown -> "Something went wrong"
}
