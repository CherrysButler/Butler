package com.cherry.butler.core.lorebook

import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.generation.ChoicesService
import com.cherry.butler.core.generation.EnvelopeProfile
import com.cherry.butler.core.generation.GenerateMode
import com.cherry.butler.core.generation.GenerationEnvelope
import com.cherry.butler.core.generation.GuidedRetry
import com.cherry.butler.core.generation.MoodTagger
import com.cherry.butler.core.generation.PromptAddons
import com.cherry.butler.core.generation.SuggestionPrompt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LorebookRequestTest {
    private val world = Lorebook("world", "World", listOf(LoreEntry("{{user}} owns a castle.", keys = listOf("castle"))))
    private val library = LorebookLibrary(books = listOf(world), bindings = mapOf("persona:p" to setOf("world")))
    private val user = MessageEntity(
        serverId = 10, chatId = 1, isBot = false, isMain = true, text = "Go to the castle",
        createdAt = 0, rating = null, personaId = "p", generationRequestIds = emptyList(),
        thinking = null, streamState = null, cachedAt = 0,
    )
    private val bot = user.copy(serverId = 11, isBot = true, personaId = null, text = "At the gate")
    private val scene = listOf(user, bot)

    private fun instruction(
        lib: LorebookLibrary = library,
        history: List<MessageEntity> = scene,
        persona: String? = "p",
        owner: String = "owner",
        name: String = "Alice",
    ) = LorebookRequest.instructionFor(lib, 1, owner, persona, name, history)

    private fun envelope(
        mode: GenerateMode,
        history: List<MessageEntity> = scene,
        lore: String? = null,
        draft: String? = null,
        extraUserLine: String? = null,
        api: String = "janitor",
    ) = GenerationEnvelope.build(
        chatId = 1, characterId = "char", userId = "owner", summary = null, summaryChatId = null,
        history = history, profile = EnvelopeProfile("owner", "Default", "default", ""),
        persona = PersonaDto(id = "p", name = "Alice"),
        userConfig = buildJsonObject { put("api", api) }, mode = mode, clientPlatform = "web",
        forceRefetch = setOf("character"), loreContext = lore, draft = draft, extraUserLine = extraUserLine,
    )

    private fun JsonObject.lines() = getValue("chatMessages").jsonArray.map { it.jsonObject.getValue("message").jsonPrimitive.content }
    private fun JsonObject.refetch(key: String) = getValue("forcedPromptGenerationCacheRefetch").jsonObject.getValue(key).jsonPrimitive.boolean

    @Test
    fun `new alternative and continue append lore to the last confirmed user line on either provider`() {
        val history = listOf(user.copy(serverId = 8, text = "Hello"), user, bot, user.copy(serverId = null))
        val lore = assertNotNull(instruction(history = history))
        assertContains(lore, "Alice owns a castle.")
        for (api in listOf("janitor", "openai")) {
            for (mode in listOf(GenerateMode.New, GenerateMode.Alternative, GenerateMode.Continue)) {
                val request = envelope(mode, history, lore, api = api)
                assertEquals(listOf("Hello", "Go to the castle\n\n$lore", "At the gate"), request.lines())
                assertTrue(request.refetch("chat"))
                assertTrue(request.refetch("character"))
            }
        }
        assertEquals("Go to the castle", user.text)
    }

    @Test
    fun `highlights choices and summary omit lore even when a reply in the same chat activates a constant`() {
        val constant = world.copy(entries = listOf(LoreEntry("Always active", constant = true)))
        val lore = assertNotNull(instruction(library.copy(books = listOf(constant))))
        val reply = envelope(GenerateMode.New, lore = lore)
        assertContains(reply.lines().first(), "Always active")
        assertTrue(reply.refetch("chat"))

        val prompts = listOf(MoodTagger.question(emptySet()), ChoicesService.instruction("Alice"))
        for (api in listOf("janitor", "openai")) {
            for (prompt in prompts) {
                val tail = prompt.takeIf { api == "janitor" }
                val helper = envelope(GenerateMode.New, extraUserLine = tail, api = api)
                assertEquals(scene.map { it.text } + listOfNotNull(tail), helper.lines())
                assertFalse(helper.refetch("chat"))
                assertTrue(helper.refetch("character"))
            }
        }
        val summary = envelope(GenerateMode.SummaryFull)
        assertEquals(scene.map { it.text }, summary.lines())
        assertFalse(summary.refetch("chat"))
    }

    @Test
    fun `butter guidance and draft keywords are outside the scene scan`() {
        val entries = listOf(LoreEntry("Rain lore", keys = listOf("rain")), LoreEntry("Moon lore", keys = listOf("moon")))
        val lib = library.copy(books = listOf(world.copy(entries = entries)))
        val lore = instruction(lib)
        assertNull(lore)
        val guided = GuidedRetry.apply(scene, "Look at the moon")
        val reply = envelope(GenerateMode.New, PromptAddons.intoHistory(guided, PromptAddons.BUTTER), lore)
        assertContains(reply.lines().first(), "rain")
        assertContains(reply.lines().last(), "moon")
        assertFalse(reply.refetch("chat"))
        val draft = SuggestionPrompt.line("In the rain", perspective = null)
        val suggestion = envelope(GenerateMode.Suggestion, lore = lore, draft = draft)
        assertEquals(scene.map { it.text } + draft, suggestion.lines())
        assertFalse(suggestion.refetch("chat"))
    }

    @Test
    fun `write and enhance keep their exact draft last even before the first user message`() {
        val opening = bot.copy(text = "At the castle")
        for (history in listOf(scene, listOf(opening))) {
            val lore = assertNotNull(instruction(history = history))
            for (text in listOf("", "I approach")) {
                val draft = SuggestionPrompt.line(text, perspective = null)
                val request = envelope(GenerateMode.Suggestion, history, lore, draft)
                val expected = if (history == scene) listOf("${user.text}\n\n$lore", bot.text) else listOf(opening.text, lore)
                assertEquals(expected + draft, request.lines())
                assertTrue(request.refetch("chat"))
                if (history.size == 1) {
                    val transient = request.getValue("chatMessages").jsonArray[1].jsonObject
                    assertNull(transient["id"])
                    assertFalse(transient.getValue("is_bot").jsonPrimitive.boolean)
                }
            }
        }
    }

    @Test
    fun `selection respects exclusions chat and default persona ownership`() {
        val both = library.toggle("chat:1", "world", true)
        assertEquals(instruction(), instruction(both))
        assertNull(instruction(both.toggle("chat:1", "world", false)))
        assertNull(instruction(LorebookLibrary()))
        assertNull(instruction(library.copy(bindings = emptyMap())))

        val default = library.copy(bindings = mapOf("persona:default:owner" to setOf("world")))
        assertContains(assertNotNull(instruction(default, persona = null, name = "Default")), "Default owns a castle.")
        assertNull(instruction(default, persona = null, owner = "another"))
        assertNotNull(instruction(both, persona = null, owner = ""))
    }

    @Test
    fun `scan fills the active name and ignores unconfirmed messages`() {
        val book = world.copy(entries = listOf(LoreEntry("Met {{USER}}.", keys = listOf("Alice"))))
        val lib = library.copy(books = listOf(book))
        assertNull(instruction(lib, history = scene + user.copy(serverId = null, text = "Alice")))
        val greeting = bot.copy(text = "Greetings, {user}.")
        assertContains(assertNotNull(instruction(lib, history = listOf(greeting))), "Met Alice.")
        assertEquals("Greetings, {user}.", greeting.text)
    }

    @Test
    fun `uses the saved device limit and zero removes the cap`() {
        val large = "x".repeat(30_000)
        val lib = library.copy(books = listOf(world.copy(entries = listOf(LoreEntry(large, constant = true)))))
        for (limit in listOf(1, 24_000, 29_999)) assertNull(instruction(lib.copy(contextCharLimit = limit)))
        for (limit in listOf(0, 30_000)) assertContains(assertNotNull(instruction(lib.copy(contextCharLimit = limit))), large)
    }

    @Test
    fun `recursive matching considers only enabled books and the scene`() {
        val first = world.copy(entries = listOf(LoreEntry("bridge", keys = listOf("castle"))))
        val second = Lorebook("second", "Second", listOf(LoreEntry("{{user}} returns.", keys = listOf("bridge"))))
        val unselected = Lorebook("unselected", "Unselected", listOf(LoreEntry("Forbidden", keys = listOf("bridge"))))
        val metadataOnly = Lorebook("metadata", "Metadata", listOf(LoreEntry("Profile leaked", keys = listOf("Alice"))))
        val lib = library.copy(books = listOf(first, second, unselected, metadataOnly))
            .toggle("chat:1", "second", true).toggle("chat:1", "metadata", true)
        val lore = assertNotNull(instruction(lib))
        assertEquals("bridge\n\nAlice returns.", lore.substringAfter("<lorebook>\n").substringBefore("\n</lorebook>"))
    }
}
