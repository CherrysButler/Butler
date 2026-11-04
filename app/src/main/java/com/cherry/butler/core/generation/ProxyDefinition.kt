package com.cherry.butler.core.generation

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.ChatRepository
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.Variants
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.network.ApiError
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A character's definition read from the prompt Janitor assembles for a proxy. A creator can
 * hide the definition on the page and still allow proxies; a proxy chat is then sent the whole
 * definition, so it is the person's to read. Butler asks `/generateAlpha` for a normal proxy
 * prompt (§18.1: on the proxy path it only assembles), keeps the payload, and stops before
 * anything goes to a proxy: no model is called, nothing is spent.
 *
 * It asks in the person's own chat with the character, as a message would: the chat's last
 * lines and one more user line, sent in this request only (nothing is added to the chat).
 * With no chat there's nothing to ask in; the person starts one first.
 */
@Singleton
class ProxyDefinition @Inject constructor(
    db: ButlerDatabase,
    private val chats: ChatRepository,
    private val profiles: ProfileRepository,
    private val transport: GenerationTransport,
) {
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()

    /** What a proxy prompt says the character is. */
    data class Definition(val personality: String, val scenario: String, val exampleDialogs: String)

    /** The definition, from the proxy prompt Janitor assembles for a message in [chatId]. */
    suspend fun read(chatId: Long): Definition {
        val (system, who) = assemble(chatId)
        return Prompt.definition(system, who)
    }

    /** The system prompt Janitor assembles for [chatId], and the name it filled `{{user}}` with. */
    private suspend fun assemble(chatId: Long): Pair<String, String> {
        val chat = chatDao.get(chatId)?.takeIf { it.detailLoaded } ?: chats.refreshChat(chatId)
        val history = Variants.collapse(messageDao.observeTranscriptOnce(chatId)).filter { it.serverId != null }.takeLast(HISTORY)
        val profile = profiles.profile()
        val played = profiles.playedPersona(chat, history)
        // A normal proxy prompt, whatever the person chats with now: their own proxy when they
        // have one saved, else a stand-in address no request is ever sent to.
        val saved = profile.config?.get("proxyConfigurations")?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
        val userConfig = if (saved != null) profiles.inputsFor(profile, com.cherry.butler.core.data.Writer.Proxy(saved)).first
        else GenerationEnvelope.userConfig(
            profileConfig = JsonObject((profile.config ?: JsonObject(emptyMap())) + mapOf("api" to JsonPrimitive("openai"), "open_ai_mode" to JsonPrimitive("proxy"))),
            reverseProxyKey = "",
            reverseProxyUrl = STAND_IN,
            routerEnabled = false,
        )
        val envelope = GenerationEnvelope.build(
            chatId = chat.id,
            characterId = chat.characterId,
            userId = chat.userId.ifEmpty { profile.id },
            summary = null,
            summaryChatId = null,
            history = history,
            profile = EnvelopeProfile(
                id = profile.id, name = profile.name, userName = profile.userName,
                userAppearance = played.appearance, defaultAppearance = chat.defaultPersonaAppearance ?: profile.appearance.orEmpty(),
            ),
            userConfig = userConfig,
            mode = GenerateMode.New,
            clientPlatform = JanitorConfig.GENERATION_CLIENT_PLATFORM,
            // The message: in this request only, never saved to the chat.
            extraUserLine = MESSAGE,
            persona = played.persona,
            knownPersonas = profiles.personas(),
        )
        var payload: JsonObject? = null
        // Kept on its way to the proxy, and stopped there.
        val keep = ProxyTarget(url = STAND_IN, key = "") { p -> payload = p; throw Kept() }
        try {
            transport.generate(envelope, keep).collect { }
        } catch (e: Throwable) {
            if (payload == null) throw e
        }
        val system = payload?.get("messages")?.jsonArray?.firstOrNull()?.jsonObject
            ?.takeIf { it["role"]?.jsonPrimitive?.contentOrNull == "system" }
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?: throw ApiError.Api(code = 200, janitorCode = "BUTLER_NO_PROMPT", serverMessage = "Janitor didn't hand back a proxy prompt for this character.")
        return system to (played.persona?.name ?: profile.name.ifBlank { profile.userName })
    }

    private class Kept : RuntimeException("kept")

    /** Reads the sections Janitor wraps a character in (captured 2026-09-23 and 2026-10-05). */
    internal object Prompt {
        /** The definition in [system], with `{{user}}` put back where Janitor wrote [userName]. */
        fun definition(system: String, userName: String?): Definition {
            val persona = Regex("<([^<>\\n]{1,120})'s Persona>(.*?)</\\1's Persona>", RegexOption.DOT_MATCHES_ALL).find(system)?.groupValues?.get(2)
                ?: throw ApiError.Api(code = 200, janitorCode = "BUTLER_NO_PERSONA", serverMessage = "Janitor's proxy prompt didn't carry this character's definition.")
            fun section(tag: String) = Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(system)?.groupValues?.get(1).orEmpty()
            fun back(text: String): String {
                val t = text.trim()
                val name = userName?.trim()?.takeIf { it.length >= 2 } ?: return t
                return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])").replace(t, "{{user}}")
            }
            return Definition(
                personality = back(persona),
                scenario = back(section("Scenario")),
                exampleDialogs = back(section("example_dialogs")),
            )
        }
    }

    private companion object {
        /** Where a proxy would be, for a person with none saved. Nothing is ever sent there. */
        const val STAND_IN = "https://proxy.invalid/v1/chat/completions"
        /** The chat's last lines sent along, as a message would carry them. */
        const val HISTORY = 4
        const val MESSAGE = "Hello."
    }
}
