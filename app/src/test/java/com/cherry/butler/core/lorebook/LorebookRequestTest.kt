package com.cherry.butler.core.lorebook

import com.cherry.butler.core.generation.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.*
import kotlin.test.*

class LorebookRequestTest {
    private val world = Lorebook("world", "World", listOf(LoreEntry("{{user}} owns a castle.", keys = listOf("castle"))))
    private val library = LorebookLibrary(books = listOf(world), bindings = mapOf("persona:p" to setOf("world")))
    private fun envelope(
        mode: String = "NEW",
        api: String = "janitor",
        userLines: Boolean = true,
        persona: String? = "p",
        owner: String? = "owner",
    ) = buildJsonObject {
        put("generateMode", mode)
        put("userConfig", buildJsonObject { put("api", api) })
        put("chat", buildJsonObject {
            put("id", 1)
            owner?.let { put("user_id", it) }
            put("summary", "Saved memory")
            persona?.let { put("persona_id", it) }
        })
        put("profile", buildJsonObject { put("id", "owner"); put("name", "Default") })
        put("personas", buildJsonArray { add(buildJsonObject { put("id", "p"); put("name", "Alice") }) })
        put("forcedPromptGenerationCacheRefetch", buildJsonObject { put("character", true); put("chat", false) })
        put("chatMessages", buildJsonArray {
            if (userLines) add(buildJsonObject { put("id", 10); put("is_bot", false); put("message", "Go to the castle") })
            add(buildJsonObject { put("id", 11); put("is_bot", true); put("message", "At the castle") })
        })
    }

    @Test fun `send regenerate continue and suggestion get request only lore with active persona macros`() {
        for (mode in listOf("NEW", "ALTERNATIVE", "CONTINUE", "SUGGESTION")) {
            val original = envelope(mode)
            val before = original.toString()
            val prepared = LorebookRequest.prepare(original, library)
            val messages = prepared.getValue("chatMessages").jsonArray
            assertTrue(messages[0].jsonObject.getValue("message").jsonPrimitive.content.contains("Alice owns a castle."))
            assertEquals("At the castle", messages[1].jsonObject.getValue("message").jsonPrimitive.content)
            assertEquals(original["chat"], prepared["chat"])
            assertEquals(before, original.toString())
            assertTrue(prepared.getValue("forcedPromptGenerationCacheRefetch").jsonObject.getValue("chat").jsonPrimitive.boolean)
            assertTrue(prepared.getValue("forcedPromptGenerationCacheRefetch").jsonObject.getValue("character").jsonPrimitive.boolean)
        }
    }

    @Test fun `empty library unselected unmatched and summary requests stay unchanged`() {
        val original = envelope()
        assertSame(original, LorebookRequest.prepare(original, LorebookLibrary()))
        assertSame(original, LorebookRequest.prepare(original, library.copy(bindings = emptyMap())))
        val unmatched = world.copy(entries = listOf(LoreEntry("unused", keys = listOf("dragon"))))
        assertSame(original, LorebookRequest.prepare(original, library.copy(books = listOf(unmatched))))
        val summary = envelope("SUMMARY_FULL")
        assertSame(summary, LorebookRequest.prepare(summary, library))
    }

    @Test fun `chat and persona union do not inject a book twice and chat disable wins`() {
        val lib = library.toggle("chat:1", "world", true)
        val original = envelope()
        val text = LorebookRequest.prepare(original, lib).getValue("chatMessages").jsonArray[0].jsonObject.getValue("message").jsonPrimitive.content
        assertEquals(1, Regex("Alice owns a castle").findAll(text).count())
        assertSame(original, LorebookRequest.prepare(original, lib.toggle("chat:1", "world", false)))
    }

