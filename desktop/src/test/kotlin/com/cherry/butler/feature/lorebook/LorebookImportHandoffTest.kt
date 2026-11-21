package com.cherry.butler.feature.lorebook

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.cherry.butler.core.data.LorebookDraft
import com.cherry.butler.core.data.LorebookDraftRepository
import com.cherry.butler.core.lorebook.LorebookDatabase
import com.cherry.butler.core.lorebook.LorebookStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LorebookImportHandoffTest {
    @Test fun `editor can claim an unsaved import after the library is cleared`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<LorebookDatabase>().setDriver(BundledSQLiteDriver()).build()
        try {
            val store = LorebookStore(db)
            val drafts = LorebookDraftRepository()
            val libraryOwner = ViewModelStore()
            val factory = viewModelFactory {
                initializer { LorebooksLibraryViewModel(LorebookListDelegate(store), store, drafts) }
            }
            val library = ViewModelProvider.create(libraryOwner, factory)[LorebooksLibraryViewModel::class]
            val draftId = library.prepareImport("""[{"content":"A fact","key":["castle"]}]""", fallbackName = "Castle")
            libraryOwner.clear()

            val editor = LorebookEditorViewModel(store, drafts)
            editor.loadEditor(null, draftId)
            val draft = assertNotNull(editor.editor.value)
            assertEquals(draftId, draft.id)
            assertEquals("Castle", draft.name)
            assertNull(drafts.take(draftId))
            assertNull(store.book(draft.id))

            editor.updateEditor { it.copy(name = "Edited castle") }
            editor.loadEditor(null, draftId)
            assertEquals(draft.id to true, editor.saveEditor(enableInScope = null))
            assertEquals("Edited castle", store.book(draft.id)?.name)
        } finally {
            db.close()
        }
    }

    @Test fun `each pending import can be claimed only by its own ID`() {
        val drafts = LorebookDraftRepository()
        val first = LorebookDraft(original = null)
        val second = LorebookDraft(original = null)
        drafts.put(first)
        drafts.put(second)

        assertNull(drafts.take("missing"))
        assertEquals(second, drafts.take(second.id))
        assertNull(drafts.take(second.id))
        assertEquals(first, drafts.take(first.id))
    }
}
