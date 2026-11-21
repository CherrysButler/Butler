package com.cherry.butler.core.lorebook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class LorebookTest {
    private fun book(vararg entries: LoreEntry) = Lorebook("book", "World", entries.toList())

    @Test fun `imports SillyTavern object entries and disabled constants`() {
        val parsed = LorebookJson.parse("""{"entries":{"0":{"key":["castle"],"content":"A castle","constant":true,"disable":true,"order":7,"scanDepth":2,"probability":0}}}""", " My world ")
        assertEquals("My world", parsed.name)
        assertEquals(listOf("castle"), parsed.entries.single().keys)
        assertEquals(7, parsed.entries.single().order)
        assertEquals(2, parsed.entries.single().scanDepth)
        assertEquals("", LorebookMatcher.context(listOf(parsed), listOf("castle")))
    }

    @Test fun `imports nested character card book and extension fields`() {
        val parsed = LorebookJson.parse("""{"data":{"character_book":{"name":"Card world","scan_depth":8,"entries":[{"keys":["cat"],"secondary_keys":["black"],"content":"Fact","enabled":true,"insertion_order":9,"extensions":{"case_sensitive":true,"match_whole_words":true,"scan_depth":1,"probability":75}}]}}}""")
        assertEquals("Card world", parsed.name)
        assertEquals(8, parsed.scanDepth)
        val e = parsed.entries.single()
        assertTrue(e.caseSensitive && e.wholeWords && e.selective)
        assertEquals(75, e.probability)
        assertEquals(9, e.order)
        assertEquals(1, e.scanDepth)
    }

    @Test fun `invalid import fails before a library changes`() {
        for (text in listOf("not JSON", "[]", "{}", """{"entries":[]}""", """{"entries":[{"content":5}]}""", """{"entries":[{"content":"x","keys":"cat"}]}""")) {
            assertFailsWith<IllegalArgumentException>(text) { LorebookJson.parse(text) }
        }
        assertFailsWith<IllegalArgumentException> { LorebookJson.parse(" ".repeat(LorebookJson.MAX_JSON_CHARS + 1)) }
    }

    @Test fun `World Info export uses numeric UIDs and round trips supported settings`() {
        val world = book(
            LoreEntry(
                content = "Ключ: \"A\"\nSecond line",
                keys = listOf("gate", "ключ"),
                secondaryKeys = listOf("locked"),
                selective = true,
                selectiveLogic = 2,
                caseSensitive = true,
                wholeWords = true,
                order = 250,
                scanDepth = 0,
                probability = 37,
                name = "Gate",
            ),
            LoreEntry("An old note", constant = true, enabled = false),
        ).copy(name = "My world", scanDepth = 8, favorite = true)
        val exported = LorebookJson.export(world)
        val entries = Json.parseToJsonElement(exported).jsonObject.getValue("entries").jsonObject
        assertEquals(setOf("0", "1"), entries.keys)
        entries.forEach { (key, entry) ->
            assertEquals(key.toInt(), entry.jsonObject.getValue("uid").jsonPrimitive.int)
        }
        val reimported = LorebookJson.parse(exported, fallbackName = "file name")
        assertEquals(world.copy(id = reimported.id, favorite = false), reimported)
        assertNotEquals(world.id, reimported.id)
    }

    @Test fun `standalone World Info without a name uses the file name and accepts a UTF-8 BOM`() {
        val json = "\uFEFF" + """{"entries":{"0":{"uid":0,"constant":true,"content":"Fact"}}}"""
        assertEquals("Castle world", LorebookJson.parse(json, fallbackName = "Castle world").name)
    }

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
        assertEquals(24_000, LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT)
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
}
