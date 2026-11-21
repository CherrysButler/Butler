package com.cherry.butler.feature.lorebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.lorebook.Lorebook
import com.cherry.butler.core.lorebook.LorebookJson
import com.cherry.butler.core.lorebook.LorebookMatcher
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.DropItem
import com.cherry.butler.feature.settings.DropRow
import com.cherry.butler.feature.settings.EditorMenuItem
import com.cherry.butler.ui.components.rememberFileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A regular navigation screen, using the same activity window as the other editors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LorebookEditorScreen(
    bookId: String?,
    newBookScope: String?,
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
    viewModel: LorebookEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    importDraftId: String? = null,
) {
    val draft by viewModel.editor.collectAsStateWithLifecycle()
    val book = draft
    val session by viewModel.entry.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val limit by viewModel.contextCharLimit.collectAsStateWithLifecycle(initialValue = LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT)
    var discard by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteBook by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val saveFile = rememberFileSaver(onError = viewModel::reportError)
    LaunchedEffect(bookId, importDraftId, viewModel) { viewModel.loadEditor(bookId, importDraftId) }
    if (book == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text("Lorebook", style = MaterialTheme.typography.titleLarge)
            }
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    val savedBook = book.original
    fun back() {
        if (busy) return
        if (session?.dirty ?: book.dirty) discard = true
        else if (session != null) viewModel.closeEntry() else onBack()
    }

    androidx.activity.compose.BackHandler(onBack = ::back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        title = { Text(if (session != null) "Edit entry" else if (book.original == null) "New lorebook" else "Edit lorebook", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = ::back, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                        actions = {
                            if (session == null && savedBook != null) {
                                Box {
                                    IconButton(enabled = !busy, onClick = { menuOpen = true }) {
                                        Icon(Icons.Outlined.MoreVert, "More", tint = ButlerTheme.colors.textMed)
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false },
                                        shape = MaterialTheme.shapes.medium,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        EditorMenuItem("Export JSON", Icons.Outlined.FileDownload) {
                                            menuOpen = false
                                            coroutineScope.launch {
                                                val json = withContext(Dispatchers.Default) { LorebookJson.export(savedBook) }
                                                val name = savedBook.name.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '_' else it }
                                                    .joinToString("").trim().trimEnd('.').ifBlank { "Lorebook" }
                                                saveFile("$name.json", json)
                                            }
                                        }
                                        EditorMenuItem("Delete lorebook", Icons.Outlined.Delete, danger = true) {
                                            menuOpen = false
                                            confirmDeleteBook = true
                                        }
                                    }
                                }
                            }
                            KeyButton(
                                label = if (busy) "Saving…" else if (session != null) "Done" else "Save",
                                onClick = {
                                    if (session != null) viewModel.applyEntry()
                                    else coroutineScope.launch {
                                        viewModel.saveEditor(newBookScope)?.let { (id, created) -> onSaved(id, created) }
                                    }
                                },
                                enabled = !busy,
                                primary = true,
                                modifier = Modifier.heightIn(min = 40.dp).padding(end = 12.dp),
                            )
                        },
                    )
                    error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (session != null) {
                    item { EntryFields(session!!.form, limit, viewModel::updateEntry) }
                } else {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = book.name, onValueChange = { text -> viewModel.updateEditor { it.copy(name = text) } },
                                label = { Text("Lorebook name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            )
                            NumberField(
                                label = "Default scan depth", value = book.scanDepth, hint = "Recent messages to scan, from 0 to 100.", enabled = !busy,
                                onChange = { text -> viewModel.updateEditor { it.copy(scanDepth = text) } },
                            )
                            Text("Entries (${book.entries.size})", style = MaterialTheme.typography.titleMedium)
                            Text("Edit entries below, then save the lorebook to apply your changes.", style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton(enabled = !busy && book.entries.size < Lorebook.MAX_ENTRIES, onClick = { viewModel.editEntry() }) {
                                Icon(Icons.Outlined.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add entry")
                            }
                        }
                    }
                    if (book.entries.isEmpty()) item {
                        Text("No entries yet. Add a fact, place or character detail and choose when it becomes active.", modifier = Modifier.padding(16.dp))
                    }
                    itemsIndexed(book.entries, key = { _, row -> row.id }) { index, row ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier.clickable(enabled = !busy) { viewModel.editEntry(row.id) },
                            headlineContent = { Text(row.entry.name.ifBlank { "Entry ${index + 1}" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Column {
                                    Text(if (row.entry.constant) "Always active" else row.entry.keys.joinToString(", ").ifBlank { "No keywords" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.entry.content.ifBlank { "No content" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = row.entry.enabled, enabled = !busy,
                                        onCheckedChange = { on -> viewModel.updateEditor { draft -> draft.copy(entries = draft.entries.map { if (it.id == row.id) it.copy(entry = it.entry.copy(enabled = on)) else it }) } },
                                        modifier = Modifier.semantics { contentDescription = "Enable entry ${index + 1}" },
                                    )
                                    Box {
                                        IconButton(enabled = !busy, onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Entry ${index + 1} actions") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; viewModel.editEntry(row.id) })
                                            DropdownMenuItem(text = { Text("Duplicate") }, enabled = book.entries.size < Lorebook.MAX_ENTRIES, onClick = { menu = false; viewModel.duplicateEntry(row.id) })
                                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = row.id })
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("Discard changes?") },
        text = {
            Text(if (session != null) "Your changes to this entry have not been applied to the lorebook draft." else "Your lorebook changes have not been saved.")
        },
        confirmButton = { TextButton(onClick = { discard = false; if (session != null) viewModel.closeEntry() else onBack() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
    )
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete entry?") },
            text = {
                Text("The entry will be removed when you save the lorebook.")
            },
            confirmButton = { TextButton(onClick = { viewModel.updateEditor { it.copy(entries = it.entries.filterNot { row -> row.id == id }) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (confirmDeleteBook && savedBook != null) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete lorebook?",
            body = "\"${savedBook.name}\" will be removed from this device and unlinked from all chats and personas.",
            onConfirm = {
                confirmDeleteBook = false
                coroutineScope.launch { if (viewModel.deleteEditorBook()) onBack() }
            },
            onDismiss = { confirmDeleteBook = false },
        )
    }
}

@Composable
private fun EntryFields(form: LoreEntryForm, charLimit: Int, update: ((LoreEntryForm) -> LoreEntryForm) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = form.value.name, onValueChange = { text -> update { it.copy(value = it.value.copy(name = text)) } },
            label = { Text("Entry name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.value.content, onValueChange = { text -> update { it.copy(value = it.value.copy(content = text)) } },
            label = { Text("Content") }, supportingText = { Text("The world information the model receives when this entry activates.") },
            minLines = 6, maxLines = 14, modifier = Modifier.fillMaxWidth())
        if (charLimit > 0 && form.value.content.length > charLimit) Text(
            "This entry exceeds the $charLimit-character lore budget and will be skipped during generation.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        )
        EditorSwitch("Enabled", "Disabled entries are never included.", form.value.enabled) { on -> update { it.copy(value = it.value.copy(enabled = on)) } }
        EditorSwitch("Always active", "Include without matching keywords.", form.value.constant) { on -> update { it.copy(value = it.value.copy(constant = on)) } }
        OutlinedTextField(value = form.keys, onValueChange = { text -> update { it.copy(keys = text) } }, label = { Text("Primary keywords") },
            supportingText = { Text(if (form.value.constant) "Ignored while Always active is on." else "One keyword or phrase per line. Any match can activate the entry.") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
        EditorSwitch("Secondary conditions", "Require additional keyword conditions. Ignored for always-active entries.", form.value.selective) { on -> update { it.copy(value = it.value.copy(selective = on)) } }
        if (form.value.selective) {
            OutlinedTextField(value = form.secondaryKeys, onValueChange = { text -> update { it.copy(secondaryKeys = text) } },
                label = { Text("Secondary keywords") }, supportingText = { Text("One keyword or phrase per line.") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            val conditions = listOf("Match any secondary keyword", "Not all secondary keywords", "Match no secondary keywords", "Match all secondary keywords")
            DropRow(title = "Secondary condition", value = conditions.getOrNull(form.value.selectiveLogic) ?: conditions[0]) { close ->
                conditions.forEachIndexed { index, title -> DropItem(title, selected = index == form.value.selectiveLogic, onClick = { close(); update { it.copy(value = it.value.copy(selectiveLogic = index)) } }) }
            }
        }
        EditorSwitch("Case sensitive", "Match uppercase and lowercase exactly.", form.value.caseSensitive) { on -> update { it.copy(value = it.value.copy(caseSensitive = on)) } }
        EditorSwitch("Whole words", "Avoid matching a keyword inside a longer word.", form.value.wholeWords) { on -> update { it.copy(value = it.value.copy(wholeWords = on)) } }
        NumberField("Priority", form.order, "Higher values get priority when the lore budget is full.") { text -> update { it.copy(order = text) } }
        NumberField("Scan depth override", form.scanDepth, "Leave blank to use the lorebook default. Otherwise 0–100 messages.") { text -> update { it.copy(scanDepth = text) } }
        NumberField("Probability (%)", form.probability, "0 never activates; 100 always passes the probability check.") { text -> update { it.copy(probability = text) } }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NumberField(label: String, value: String, hint: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, supportingText = { Text(hint) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = enabled, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EditorSwitch(label: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!value) }, headlineContent = { Text(label) }, supportingContent = { Text(hint) },
        trailingContent = { Switch(checked = value, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label }) },
    )
}
