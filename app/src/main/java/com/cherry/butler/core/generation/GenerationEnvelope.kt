package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.util.IsoTime
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
    ): JsonObject = buildJsonObject {
        put("chat", buildJsonObject {
            put("id", chatId)
            put("character_id", characterId)
            put("user_id", userId)
            put("summary", summary.orEmpty())
            if (summaryChatId != null) put("summary_chat_id", summaryChatId)
        })
        put("chatMessages", buildJsonArray {
            for (m in history) {
                // Only server-confirmed rows have ids the server can resolve.
                val serverId = m.serverId ?: continue
                add(buildJsonObject {
                    put("id", serverId)
                    put("chat_id", m.chatId)
                    put("character_id", characterId)
                    put("created_at", IsoTime.format(m.createdAt))
                    put("is_bot", m.isBot)
                    put("is_main", m.isMain)
                    put("message", m.text)
                })
            }
        })
        put("profile", buildJsonObject {
            put("id", profile.id)
            put("name", profile.name)
            put("user_name", profile.userName)
            put("user_appearance", profile.userAppearance)
        })
        put("profiles", buildJsonArray {
            add(buildJsonObject {
                put("id", profile.id)
                put("name", profile.name)
                put("user_name", profile.userName)
                put("appearance", profile.userAppearance)
                put("type", "profile")
            })
        })
        put("userConfig", userConfig)
        put("generateMode", mode.wire)
        put("generateType", "CHAT")
        put("clientPlatform", clientPlatform)
        put("forcedPromptGenerationCacheRefetch", buildJsonObject {
            for (k in listOf("character", "chat", "profile", "script")) put(k, k in forceRefetch)
        })
        if (memoryReplacesHistory != null) put("memoryReplacesHistory", memoryReplacesHistory)
    }

    /**
     * `userConfig` = the profile's `config` plus the six keys the official client adds
     * (docs/JANITOR_API.md §18.2). The proxy key is threaded through as an element and never
     * stringified anywhere but into the request body.
     */
    fun userConfig(
        profileConfig: JsonObject,
        reverseProxyKey: String?,
        reverseProxyUrl: String?,
        routerEnabled: Boolean,
    ): JsonObject = buildJsonObject {
        for ((k, v) in profileConfig) put(k, v)
        put("reverseProxyKey", reverseProxyKey?.let(::JsonPrimitive) ?: JsonNull)
        if (reverseProxyUrl != null) put("open_ai_reverse_proxy", reverseProxyUrl)
        put("openAIKey", JsonNull as JsonElement)
        put("claudeApiKey", JsonNull as JsonElement)
        put("text_streaming", true)
        put("janitor_router_enabled", routerEnabled)
    }
}
