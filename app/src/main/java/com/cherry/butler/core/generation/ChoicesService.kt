package com.cherry.butler.core.generation

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.stream.TagStreamParser
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Four things the user could do next, offered as buttons under the last reply.
 *
 * Janitor assembles the prompt for the chat as it stands (docs/JANITOR_API.md §30); Butler adds one
 * closing instruction and sends it to the user's proxy itself, then reads the answer. Nothing
 * is posted or stored on Janitor: the choices live in memory, tied to the reply they were
 * made for. Needs the proxy path (JLLM never hands back a payload to add to).
 */
@Singleton
class ChoicesService @Inject constructor(
    db: ButlerDatabase,
    private val profileRepository: ProfileRepository,
    private val transport: GenerationTransport,
    private val memoryPrefs: MemoryPrefs,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Long, Job>()

    sealed interface State {
        /** The reply these belong to; anything else on screen means they're stale. */
        val forReply: Long

        data class Loading(override val forReply: Long) : State
        data class Ready(override val forReply: Long, val choices: List<String>) : State
        data class Failed(override val forReply: Long, val message: String) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states.asStateFlow()

    fun request(chatId: Long, forReply: Long) {
        running[chatId]?.cancel()
        _states.update { it + (chatId to State.Loading(forReply)) }
        running[chatId] = scope.launch {
            val result = runCatching { generate(chatId) }
            _states.update { now ->
                // Superseded by a newer request or a dismissal: leave that state alone.
                if ((now[chatId] as? State.Loading)?.forReply != forReply) return@update now
                val next = result.fold(
                    onSuccess = { State.Ready(forReply, it) },
                    onFailure = { State.Failed(forReply, (it as? ApiError)?.userSummary() ?: "Couldn't think of any.") },
                )
                now + (chatId to next)
            }
        }
    }

    fun dismiss(chatId: Long) {
        running.remove(chatId)?.cancel()
        _states.update { it - chatId }
    }

    private suspend fun generate(chatId: Long): List<String> {
        val chat = chatDao.get(chatId) ?: error("unknown chat $chatId")
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }
        if (history.isEmpty()) error("empty chat")
        val profile = profileRepository.profile()
        val played = profileRepository.playedPersona(chat, history)
        val who = played.persona?.name ?: chat.personaName ?: profile.name.ifBlank { profile.userName }

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
            mode = GenerateMode.New,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            memoryReplacesHistory = (memoryPrefs.replacesHistory.value && !chat.summary.isNullOrBlank()).takeIf { it },
        )
        val proxy = profileRepository.proxyTarget(profile)
            ?: throw ApiError.Api(code = 400, janitorCode = "BUTLER_NO_PROXY", serverMessage = "Choices need a proxy selected.")
        val target = proxy.then { payload -> ask(payload, who) }

        val text = StringBuilder()
        val parser = TagStreamParser(listOf("think", "thinking"))
        var inThink = false
        transport.generate(envelope, target).collect { event ->
            if (event is GenerationEvent.Delta) for (e in parser.feed(event.text)) {
                when (e) {
                    is TagStreamParser.Event.Open -> inThink = true
                    is TagStreamParser.Event.Close -> inThink = false
                    is TagStreamParser.Event.Text -> if (!inThink) text.append(e.text)
                }
            }
        }
        for (e in parser.finish()) if (e is TagStreamParser.Event.Text && !inThink) text.append(e.text)
        return parse(text.toString()).ifEmpty { throw ApiError.Server(code = 502, upstreamStatus = null) }
    }

    /** The assembled payload plus the one instruction, with room for a model that thinks first. */
    private fun ask(payload: JsonObject, who: String): JsonObject {
        val messages = payload["messages"]?.jsonArray.orEmpty() + buildJsonObject {
            put("role", JsonPrimitive("user"))
            put("content", JsonPrimitive(instruction(who)))
        }
        return JsonObject(payload + mapOf("messages" to JsonArray(messages), "max_tokens" to JsonPrimitive(1_500)))
    }

    companion object {
        fun instruction(who: String) =
            "[OOC: Pause the roleplay and do not write the next reply. Suggest four different things $who could do or say next, " +
                "each written as $who's own move in the story's style (actions in asterisks, speech in quotes), under 25 words, " +
                "and different from each other in tone and direction. Answer with only the four lines, numbered 1 to 4.]"

        /** "1. *She leaves.*" … → the four lines, numbering and wrapping quotes removed. */
        fun parse(raw: String): List<String> {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val numbered = lines.mapNotNull { Regex("""^(?:\*\*)?\(?([1-9])[.):\]]\s*(.+)$""").find(it)?.groupValues?.get(2) }
            return (numbered.ifEmpty { lines }).map { it.trim().removeSurrounding("**").trim() }.filter { it.isNotEmpty() }.take(4)
        }
    }
}
