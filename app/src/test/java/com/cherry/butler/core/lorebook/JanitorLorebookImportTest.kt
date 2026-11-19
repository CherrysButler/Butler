package com.cherry.butler.core.lorebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JanitorLorebookImportTest {
    @Test fun `detects a Janitor export and activates entries with their imported settings`() {
        val book = LorebookJson.parse(
            """
            [
              {
                "activationMode": "standard",
                "activationScript": "",
                "case_sensitive": false,
                "comment": "Marcus Reyes — Drummer",
                "constant": false,
                "content": "Marcus is the band's drummer.",
                "depth": 4,
                "enabled": true,
                "id": 0,
                "insertion_order": 100,
                "key": ["Marcus", "drummer", "bandmate"],
                "keysecondary": [],
                "keysRaw": "Marcus, drummer, bandmate",
                "matchWholeWords": true,
                "minMessages": 0,
                "name": "",
                "priority": 1,
                "probability": 100,
                "selectiveLogic": 0,
                "keywordsRaw": "Marcus, drummer, bandmate"
              },
              {
                "activationMode": "standard",
                "comment": "",
                "content": "The label handles press.",
                "case_sensitive": true,
                "depth": 2,
                "id": 1,
                "insertion_order": 400,
                "key": ["Renata"],
                "keysecondary": ["manager"],
                "name": "Renata Cole",
                "probability": 75,
                "selectiveLogic": 3
              }
            ]
            """.trimIndent(),
            fallbackName = "Malignant Soul",
        )
        val (marcus, renata) = book.entries
        assertEquals("Malignant Soul", book.name)
        assertEquals("Marcus Reyes — Drummer", marcus.name)
        assertEquals(listOf("Marcus", "drummer", "bandmate"), marcus.keys)
        assertEquals(100, marcus.order)
        assertEquals(4, marcus.scanDepth)
        assertFalse(marcus.selective)
        assertEquals("Renata Cole", renata.name)
        assertEquals(400, renata.order)
        assertEquals(2, renata.scanDepth)
        assertEquals(75, renata.probability)
        assertTrue(renata.selective)
        assertEquals(3, renata.selectiveLogic)
        assertEquals("Marcus is the band's drummer.", LorebookMatcher.context(listOf(book), listOf("MARCUS")))
        assertEquals("", LorebookMatcher.context(listOf(book), listOf("Marcusville")))
        assertEquals("", LorebookMatcher.context(listOf(book), listOf("renata manager")) { 0 })
        assertEquals("", LorebookMatcher.context(listOf(book), listOf("Renata manager", "x", "y")) { 0 })
        assertEquals("", LorebookMatcher.context(listOf(book), listOf("Renata")) { 0 })
        assertEquals("", LorebookMatcher.context(listOf(book), listOf("Renata manager")) { 75 })
        assertEquals("The label handles press.", LorebookMatcher.context(listOf(book), listOf("Renata manager")) { 74 })
    }

    @Test fun `structured Janitor keys take precedence and absent arrays use the raw keywords`() {
        val book = LorebookJson.parse(
            """
            [
              {"content":"A", "key":["a, b"], "keysRaw":"a, b", "keysecondary":[], "keysecondaryRaw":"stale"},
              {"content":"B", "keysRaw":"Paris, New York\r\n London", "keysecondaryRaw":"night, fog"},
              {"content":"C", "keywordsRaw":"gate, old bridge"}
            ]
            """.trimIndent(),
        )
        val (structured, raw, legacy) = book.entries
        assertEquals(listOf("a, b"), structured.keys)
        assertTrue(structured.secondaryKeys.isEmpty())
        assertEquals(listOf("Paris", "New York", "London"), raw.keys)
        assertEquals(listOf("night", "fog"), raw.secondaryKeys)
        assertTrue(raw.selective)
        assertEquals(listOf("gate", "old bridge"), legacy.keys)
    }

    @Test fun `keeps inactive and scripted entries from firing while allowing standard constants`() {
        val book = LorebookJson.parse(
            """
            [
              {"content":"Active constant", "constant":true, "enabled":true, "activationMode":"standard"},
              {"content":"Disabled", "constant":true, "enabled":false, "activationMode":"standard"},
              {"content":"Script text", "constant":true, "enabled":true, "activationMode":"script", "activationScript":"example"}
            ]
            """.trimIndent(),
        )
        assertEquals(listOf(true, false, false), book.entries.map { it.enabled })
        assertEquals("Script text", book.entries.last().content)
        assertEquals("Active constant", LorebookMatcher.context(listOf(book), emptyList()))
    }

    @Test fun `malformed Janitor entries reject the entire import`() {
        for (json in listOf(
            """[{"content":"Valid"},42]""",
            """[{"content":"Valid"},{"key":["missing content"]}]""",
            """[{"content":"Valid","key":[42]}]""",
        )) {
            assertFailsWith<IllegalArgumentException>(json) { LorebookJson.parse(json) }
        }
    }
}
