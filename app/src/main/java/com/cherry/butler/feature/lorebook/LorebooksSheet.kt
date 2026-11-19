package com.cherry.butler.feature.lorebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.lorebook.*
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LorebooksViewModel @Inject constructor(
    private val store: LorebookStore,
) : ViewModel() {
    val library = store.library

    fun change(update: (LorebookLibrary) -> LorebookLibrary) {
        viewModelScope.launch(Dispatchers.IO) { store.update(update) }
    }

    fun setContextCharLimit(limit: Int) {
        require(limit >= 0) { "Lore character limit cannot be negative." }
        change { it.copy(contextCharLimit = limit) }
    }

    private val _editor = MutableStateFlow<LorebookDraft?>(null)
    val editor = _editor.asStateFlow()
    private var pendingImport: LorebookDraft? = null
    private val _entry = MutableStateFlow<LoreEntrySession?>(null)
    val entry = _entry.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadEditor(id: String?, takeImportedDraft: (() -> LorebookDraft?)? = null) {
        if (_editor.value != null) return
        _error.value = null
        if (takeImportedDraft != null) {
            _editor.value = takeImportedDraft()
            if (_editor.value == null) _error.value = "This import draft is unavailable. Go back and import it again."
            return
        }
        val book = id?.let { bookId -> library.value.books.firstOrNull { it.id == bookId } }
        if (id != null && book == null) {
            _error.value = "That lorebook no longer exists."
            return
        }
        _editor.value = LorebookDraft(book)
    }

    fun closeEditor() { _editor.value = null; _entry.value = null; _error.value = null }
    fun updateEditor(change: (LorebookDraft) -> LorebookDraft) {
        if (_busy.value) return
        _editor.update { it?.let(change) }
        _error.value = null
    }

    fun editEntry(id: String? = null) {
        val draft = _editor.value ?: return
        if (id == null && draft.entries.size >= 5000) { _error.value = "A lorebook can contain up to 5000 entries."; return }
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
        if ((_editor.value?.entries?.size ?: 0) >= 5000) { _error.value = "A lorebook can contain up to 5000 entries."; return }
        updateEditor { draft ->
            val index = draft.entries.indexOfFirst { it.id == id }
            if (index < 0) draft else draft.copy(entries = draft.entries.toMutableList().apply {
                add(index + 1, EditableLoreEntry(entry = draft.entries[index].entry.copy(name = draft.entries[index].entry.name.let { if (it.isBlank()) "Copy" else "$it (copy)" })))
            })
        }
    }

    suspend fun prepareImport(text: String, fallbackName: String) {
        pendingImport = withContext(Dispatchers.Default) {
            LorebookDraft.fromImport(LorebookJson.parse(text, fallbackName = fallbackName))
        }
    }

    fun takeImportedDraft(): LorebookDraft? {
        val draft = pendingImport
        pendingImport = null
        return draft
    }

    fun reportError(message: String) { _error.value = message }

    suspend fun deleteEditorBook(): Boolean {
        val draft = _editor.value ?: return false
        if (draft.original == null || _busy.value) return false
        _busy.value = true
        _error.value = null
        return try {
            withContext(Dispatchers.IO) { store.update { it.delete(draft.id) } }
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
            withContext(Dispatchers.IO) { store.update { draft.saveInto(it, enableInScope) } }
            closeEditor()
            draft.id to (draft.original == null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Couldn't save this lorebook."; null }
        finally { _busy.value = false }
    }
}

/** One library shared by chat switches and persona bindings; a new persona keeps a draft selection. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LorebooksSheet(
    scope: String?,
    onDismiss: () -> Unit,
    onEdit: (String?) -> Unit,
    inheritedPersonaScope: String? = null,
    selection: Set<String>? = null,
    onSelectionChange: ((Set<String>) -> Unit)? = null,
    viewModel: LorebooksViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val inherited = library.bindings[inheritedPersonaScope].orEmpty()
    val selected = selection ?: scope?.let { library.enabled(it, inheritedPersonaScope) }.orEmpty()
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val books = library.books.filter { !favoritesOnly || it.favorite }
        .sortedWith(compareByDescending<Lorebook> { it.favorite }.thenBy { it.name.lowercase() })
    fun toggle(id: String, on: Boolean) {
        if (onSelectionChange != null) onSelectionChange(if (on) selected + id else selected - id)
        else if (scope != null) viewModel.change { it.toggle(scope, id, on) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                PlateText("Local Lorebooks", PlateLevel.Name, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(null) }) { Text("Create") }
            }
            Text(
                if (scope?.startsWith("chat:") == true) "Enabled books provide context for this chat. Persona books are included automatically. Tap a book to edit it."
                else "Enabled books provide context whenever you chat as this persona. Tap a book to edit it.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("All") })
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("Favorites") })
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (books.isEmpty()) item {
                    Text(
                        if (favoritesOnly) "No favorite lorebooks yet. Tap a star to add one."
                        else "No lorebooks yet. Create one here or import JSON in Settings.",
                        modifier = Modifier.padding(20.dp),
                    )
                }
                items(books, key = { it.id }) { book ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(book.id) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(book.name) },
                        supportingContent = {
                            Text("${book.entries.size} entries" + if (book.id in inherited) " · From persona" else "")
                        },
                        leadingContent = {
                            IconButton(onClick = {
                                viewModel.change { lib -> lib.copy(books = lib.books.map { if (it.id == book.id) it.copy(favorite = !it.favorite) else it }) }
                            }) {
                                Icon(
                                    if (book.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (book.favorite) "Remove ${book.name} from favorites" else "Favorite ${book.name}",
                                    tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = book.id in selected,
                                onCheckedChange = { toggle(book.id, it) },
                                modifier = Modifier.semantics { contentDescription = "Use ${book.name}" },
                            )
                        },
                    )
                }
            }
        }
    }
}
