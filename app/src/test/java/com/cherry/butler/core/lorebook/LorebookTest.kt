package com.cherry.butler.core.lorebook

import kotlinx.serialization.json.Json
import kotlin.test.*

class LorebookTest {
    private fun book(vararg entries: LoreEntry) = Lorebook("book", "World", entries.toList())

    @Test fun `keyword matching scans recent messages and constants need no user line`() {
        val world = book(LoreEntry("Castle lore", keys = listOf("castle")), LoreEntry("Always", constant = true))
        assertTrue(LorebookMatcher.context(listOf(world), listOf("CASTLE")).contains("Castle lore"))
        assertEquals("Always", LorebookMatcher.context(listOf(world), listOf("castle", "a", "b", "c", "d")))
        assertEquals("Always", LorebookMatcher.context(listOf(world), emptyList()))
    }

    @Test fun `per entry depth and Unicode whole words are honored`() {
        val e = LoreEntry("Fact", keys = listOf("кот"), wholeWords = true, caseSensitive = true, scanDepth = 1)
        assertEquals("", LorebookMatcher.context(listOf(book(e)), listOf("кот", "nothing")))
        assertEquals("", LorebookMatcher.context(listOf(book(e)), listOf("котик КОТ")))
        assertEquals("Fact", LorebookMatcher.context(listOf(book(e)), listOf("Вот кот!")))
    }

    @Test fun `all four secondary keyword conditions match SillyTavern values`() {
        val e = LoreEntry("Fact", keys = listOf("door"), secondaryKeys = listOf("red", "key"), selective = true)
        fun matches(logic: Int, text: String) = LorebookMatcher.context(listOf(book(e.copy(selectiveLogic = logic))), listOf(text)).isNotEmpty()
        assertTrue(matches(0, "door red"))
        assertFalse(matches(0, "door"))
        assertTrue(matches(1, "door red"))
        assertFalse(matches(1, "door red key"))
        assertTrue(matches(2, "door"))
        assertFalse(matches(2, "door red"))
        assertTrue(matches(3, "door red key"))
        assertFalse(matches(3, "door red"))
        assertFalse(matches(2, "unrelated"))
    }

    @Test fun `probability and disabled flags apply to constants too`() {
        val entries = book(
            LoreEntry("Disabled", constant = true, enabled = false),
            LoreEntry("Never", constant = true, probability = 0),
            LoreEntry("Sometimes", constant = true, probability = 50),
        )
        assertEquals("Sometimes", LorebookMatcher.context(listOf(entries), emptyList()) { 49 })
        assertEquals("", LorebookMatcher.context(listOf(entries), emptyList()) { 50 })
    }

    @Test fun `budget keeps higher priority entries whole`() {
        val world = book(
            LoreEntry("low".repeat(4000), constant = true, order = 1),
            LoreEntry("high".repeat(2000), constant = true, order = 200),
        )
        assertEquals("high".repeat(2000), LorebookMatcher.context(listOf(world), emptyList(), charLimit = 12_000))
        assertEquals("small", LorebookMatcher.context(listOf(book(
            LoreEntry("x".repeat(12001), constant = true), LoreEntry("small", constant = true),
        )), emptyList(), charLimit = 12_000))
    }

    @Test fun `default budget is 24000 and includes entries exactly at the limit`() {
        assertEquals(24_000, LorebookLibrary().contextCharLimit)
        val exact = "x".repeat(24_000)
        assertEquals(exact, LorebookMatcher.context(listOf(book(LoreEntry(exact, constant = true))), emptyList()))
        assertEquals("", LorebookMatcher.context(listOf(book(LoreEntry(exact + "x", constant = true))), emptyList()))
    }

    @Test fun `custom budget counts separators and zero includes all eligible entries`() {
        val books = listOf(book(LoreEntry("aaaa", constant = true, order = 1)),
            book(LoreEntry("bbbb", constant = true, order = 2)).copy(id = "second"))
        assertEquals("aaaa\n\nbbbb", LorebookMatcher.context(books, emptyList(), charLimit = 10))
        assertEquals("bbbb", LorebookMatcher.context(books, emptyList(), charLimit = 9))
        val huge = "x".repeat(30_000)
        val unlimited = books + book(LoreEntry(huge, constant = true, order = 3), LoreEntry("disabled", constant = true, enabled = false))
        assertEquals("aaaa\n\nbbbb\n\n$huge", LorebookMatcher.context(unlimited, emptyList(), charLimit = 0))
        assertFailsWith<IllegalArgumentException> { LorebookMatcher.context(books, emptyList(), charLimit = -1) }
    }

    @Test fun `old libraries adopt new default and custom and unlimited settings survive persistence`() {
        assertEquals(24_000, Json.decodeFromString(LorebookLibrary.serializer(), "{}").contextCharLimit)
        for (limit in listOf(0, 12345, 24000)) {
            val library = LorebookLibrary(books = listOf(book(LoreEntry("Fact", constant = true))), contextCharLimit = limit)
                .toggle("persona:p", "book", true)
            val stored = Json.encodeToString(LorebookLibrary.serializer(), library)
            val restored = Json.decodeFromString(LorebookLibrary.serializer(), stored)
            assertEquals(library, restored)
            assertEquals(limit, restored.delete("book").contextCharLimit)
        }
    }

    @Test fun `persona and chat selections union once with explicit chat opt out`() {
        val scope = LorebookLibrary.chat(1)
        val persona = LorebookLibrary.persona("p")
        val lib = LorebookLibrary(books = listOf(book(LoreEntry("x"))), bindings = mapOf(scope to setOf("book"), persona to setOf("book", "other")))
        assertEquals(setOf("book", "other"), lib.enabled(scope, persona))
        val disabled = lib.toggle(scope, "other", false)
        assertEquals(setOf("book"), disabled.enabled(scope, persona))
        assertEquals(setOf("book", "other"), disabled.enabled(LorebookLibrary.chat(2), persona))
        assertEquals(setOf("book", "other"), disabled.toggle(scope, "other", true).enabled(scope, persona))
        assertTrue(lib.delete("book").bindings.values.none { "book" in it })
        assertTrue(disabled.delete("other").excluded.values.none { "other" in it })
    }

    @Test fun `local library round trip preserves favorites selections and exclusions`() {
        val lib = LorebookLibrary(books = listOf(book(LoreEntry("Fact", constant = true)).copy(favorite = true)))
            .toggle("persona:p", "book", true).toggle("chat:1", "book", false)
        val encoded = Json.encodeToString(LorebookLibrary.serializer(), lib)
        assertEquals(lib, Json.decodeFromString(LorebookLibrary.serializer(), encoded))
        assertTrue(lib.enabled("chat:2").isEmpty()) // Favorite is not activation.
    }
}
