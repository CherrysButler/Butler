package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the envelope to the key sets observed on the wire (docs/JANITOR_API.md §18.2, §19.3). A
 * key drifting here is a silent prompt-assembly failure server-side, so this test is the
 * contract, not the docs.
 */
class GenerationEnvelopeTest {

    private fun row(id: Long?, bot: Boolean, main: Boolean = true, text: String = "x") = MessageEntity(
        localId = (id ?: 0L) + 1000, serverId = id, chatId = 42, isBot = bot, isMain = main, text = text,
        createdAt = 1_790_127_611_265L, rating = null, personaId = null, generationRequestIds = emptyList(),
        thinking = null, streamState = null, cachedAt = 0,
    )

    private fun build(mode: GenerateMode = GenerateMode.New, replaces: Boolean? = null): JsonObject =
        GenerationEnvelope.build(
            chatId = 42, characterId = "char", userId = "user", summary = "", summaryChatId = null,
            history = listOf(row(1, bot = false), row(2, bot = true), row(null, bot = false)),
            profile = EnvelopeProfile(id = "user", name = "Rain", userName = "abrin", userAppearance = "tall"),
            userConfig = buildJsonObject { put("api", JsonPrimitive("openai")) },
            mode = mode, clientPlatform = "web", memoryReplacesHistory = replaces,
        )

    @Test
    fun `top-level keys match the capture`() {
        assertEquals(
            setOf("chat", "chatMessages", "clientPlatform", "forcedPromptGenerationCacheRefetch", "generateMode", "generateType", "profile", "profiles", "userConfig"),
            build().keys,
        )
    }

    @Test
    fun `chat, message, profile and profiles blocks match the capture`() {
        val env = build()
        assertEquals(setOf("id", "character_id", "user_id", "summary"), env["chat"]!!.jsonObject.keys)
        val msg = env["chatMessages"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("id", "chat_id", "character_id", "created_at", "is_bot", "is_main", "message"), msg.keys)
        assertEquals(setOf("id", "name", "user_name", "user_appearance"), env["profile"]!!.jsonObject.keys)
        val p0 = env["profiles"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("id", "name", "user_name", "appearance", "type"), p0.keys)
        assertEquals("profile", p0["type"]!!.jsonPrimitive.content)
        assertEquals("CHAT", env["generateType"]!!.jsonPrimitive.content)
        assertEquals("NEW", env["generateMode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `unconfirmed rows are never sent as history`() {
        assertEquals(2, build()["chatMessages"]!!.jsonArray.size)
    }

    @Test
    fun `memoryReplacesHistory rides only when set`() {
        assertNull(build()["memoryReplacesHistory"])
        assertEquals(true, build(GenerateMode.SummaryFull, replaces = true)["memoryReplacesHistory"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `userConfig gains exactly the official client's extra keys`() {
        val uc = GenerationEnvelope.userConfig(
            profileConfig = buildJsonObject { put("api", JsonPrimitive("openai")) },
            reverseProxyKey = "k", reverseProxyUrl = "https://p/x", routerEnabled = false,
        )
        assertEquals(
            setOf("api", "reverseProxyKey", "open_ai_reverse_proxy", "openAIKey", "claudeApiKey", "text_streaming", "janitor_router_enabled"),
            uc.keys,
        )
    }
}
