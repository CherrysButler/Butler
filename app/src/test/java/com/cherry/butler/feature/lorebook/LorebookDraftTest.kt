package com.cherry.butler.feature.lorebook

import com.cherry.butler.core.lorebook.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class LorebookDraftTest {
    private val entry = LoreEntry("The castle is red.", keys = listOf("castle"), name = "Castle")
    private val book = Lorebook("world", "World", listOf(entry), favorite = true)
    private val library = LorebookLibrary(
        books = listOf(book), bindings = mapOf("persona:p" to setOf("world"), "chat:1" to setOf("world")),
        excluded = mapOf("chat:2" to setOf("world")),
    )

    @Test fun `editing a draft does not affect generation until Save`() {
        val original = LorebookDraft(book)
        val draft = original.copy(name = "Updated", entries = listOf(EditableLoreEntry(entry = entry.copy(content = "The castle is blue."))))
        assertFalse(original.dirty)
        assertTrue(draft.dirty)
        assertEquals("The castle is red.", LorebookMatcher.context(library.books, listOf("castle")))
        val saved = draft.saveInto(library)
        assertEquals("The castle is blue.", LorebookMatcher.context(saved.books, listOf("castle")))
        assertEquals("The castle is red.", library.books.single().entries.single().content)
        assertEquals("world", saved.books.single().id)
        assertEquals("Updated", saved.books.single().name)
        assertTrue(saved.books.single().favorite)
        assertEquals(library.bindings, saved.bindings)
        assertEquals(library.excluded, saved.excluded)
    }

    @Test fun `a new draft saves a new independent book`() {
        val blank = LorebookDraft(null)
        assertFalse(blank.dirty)
        val created = blank.copy(name = " My world ", entries = listOf(EditableLoreEntry(entry = entry)))
        val saved = created.saveInto(library)
        assertEquals(2, saved.books.size)
        assertEquals("My world", saved.books.last().name)
        assertNotEquals("world", saved.books.last().id)
        assertFalse(saved.books.last().favorite)
        assertEquals(library.bindings, saved.bindings)
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
        assertEquals(listOf(book), library.books)

        val saved = draft.copy(name = "Edited import").saveInto(library)
        assertEquals(listOf(book, imported.copy(name = "Edited import")), saved.books)
        assertEquals(library.bindings, saved.bindings)
        assertEquals(library.excluded, saved.excluded)
    }

    @Test fun `unnamed pasted lorebook requires a name before saving`() {
        val imported = LorebookJson.parse("""[{"content":"Janitor fact","key":["castle"]}]""")
        val draft = LorebookDraft.fromImport(imported)
        assertEquals("", draft.name)
        assertFailsWith<IllegalArgumentException> { draft.saveInto(library) }
        assertEquals("Castle", draft.copy(name = "Castle").saveInto(library).books.last().name)
    }

    @Test fun `deleted books cannot be resurrected by an old editor draft`() {
        assertFailsWith<IllegalStateException> { LorebookDraft(book).saveInto(library.delete("world")) }
    }

    @Test fun `new book save enables it atomically but editing does not turn a disabled book on`() {
        val created = LorebookDraft(null).copy(name = "New", entries = listOf(EditableLoreEntry(entry = entry)))
        val saved = created.saveInto(library, "chat:2")
        assertEquals(setOf(created.id), saved.enabled("chat:2"))
        assertEquals(created.id, saved.books.last().id)
        val edited = LorebookDraft(book).copy(name = "Edited").saveInto(library, "chat:2")
        assertTrue(edited.enabled("chat:2").isEmpty())
    }

    @Test fun `invalid draft settings fail without mutating saved books`() {
        val draft = LorebookDraft(book)
        for (invalid in listOf(draft.copy(name = " "), draft.copy(name = "x".repeat(201)), draft.copy(scanDepth = "101"), draft.copy(scanDepth = "-1"), draft.copy(scanDepth = ""), draft.copy(entries = emptyList()))) {
            assertFailsWith<IllegalArgumentException> { invalid.saveInto(library) }
        }
        assertEquals(book, library.books.single())
        assertEquals(0, draft.copy(scanDepth = "0").saveInto(library).books.single().scanDepth)
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
