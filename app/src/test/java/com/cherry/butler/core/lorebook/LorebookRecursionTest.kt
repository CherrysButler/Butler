package com.cherry.butler.core.lorebook

import org.junit.Test
import kotlin.test.assertEquals

class LorebookRecursionTest {
    private fun book(vararg entries: LoreEntry, id: String = "world", scanDepth: Int = 4) =
        Lorebook(id, id, entries.toList(), scanDepth = scanDepth)

    @Test fun `chains cross books and output uses priority rather than discovery order`() {
        val books = listOf(
            book(LoreEntry("Done", keys = listOf("moon"), order = 10), id = "last"),
            book(LoreEntry("moon", keys = listOf("gate"), order = 20), id = "middle"),
            book(LoreEntry("gate", keys = listOf("castle"), order = 30), id = "first"),
        )
        assertEquals("Done\n\nmoon\n\ngate", LorebookMatcher.context(books, listOf("castle")))
    }

    @Test(timeout = 2_000) fun `self reference activates once`() {
        val books = listOf(book(LoreEntry("seed", keys = listOf("seed"))))
        assertEquals("seed", LorebookMatcher.context(books, listOf("seed"), charLimit = 0))
    }

    @Test(timeout = 2_000) fun `mutual cycle across books activates each entry once`() {
        val books = listOf(
            book(LoreEntry("bridge", keys = listOf("seed")), id = "first"),
            book(LoreEntry("seed", keys = listOf("bridge")), id = "second"),
        )
        assertEquals("bridge\n\nseed", LorebookMatcher.context(books, listOf("seed"), charLimit = 0))
        assertEquals("", LorebookMatcher.context(books, listOf("unrelated"), charLimit = 0))
    }

    @Test(timeout = 2_000) fun `branching paths converge without reactivating the shared entry`() {
        val books = listOf(book(
            LoreEntry("left right", keys = listOf("seed")),
            LoreEntry("finish", keys = listOf("left")),
            LoreEntry("finish", keys = listOf("right")),
            LoreEntry("seed", keys = listOf("finish")),
        ))
        assertEquals("left right\n\nfinish\n\nfinish\n\nseed", LorebookMatcher.context(books, listOf("seed"), charLimit = 0))
    }

    @Test fun `equal entries in one book or different books keep their own identity`() {
        val entry = LoreEntry("Fact", keys = listOf("seed"))
        val books = listOf(book(entry, entry), book(entry, id = "second"))
        assertEquals("Fact\n\nFact\n\nFact", LorebookMatcher.context(books, listOf("seed")))
    }

    @Test fun `later rounds never combine their text with history or older rounds`() {
        val books = listOf(book(
            LoreEntry("bridge", keys = listOf("seed")),
            LoreEntry("leaf", keys = listOf("bridge")),
            LoreEntry("History leaked", keys = listOf("seed"), secondaryKeys = listOf("leaf"), selective = true),
            LoreEntry("Old round leaked", keys = listOf("bridge"), secondaryKeys = listOf("leaf"), selective = true),
        ))
        assertEquals("bridge\n\nleaf", LorebookMatcher.context(books, listOf("seed")))
    }

    @Test fun `recursion searches the complete previous round regardless of history depth`() {
        val books = listOf(book(
            LoreEntry("red", constant = true),
            LoreEntry("door", constant = true),
            LoreEntry("Unlocked", keys = listOf("door"), secondaryKeys = listOf("red"), selective = true, scanDepth = 0),
            scanDepth = 1,
        ))
        assertEquals("red\n\ndoor\n\nUnlocked", LorebookMatcher.context(books, emptyList()))
    }

