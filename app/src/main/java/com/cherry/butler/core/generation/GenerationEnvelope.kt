package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.PronounsDto
import com.cherry.butler.core.util.IsoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The wire values of `generateMode` (docs/JANITOR_API.md §19.2). */
enum class GenerateMode(val wire: String) {
    New("NEW"),
    Alternative("ALTERNATIVE"),
    Continue("CONTINUE"),
    SummaryFull("SUMMARY_FULL"),
    /** The user's next line, written or rewritten for them; see [GenerationEnvelope.build]'s `draft`. */
    Suggestion("SUGGESTION"),
}

/**
 * The `profile` block: who the user is in this chat. `userAppearance` is the active
 * persona's appearance, not the profile's `about_me` — the capture proved the two differ.
 */
data class EnvelopeProfile(
    val id: String,
    val name: String,
    val userName: String,
    val userAppearance: String,
    /** The profile's own appearance, for when a persona plays and the profile is listed beside it. */
    val defaultAppearance: String = userAppearance,
)

/**
 * Builds the `POST /mobile/generateAlpha` body exactly as the official client sends it
 * (docs/JANITOR_API.md §18.2). Every key here was observed on the wire; the builder is the single
 * place the shape lives so a server change is a one-file fix.
 *
 * The caller supplies `userConfig` already assembled from the profile's `config` — this
 * class never reads or logs it, because it carries the user's proxy key.
 */
object GenerationEnvelope {

    fun build(
        chatId: Long,
        characterId: String,
        userId: String,
        summary: String?,
        summaryChatId: Long?,
        history: List<MessageEntity>,
        profile: EnvelopeProfile,
        userConfig: JsonObject,
        mode: GenerateMode,
        clientPlatform: String,
        memoryReplacesHistory: Boolean? = null,
        forceRefetch: Set<String> = emptySet(),
        /**
         * With [GenerateMode.Suggestion]: the user's draft, sent as a trailing user line with
         * no id ("" asks for a line from scratch, text asks for that text rewritten) beside
         * `suggestionMode: "write"`. Janitor writes the instruction itself, on JLLM and when
         * it assembles a proxy's prompt alike (captured on the website, 2026-10-05).
         */
        draft: String? = null,
        /** A user line after the chat, in this request only (Highlights asks its question here on JLLM). */
        extraUserLine: String? = null,
        /**
         * The persona being played now, or null for the profile (the default persona).
         * Janitor fills `{{user}}` from `chat.persona_id`, so it is this one's id that goes
         * there, not the persona the chat was started with (tried against `/generateAlpha`,
         * 2026-10-06).
         */
        persona: PersonaDto? = null,
        /**
         * The user's personas, to name every one the history speaks as: Janitor labels each
         * user line by looking its `persona_id` up in `profiles[]`, and a line it can't find
         * is put down to the profile.
         */
        knownPersonas: List<PersonaDto> = emptyList(),
    ): JsonObject = sortedLikeTheWebsite(buildJsonObject {
        val userLines = history.filter { !it.isBot && it.serverId != null }
        val usedIds = userLines.mapNotNullTo(LinkedHashSet()) { it.personaId }.apply { persona?.let { add(it.id) } }
        val used = usedIds.mapNotNull { id -> if (id == persona?.id) persona else knownPersonas.firstOrNull { it.id == id } }
        // The profile is listed when it is being played, or spoke a line the history still has.
        val profileSpeaks = persona == null || userLines.any { it.personaId == null }
        put("chat", buildJsonObject {
            put("id", chatId)
            put("character_id", characterId)
            if (persona != null) put("persona_id", persona.id)
            put("user_id", userId)
            put("summary", summary.orEmpty())
            if (summaryChatId != null) put("summary_chat_id", summaryChatId)
        })
        // The website fills `{{user}}` in the history itself, with the name being played.
        val userName = (persona?.name ?: profile.name).takeIf { it.isNotBlank() }
        fun filled(text: String) = userName?.let { USER_MACRO.replace(text, Regex.escapeReplacement(it)) } ?: text
        put("chatMessages", buildJsonArray {
            for (m in history) {
                // Only server-confirmed rows have ids the server can resolve.
                val serverId = m.serverId ?: continue
                add(buildJsonObject {
                    put("id", serverId)
                    put("chat_id", m.chatId)
                    // The website names the character on its lines only, not on the user's.
                    if (m.isBot) put("character_id", characterId)
                    put("created_at", IsoTime.format(m.createdAt))
                    put("is_bot", m.isBot)
                    put("is_main", m.isMain)
                    put("message", filled(m.text))
                    if (!m.isBot && m.personaId != null) put("persona_id", m.personaId)
                })
            }
            if (extraUserLine != null) add(buildJsonObject {
                put("chat_id", chatId)
                put("is_bot", false)
                put("is_main", true)
                put("message", extraUserLine)
            })
            if (draft != null) add(buildJsonObject {
                put("chat_id", chatId)
                put("is_bot", false)
                put("is_main", true)
                put("message", draft)
            })
        })
        put("profile", buildJsonObject {
            put("id", profile.id)
            put("name", profile.name)
            put("user_name", profile.userName)
            // The profile's appearance rides here only while the profile is the one playing.
            if (persona == null) put("user_appearance", profile.userAppearance)
        })
        if (used.isNotEmpty()) {
            put("personas", buildJsonArray {
                for (p in used) add(buildJsonObject {
                    put("appearance", p.appearance.orEmpty())
                    put("id", p.id)
                    put("name", p.name)
                    put("pronouns", p.pronounsJson())
                    put("user_id", userId)
                })
            })
        }
        put("profiles", buildJsonArray {
            if (profileSpeaks || used.isEmpty()) add(buildJsonObject {
                put("id", profile.id)
                put("name", profile.name)
                put("user_name", profile.userName)
                put("appearance", if (persona == null) profile.userAppearance else profile.defaultAppearance)
                put("type", "profile")
            })
            for (p in used) add(buildJsonObject {
                put("appearance", p.appearance.orEmpty())
                put("id", p.id)
                put("name", p.name)
                put("pronouns", p.pronounsJson())
                put("type", "persona")
            })
        })
        put("userConfig", userConfig)
        put("generateMode", mode.wire)
        put("generateType", "CHAT")
        if (draft != null) put("suggestionMode", "write")
        put("clientPlatform", clientPlatform)
        put("forcedPromptGenerationCacheRefetch", buildJsonObject {
            for (k in listOf("character", "chat", "profile", "script")) put(k, k in forceRefetch)
        })
        if (memoryReplacesHistory != null) put("memoryReplacesHistory", memoryReplacesHistory)
    }) as JsonObject

