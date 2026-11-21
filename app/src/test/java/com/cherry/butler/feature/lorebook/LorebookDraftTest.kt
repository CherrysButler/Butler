package com.cherry.butler.feature.lorebook

import com.cherry.butler.core.data.EditableLoreEntry
import com.cherry.butler.core.data.LorebookDraft
import com.cherry.butler.core.lorebook.LoreEntry
import com.cherry.butler.core.lorebook.Lorebook
import com.cherry.butler.core.lorebook.LorebookJson
import com.cherry.butler.core.lorebook.LorebookMatcher
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LorebookDraftTest {
    private val entry = LoreEntry("The castle is red.", keys = listOf("castle"), name = "Castle")
    private val book = Lorebook("world", "World", listOf(entry), favorite = true)

    @Test fun `editing a draft does not affect generation until Save`() {
        val original = LorebookDraft(book)
        val draft = original.copy(name = "Updated", entries = listOf(EditableLoreEntry(entry = entry.copy(content = "The castle is blue."))))
        assertFalse(original.dirty)
        assertTrue(draft.dirty)
        assertEquals("The castle is red.", LorebookMatcher.context(listOf(book), listOf("castle")))
        val saved = draft.toBook()
        assertEquals("The castle is blue.", LorebookMatcher.context(listOf(saved), listOf("castle")))
        assertEquals("The castle is red.", book.entries.single().content)
        assertEquals("world", saved.id)
        assertEquals("Updated", saved.name)
        assertTrue(saved.favorite)
    }

    @Test fun `a new draft saves a new independent book`() {
        val blank = LorebookDraft(null)
        assertFalse(blank.dirty)
        val created = blank.copy(name = " My world ", entries = listOf(EditableLoreEntry(entry = entry)))
        val saved = created.toBook()
        assertEquals("My world", saved.name)
        assertNotEquals("world", saved.id)
        assertFalse(saved.favorite)
    }

    @Test fun `import opens a dirty new draft and saves the edited version beside existing books`() {
        val imported = LorebookJson.parse(
            """{"name":"World","scan_depth":8,"entries":{"0":{"content":"Imported fact","key":["castle"]}}}""",
        )
        val draft = LorebookDraft.fromImport(imported)
        assertNull(draft.original)
        assertTrue(draft.dirty)
        assertEquals("World", draft.name)
        assertEquals("8", draft.scanDepth)
        assertEquals(imported.entries, draft.entries.map { it.entry })

        val saved = draft.copy(name = "Edited import").toBook()
        assertEquals(imported.copy(name = "Edited import"), saved)
    }

    @Test fun `import and save accept exactly the entry limit and reject one more`() {
        fun importBook(count: Int): Lorebook {
            val json = (1..count).joinToString(prefix = "[", postfix = "]") { """{"content":"Fact"}""" }
            return LorebookJson.parse(json, fallbackName = "World")
        }

        val draft = LorebookDraft.fromImport(importBook(Lorebook.MAX_ENTRIES))
        assertEquals(Lorebook.MAX_ENTRIES, draft.toBook().entries.size)
        assertFailsWith<IllegalArgumentException> { importBook(Lorebook.MAX_ENTRIES + 1) }
        val extra = EditableLoreEntry(entry = entry)
        assertFailsWith<IllegalArgumentException> { draft.copy(entries = draft.entries + extra).toBook() }
    }

    @Test fun `unnamed pasted lorebook requires a name before saving`() {
        val imported = LorebookJson.parse("""[{"content":"Janitor fact","key":["castle"]}]""")
        val draft = LorebookDraft.fromImport(imported)
        assertEquals("", draft.name)
        assertFailsWith<IllegalArgumentException> { draft.toBook() }
        assertEquals("Castle", draft.copy(name = "Castle").toBook().name)
    }

    @Test fun `invalid draft settings fail without mutating saved books`() {
        val draft = LorebookDraft(book)
        for (invalid in listOf(draft.copy(name = " "), draft.copy(name = "x".repeat(201)), draft.copy(scanDepth = "101"), draft.copy(scanDepth = "-1"), draft.copy(scanDepth = ""), draft.copy(entries = emptyList()))) {
            assertFailsWith<IllegalArgumentException> { invalid.toBook() }
        }
        assertEquals(0, draft.copy(scanDepth = "0").toBook().scanDepth)
    }

    @Test fun `entry edits validate activation and preserve numeric settings`() {
        val form = LoreEntryForm(entry).copy(keys = " castle \n\ncastle\nblack gate ", secondaryKeys = " key \nmoon", order = "-20", probability = "0", scanDepth = "0")
        val saved = form.entry()
        assertEquals(listOf("castle", "black gate"), saved.keys)
        assertEquals(listOf("key", "moon"), saved.secondaryKeys)
        assertEquals(-20, saved.order)
        assertEquals(0, saved.probability)
        assertEquals(0, saved.scanDepth)
        assertNull(form.copy(scanDepth = "").entry().scanDepth)
        assertFailsWith<IllegalArgumentException> { form.copy(keys = "").entry() }
        assertTrue(form.copy(keys = "", value = entry.copy(constant = true)).entry().constant)
        assertFalse(form.copy(keys = "", value = entry.copy(enabled = false)).entry().enabled)
        for (invalid in listOf(form.copy(value = entry.copy(content = " ")), form.copy(probability = "101"), form.copy(probability = "-1"), form.copy(probability = ""), form.copy(order = "abc"), form.copy(scanDepth = "-1"), form.copy(scanDepth = "101"))) {
            assertFailsWith<IllegalArgumentException> { invalid.entry() }
        }
    }

    @Test fun `entry names load from old data and survive local persistence`() {
        val old = Json.decodeFromString(LoreEntry.serializer(), """{"content":"An old fact","constant":true}""")
        assertEquals("", old.name)
        assertEquals(entry, Json.decodeFromString(LoreEntry.serializer(), Json.encodeToString(LoreEntry.serializer(), entry)))
    }
}
