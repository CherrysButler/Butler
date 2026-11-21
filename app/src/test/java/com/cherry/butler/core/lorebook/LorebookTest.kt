package com.cherry.butler.core.lorebook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
                selectiveLogic = SecondaryKeywordRule.MatchNone,
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

    @Test fun `shared whole word patterns live until their last distinct entry releases them`() {
        val pool = WholeWordPatterns()
        val entry = LoreEntry("Weather", listOf("rain", "rain"), listOf("rain"), selective = true, wholeWords = true)
        fun prepare(e: LoreEntry) = requireNotNull(pool.prepare(e, 4))
        val weather = prepare(entry)
        val rain = weather.patterns.getValue("rain")
        val pedestrian = prepare(entry.copy(content = "Umbrella"))
        val fighter = prepare(entry.copy(keys = listOf("Rain"), selective = false, caseSensitive = true))
        val properName = fighter.patterns.getValue("Rain")
        assertNotSame(rain, properName)
        assertSame(rain, pedestrian.patterns.getValue("rain"))

        val large = prepare(entry.copy(keys = List(20_000) { "word$it" }, selective = false))
        pool.release(weather)
        val again = prepare(entry)
        assertSame(rain, again.patterns.getValue("rain"))
        pool.release(pedestrian)
        pool.release(again)
        assertNotSame(rain, prepare(entry).patterns.getValue("rain"))
        assertSame(properName, prepare(fighter.entry).patterns.getValue("Rain"))
        pool.release(large)
    }

    @Test fun `primary and secondary whole word keys are literal and respect each entry's case mode`() {
        val primary = LoreEntry("Primary", keys = listOf("a+b"), wholeWords = true)
        val secondary = primary.copy(
            content = "Secondary", keys = listOf("gate"), secondaryKeys = listOf("a+b"), selective = true, caseSensitive = true,
        )
        val books = listOf(book(primary, secondary))
        assertEquals("Primary\n\nSecondary", LorebookMatcher.context(books, listOf("gate [a+b]")))
        assertEquals("Primary", LorebookMatcher.context(books, listOf("gate [A+B]")))
        assertEquals("", LorebookMatcher.context(books, listOf("gate aaab _a+b a+b9 Жa+b")))
    }

    @Test fun `secondary keyword rules keep their numeric JSON codes`() {
        val localJson = Json { encodeDefaults = true }
        fun exportedCode(value: LoreEntry): JsonPrimitive {
            val json = Json.parseToJsonElement(LorebookJson.export(book(value)))
            val exportedEntry = json.jsonObject.getValue("entries").jsonObject.getValue("0").jsonObject
            return exportedEntry.getValue("selectiveLogic").jsonPrimitive
        }
        for ((code, rule) in listOf(
            0 to SecondaryKeywordRule.MatchAny, 1 to SecondaryKeywordRule.NotAll,
            2 to SecondaryKeywordRule.MatchNone, 3 to SecondaryKeywordRule.MatchAll,
        )) {
            val imported = LorebookJson.parse("""{"entries":[{"content":"Fact","extensions":{"selectiveLogic":$code}}]}""")
            val entry = LoreEntry("Fact", selectiveLogic = rule)
            assertEquals(entry, imported.entries.single())
            assertEquals(JsonPrimitive(code), exportedCode(entry))
            val legacy = """{"content":"Fact","selectiveLogic":$code}"""
            assertEquals(entry, localJson.decodeFromString(LoreEntry.serializer(), legacy))
            val encoded = localJson.encodeToJsonElement(LoreEntry.serializer(), entry)
            assertEquals(JsonPrimitive(code), encoded.jsonObject.getValue("selectiveLogic"))
        }

        val unknown = LorebookJson.parse("""[{"content":"Fact","selectiveLogic":42}]""").entries.single()
        assertEquals(SecondaryKeywordRule.MatchAny, unknown.selectiveLogic)
        assertEquals(JsonPrimitive(0), exportedCode(unknown))
    }

    @Test fun `all four secondary keyword conditions match their named rules`() {
        val e = LoreEntry("Fact", keys = listOf("door"), secondaryKeys = listOf("red", "key"), selective = true)
        fun matches(rule: SecondaryKeywordRule, text: String) =
            LorebookMatcher.context(listOf(book(e.copy(selectiveLogic = rule))), listOf(text)).isNotEmpty()
        assertTrue(matches(SecondaryKeywordRule.MatchAny, "door red"))
        assertFalse(matches(SecondaryKeywordRule.MatchAny, "door"))
        assertTrue(matches(SecondaryKeywordRule.NotAll, "door red"))
        assertFalse(matches(SecondaryKeywordRule.NotAll, "door red key"))
        assertTrue(matches(SecondaryKeywordRule.MatchNone, "door"))
        assertFalse(matches(SecondaryKeywordRule.MatchNone, "door red"))
        assertTrue(matches(SecondaryKeywordRule.MatchAll, "door red key"))
        assertFalse(matches(SecondaryKeywordRule.MatchAll, "door red"))
        assertFalse(matches(SecondaryKeywordRule.MatchNone, "unrelated"))
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