    /**
     * The website sends its body with every object's keys sorted, `_` before letters and case
     * aside (`claude_jailbreak_prompt` before `claudeApiKey`, `openAIKey` before `openAiModel`;
     * captured 2026-10-07). Butler's body is sorted the same way, nested objects included.
     */
    private fun sortedLikeTheWebsite(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(
            e.keys.sortedWith(compareBy<String> { it.lowercase().replace('_', ' ') }.thenBy { it })
                .associateWithTo(LinkedHashMap()) { sortedLikeTheWebsite(e.getValue(it)) },
        )
        is JsonArray -> JsonArray(e.map(::sortedLikeTheWebsite))
        else -> e
    }

    /**
     * The keys of the profile's `config` the website sends to `/generateAlpha` (captured
     * 2026-10-06). Everything else stays home: the full config carried every saved proxy's
     * key and the reader's colours, and that larger body was turned away by Janitor's
     * firewall where the website's was not.
     */
    private val USER_MACRO = Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE)

    /**
     * The `generation_settings` the website sends when the account has saved none of them
     * (captured 2026-10-07, a test account that had changed only `enable_short_responses`).
     * Saved values go on top. Settings shows these same values as the defaults.
     */
    val WEB_GENERATION_DEFAULTS: JsonObject = buildJsonObject {
        put("context_length", 50000)
        put("enable_reasoning", true)
        put("enable_reasoning_chat", false)
        put("enable_router_temperature", false)
        put("enable_short_responses", false)
        put("max_new_token", 0)
        put("prefill_enabled", false)
        put("prefill_text", "")
        put("temperature", 1)
    }

    private val SENT_CONFIG_KEYS = setOf(
        "allow_mobile_nsfw", "api", "bad_words", "claude_jailbreak_prompt", "claudeModel",
        "generation_settings", "llm_prompt", "open_ai_jailbreak_prompt", "open_ai_mode",
        "open_ai_reverse_proxy", "openAiModel", "proxy_global_prompt",
    )

    /**
     * `userConfig` = the sent part of the profile's `config` plus the six keys the official
     * client adds (docs/JANITOR_API.md §18.2). The proxy key is threaded through as an element
     * and never stringified anywhere but into the request body.
     */
    private fun PersonaDto.pronounsJson(): JsonElement =
        pronouns?.let { Json.encodeToJsonElement(PronounsDto.serializer(), it) } ?: JsonNull

    fun userConfig(
        profileConfig: JsonObject,
        reverseProxyKey: String?,
        reverseProxyUrl: String?,
        routerEnabled: Boolean,
        allowMobileNsfw: Boolean = false,
    ): JsonObject = buildJsonObject {
        // A fresh account's config is `{}` until AI settings are first saved; Janitor (and
        // the website) treat that as JLLM, so the envelope says so rather than saying nothing.
        if ("api" !in profileConfig) {
            put("api", "janitor")
            put("open_ai_mode", "api_key")
        }
        for ((k, v) in profileConfig) if (k in SENT_CONFIG_KEYS) put(k, v)
        // Janitor keeps only what the user changed; the website sends its defaults under that.
        val stored = profileConfig["generation_settings"] as? JsonObject ?: JsonObject(emptyMap())
        put("generation_settings", JsonObject(WEB_GENERATION_DEFAULTS + stored))
        put("allow_mobile_nsfw", allowMobileNsfw)
        put("reverseProxyKey", reverseProxyKey?.let(::JsonPrimitive) ?: JsonNull)
        if (reverseProxyUrl != null) put("open_ai_reverse_proxy", reverseProxyUrl)
        put("openAIKey", JsonNull as JsonElement)
        put("claudeApiKey", JsonNull as JsonElement)
        put("text_streaming", true)
        put("janitor_router_enabled", routerEnabled)
    }
}
