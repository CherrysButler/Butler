package com.cherry.butler.core.generation

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.PronounsDto
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

    private fun row(id: Long?, bot: Boolean, main: Boolean = true, text: String = "x", persona: String? = null) = MessageEntity(
        localId = (id ?: 0L) + 1000, serverId = id, chatId = 42, isBot = bot, isMain = main, text = text,
        createdAt = 1_790_127_611_265L, rating = null, personaId = persona, generationRequestIds = emptyList(),
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

    private val sen = PersonaDto(
        id = "sen", name = "Sen", appearance = "green hair",
        pronouns = PronounsDto(subjective = "she", objective = "her", possessive = "her", possessivePronoun = "hers", reflexive = "herself"),
    )

    private fun buildAsPersona(): JsonObject = GenerationEnvelope.build(
        chatId = 42, characterId = "char", userId = "user", summary = "", summaryChatId = null,
        history = listOf(row(1, bot = true), row(2, bot = false, persona = "sen")),
        profile = EnvelopeProfile(id = "user", name = "Rain", userName = "abrin", userAppearance = "green hair"),
        userConfig = buildJsonObject { put("api", JsonPrimitive("openai")) },
        mode = GenerateMode.New, clientPlatform = "web", persona = sen, knownPersonas = listOf(sen),
    )

    @Test
    fun `a persona is named in profiles and personas, as the website sends it`() {
        val env = buildAsPersona()
        assertEquals("sen", env["chat"]!!.jsonObject["persona_id"]!!.jsonPrimitive.content)
        assertEquals(setOf("id", "name", "user_name"), env["profile"]!!.jsonObject.keys)
        assertEquals("Rain", env["profile"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val p0 = env["profiles"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("appearance", "id", "name", "pronouns", "type"), p0.keys)
        assertEquals("persona", p0["type"]!!.jsonPrimitive.content)
        assertEquals("Sen", p0["name"]!!.jsonPrimitive.content)
        val persona = env["personas"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("appearance", "id", "name", "pronouns", "user_id"), persona.keys)
        assertEquals("she", persona["pronouns"]!!.jsonObject["subjective"]!!.jsonPrimitive.content)
    }

    @Test
    fun `user lines carry their persona, bot lines do not`() {
        val (bot, user) = buildAsPersona()["chatMessages"]!!.jsonArray.map { it.jsonObject }
        assertNull(bot["persona_id"])
        assertEquals("sen", user["persona_id"]!!.jsonPrimitive.content)
    }

    private val ghost = PersonaDto(id = "ghost", name = "Ghost", appearance = "masked")

    /** A chat started as Sen whose latest line was sent as [now] (null: the profile). */
    private fun buildAfterSwitch(now: PersonaDto?): JsonObject = GenerationEnvelope.build(
        chatId = 42, characterId = "char", userId = "user", summary = "", summaryChatId = null,
        history = listOf(row(1, bot = true), row(2, bot = false, persona = "sen"), row(3, bot = false, persona = now?.id)),
        profile = EnvelopeProfile(id = "user", name = "Rain", userName = "abrin", userAppearance = now?.appearance ?: "tall", defaultAppearance = "tall"),
        userConfig = buildJsonObject { put("api", JsonPrimitive("openai")) },
        mode = GenerateMode.New, clientPlatform = "web", persona = now, knownPersonas = listOf(sen, ghost),
    )

    private fun JsonObject.profileNames() = this["profiles"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }

    @Test
    fun `after a switch the new persona is user and every speaker is listed`() {
        val env = buildAfterSwitch(ghost)
        assertEquals("ghost", env["chat"]!!.jsonObject["persona_id"]!!.jsonPrimitive.content)
        assertEquals(listOf("Sen", "Ghost"), env.profileNames())
        assertEquals(2, env["personas"]!!.jsonArray.size)
    }

    @Test
    fun `switching back to the profile lists it beside the persona it left`() {
        val env = buildAfterSwitch(null)
        assertNull(env["chat"]!!.jsonObject["persona_id"])
        assertEquals(listOf("Rain", "Sen"), env.profileNames())
        assertEquals("tall", env["profile"]!!.jsonObject["user_appearance"]!!.jsonPrimitive.content)
    }
}
