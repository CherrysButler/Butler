package com.cherry.butler.feature.lorebook

import androidx.lifecycle.ViewModel
import com.cherry.butler.core.data.EditableLoreEntry
import com.cherry.butler.core.data.LorebookDraft
import com.cherry.butler.core.data.LorebookDraftRepository
import com.cherry.butler.core.lorebook.LoreEntry
import com.cherry.butler.core.lorebook.Lorebook
import com.cherry.butler.core.lorebook.LorebookStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LorebookEditorViewModel @Inject constructor(
    private val store: LorebookStore,
    private val drafts: LorebookDraftRepository,
) : ViewModel() {
    val contextCharLimit = store.contextCharLimit

    private val _editor = MutableStateFlow<LorebookDraft?>(null)
    val editor = _editor.asStateFlow()
    private val _entry = MutableStateFlow<LoreEntrySession?>(null)
    val entry = _entry.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    suspend fun loadEditor(id: String?, importDraftId: String? = null) {
        if (_editor.value != null) return
        _error.value = null
        if (importDraftId != null) {
            _editor.value = drafts.take(importDraftId)
            if (_editor.value == null) _error.value = "This import draft is unavailable. Go back and import it again."
            return
        }
        try {
            _editor.value = withContext(Dispatchers.IO) {
                val book = id?.let { checkNotNull(store.book(it)) { "That lorebook no longer exists." } }
                LorebookDraft(book)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Couldn't load this lorebook." }
    }

    private fun closeEditor() { _editor.value = null; _entry.value = null; _error.value = null }
    fun updateEditor(change: (LorebookDraft) -> LorebookDraft) {
        if (_busy.value) return
        _editor.update { it?.let(change) }
        _error.value = null
    }

    fun editEntry(id: String? = null) {
        val draft = _editor.value ?: return
        if (id == null && draft.entries.size >= Lorebook.MAX_ENTRIES) {
            _error.value = "A lorebook can contain up to ${Lorebook.MAX_ENTRIES} entries."
            return
        }
        val value = if (id == null) LoreEntry(content = "") else draft.entries.firstOrNull { it.id == id }?.entry ?: return
        _entry.value = LoreEntrySession(id, LoreEntryForm(value))
        _error.value = null
    }

    fun updateEntry(change: (LoreEntryForm) -> LoreEntryForm) {
        _entry.update { it?.copy(form = change(it.form)) }
        _error.value = null
    }

    fun closeEntry() { _entry.value = null; _error.value = null }

    fun applyEntry() {
        val session = _entry.value ?: return
        if (session.id != null && !session.dirty) { closeEntry(); return }
        try {
            val value = session.form.entry()
            updateEditor { draft -> draft.copy(entries = if (session.id == null) draft.entries + EditableLoreEntry(entry = value)
                else draft.entries.map { if (it.id == session.id) it.copy(entry = value) else it }) }
            closeEntry()
        } catch (e: IllegalArgumentException) { _error.value = e.message }
    }

    fun duplicateEntry(id: String) {
        if ((_editor.value?.entries?.size ?: 0) >= Lorebook.MAX_ENTRIES) {
            _error.value = "A lorebook can contain up to ${Lorebook.MAX_ENTRIES} entries."
            return
        }
        updateEditor { draft ->
            val index = draft.entries.indexOfFirst { it.id == id }
            if (index < 0) draft else draft.copy(entries = draft.entries.toMutableList().apply {
                add(index + 1, EditableLoreEntry(entry = draft.entries[index].entry.copy(name = draft.entries[index].entry.name.let { if (it.isBlank()) "Copy" else "$it (copy)" })))
            })
        }
    }

    fun reportError(message: String) { _error.value = message }

    suspend fun deleteEditorBook(): Boolean {
        val draft = _editor.value ?: return false
        if (draft.original == null || _busy.value) return false
        _busy.value = true
        _error.value = null
        return try {
            store.delete(draft.id)
            closeEditor()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = e.message ?: "Couldn't delete this lorebook."
            false
        } finally {
            _busy.value = false
        }
    }

    suspend fun saveEditor(enableInScope: String?): Pair<String, Boolean>? {
        val draft = _editor.value ?: return null
        if (_busy.value) return null
        _busy.value = true
        _error.value = null
        return try {
            withContext(Dispatchers.IO) { store.save(draft.toBook(), isNew = draft.original == null, enableInScope = enableInScope) }
            closeEditor()
            draft.id to (draft.original == null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Couldn't save this lorebook."; null }
        finally { _busy.value = false }
    }
}