    @Test fun `default persona bindings stay with their original owner and use the default name`() {
        val lib = library.copy(bindings = mapOf("persona:default:owner" to setOf("world")))
        val prepared = LorebookRequest.prepare(envelope(persona = null), lib)
        assertTrue(prepared.toString().contains("Default owns a castle."))
        val other = envelope(persona = null, owner = "another-owner")
        assertSame(other, LorebookRequest.prepare(other, lib))
    }

    @Test fun `direct chat bindings work without a profile owner`() {
        val original = envelope(persona = null, owner = null)
        val lib = library.copy(bindings = mapOf(LorebookLibrary.chat(1) to setOf("world")))
        assertTrue(LorebookRequest.prepare(original, lib).toString().contains("Default owns a castle."))
    }

    @Test fun `bot only history gets a transient user line without modifying the bot`() {
        val original = envelope(userLines = false)
        val prepared = LorebookRequest.prepare(original, library)
        val messages = prepared.getValue("chatMessages").jsonArray
        assertEquals(2, messages.size)
        assertEquals(original.getValue("chatMessages").jsonArray[0], messages[0])
        assertFalse(messages[1].jsonObject.getValue("is_bot").jsonPrimitive.boolean)
        assertNull(messages[1].jsonObject["id"])
    }

    @Test fun `request uses the saved device limit and zero removes the cap`() {
        val original = envelope()
        val large = "x".repeat(30_000)
        val lib = library.copy(books = listOf(world.copy(entries = listOf(LoreEntry(large, constant = true)))))
        for (limit in listOf(1, 24_000, 29_999)) {
            assertSame(original, LorebookRequest.prepare(original, lib.copy(contextCharLimit = limit)))
        }
        for (limit in listOf(0, 30_000)) {
            val prepared = LorebookRequest.prepare(original, lib.copy(contextCharLimit = limit))
            assertTrue(prepared.getValue("chatMessages").jsonArray[0].jsonObject.getValue("message").jsonPrimitive.content.contains(large))
        }
    }

    @Test fun `both transports receive local preparation before network dispatch`() {
        val received = mutableListOf<JsonObject>()
        val spy = object : GenerationTransport {
            override fun generate(envelope: JsonObject, proxy: ProxyTarget?): Flow<GenerationEvent> {
                received += envelope
                return emptyFlow()
            }
        }
        val router = RoutingTransport(spy, spy) { LorebookRequest.prepare(it, library) }
        router.generate(envelope(api = "janitor"), null)
        router.generate(envelope(api = "openai"), null)
        assertEquals(2, received.size)
        assertTrue(received.all { it.toString().contains("Alice owns a castle.") })
    }

    @Test fun `recursive request context uses only enabled books and leaves the envelope untouched`() {
        val first = world.copy(entries = listOf(LoreEntry("bridge", keys = listOf("castle"))))
        val second = Lorebook("second", "Second", listOf(LoreEntry("{{user}} returns to the castle.", keys = listOf("bridge"))))
        val unselected = Lorebook("unselected", "Unselected", listOf(LoreEntry("Forbidden", keys = listOf("bridge"))))
        val metadataOnly = Lorebook("metadata", "Metadata", listOf(LoreEntry("Profile leaked", keys = listOf("Alice", "Saved memory"))))
        val lib = library.copy(books = listOf(first, second, unselected, metadataOnly))
            .toggle("chat:1", "second", true).toggle("chat:1", "metadata", true)
        for (mode in listOf("NEW", "ALTERNATIVE", "CONTINUE", "SUGGESTION")) {
            val original = envelope(mode)
            val before = original.toString()
            val prepared = LorebookRequest.prepare(original, lib)
            val text = prepared.getValue("chatMessages").jsonArray[0].jsonObject.getValue("message").jsonPrimitive.content
            assertEquals("bridge\n\nAlice returns to the castle.", text.substringAfter("<lorebook>\n").substringBefore("\n</lorebook>"))
            assertEquals(before, original.toString())
            assertEquals(prepared, LorebookRequest.prepare(original, lib))
        }
    }
}