    @Test fun `recursive matching preserves case whole words and secondary conditions`() {
        for ((text, expected) in listOf(
            "котик red" to false,
            "КОТ red" to false,
            "кот blue" to false,
            "кот red" to true,
        )) {
            val books = listOf(book(
                LoreEntry(text, constant = true),
                LoreEntry("Fact", keys = listOf("кот"), secondaryKeys = listOf("red"), selective = true, caseSensitive = true, wholeWords = true),
            ))
            assertEquals(text + if (expected) "\n\nFact" else "", LorebookMatcher.context(books, emptyList()), text)
        }
        for ((rule, text, expected) in listOf(
            Triple(SecondaryKeywordRule.MatchAny, "door red", true), Triple(SecondaryKeywordRule.MatchAny, "door", false),
            Triple(SecondaryKeywordRule.NotAll, "door red", true), Triple(SecondaryKeywordRule.NotAll, "door red key", false),
            Triple(SecondaryKeywordRule.MatchNone, "door", true), Triple(SecondaryKeywordRule.MatchNone, "door red", false),
            Triple(SecondaryKeywordRule.MatchAll, "door red key", true), Triple(SecondaryKeywordRule.MatchAll, "door red", false),
        )) {
            val books = listOf(book(
                LoreEntry(text, constant = true),
                LoreEntry("Fact", keys = listOf("door"), secondaryKeys = listOf("red", "key"), selective = true, selectiveLogic = rule),
            ))
            assertEquals(text + if (expected) "\n\nFact" else "", LorebookMatcher.context(books, emptyList()), "rule=$rule text=$text")
        }
    }

    @Test(timeout = 2_000) fun `failed probability is not retried when later rounds repeat its keyword`() {
        val books = listOf(book(
            LoreEntry("bridge", constant = true),
            LoreEntry("seed", keys = listOf("bridge")),
            LoreEntry("Should not appear", keys = listOf("seed"), probability = 50),
        ))
        var rolls = 0
        val result = LorebookMatcher.context(books, listOf("seed")) { if (rolls++ == 0) 99 else 0 }
        assertEquals("bridge\n\nseed", result)
        assertEquals(1, rolls)
    }

    @Test(timeout = 2_000) fun `successful probability is rolled once even in a self cycle`() {
        var rolls = 0
        val books = listOf(book(LoreEntry("seed", keys = listOf("seed"), probability = 50)))
        assertEquals("seed", LorebookMatcher.context(books, listOf("seed"), charLimit = 0) { rolls++; 0 })
        assertEquals(1, rolls)
    }

    @Test fun `disabled blank and zero probability entries cannot propagate`() {
        val books = listOf(book(
            LoreEntry("bridge", keys = listOf("seed"), enabled = false),
            LoreEntry("bridge", keys = listOf("seed"), probability = 0),
            LoreEntry(" ", constant = true),
            LoreEntry("Fact", keys = listOf("bridge")),
        ))
        assertEquals("", LorebookMatcher.context(books, listOf("seed")))
    }

    @Test fun `entries that exceed the budget do not trigger otherwise affordable entries`() {
        val books = listOf(book(
            LoreEntry("bridge", keys = listOf("seed")),
            LoreEntry("x", keys = listOf("bridge")),
        ))
        assertEquals("", LorebookMatcher.context(books, listOf("seed"), charLimit = 1))
    }

    @Test fun `recursive rounds share the remaining budget including separators`() {
        val books = listOf(book(
            LoreEntry("beta", keys = listOf("seed"), order = 1),
            LoreEntry("x", keys = listOf("beta"), order = 200),
        ))
        assertEquals("beta", LorebookMatcher.context(books, listOf("seed"), charLimit = 6))
        assertEquals("beta\n\nx", LorebookMatcher.context(books, listOf("seed"), charLimit = 7))
        assertEquals("beta\n\nx", LorebookMatcher.context(books, listOf("seed"), charLimit = 0))
    }

    @Test(timeout = 5_000) fun `long cycle terminates without an arbitrary round limit or stack recursion`() {
        val count = 256
        val entries = (0 until count).map {
            LoreEntry("[node:${(it + 1) % count}]", keys = listOf("[node:$it]"))
        }
        val world = Lorebook("cycle", "Cycle", entries)
        assertEquals(entries.joinToString("\n\n") { it.content }, LorebookMatcher.context(listOf(world), listOf("[node:0]"), charLimit = 0))
    }

    @Test fun `activation state is isolated across repeated and reentrant calls`() {
        val books = listOf(book(
            LoreEntry("bridge", keys = listOf("seed"), probability = 50),
            LoreEntry("seed", keys = listOf("bridge")),
        ))
        val messages = listOf("seed")
        repeat(2) {
            assertEquals("bridge\n\nseed", LorebookMatcher.context(books, messages) {
                assertEquals("", LorebookMatcher.context(books, listOf("unrelated")))
                0
            })
            assertEquals("", LorebookMatcher.context(books, messages) { 99 })
        }
        assertEquals(listOf("seed"), messages)
    }
}
