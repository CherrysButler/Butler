package com.cherry.butler.feature.lorebook

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.frostSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.lorebook.LorebookMatcher
import com.cherry.butler.core.lorebook.LorebookJson
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.NumberRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The whole library, independent of any chat/persona activation switches. */
@Composable
fun LorebooksLibraryScreen(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onEditImport: (draftId: String) -> Unit,
    viewModel: LorebooksLibraryViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle(initialValue = emptyList())
    val limit by viewModel.contextCharLimit.collectAsStateWithLifecycle(initialValue = LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT)
    var importing by rememberSaveable { mutableStateOf(false) }

    // Making or bringing in a book is the round key at the foot of the page, as on Profile:
    // always in reach. (The two keys for it sat under the whole list.) With Depth Lifted the
    // key is glass over the page, so the page is what it looks through.
    val keyGlass = if (com.cherry.butler.ui.components.glassOn()) io.github.fletchmckee.liquid.rememberLiquidState() else null
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().frostSource(keyGlass)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    if (books.isEmpty()) "Local lorebooks" else "Local lorebooks · ${books.size}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (books.isEmpty()) {
                // Nothing to list: say so, and say the one thing a new book needs said.
                CenteredMessage(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "No lorebooks yet",
                    body = "Make one or bring one in as JSON. A book is switched on in a chat, or attached to a persona.",
                ) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KeyButton("Import JSON", onClick = { importing = true })
                        KeyButton("New lorebook", onClick = { onEdit(null) }, primary = true)
                    }
                }
            } else {
                // Each book a card of its own; the room at the foot is the round key's.
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .card()
                                .clickable(onClickLabel = "Edit ${book.name}") { onEdit(book.id) }
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    book.name.ifBlank { "Lorebook" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (book.entryCount == 1) "1 entry" else "${book.entryCount} entries",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = ButlerTheme.colors.textMed,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            LorebookFavoriteButton(
                                isFavorite = book.favorite,
                                bookName = book.name,
                                onClick = { viewModel.toggleFavorite(book.id) },
                            )
                        }
                    }
                    // What every book shares: how much lore a message may carry in all.
                    item(key = "limit") {
                        com.cherry.butler.feature.settings.SettingsSection(title = "For every book") {
                            NumberRow(
                                title = "Lore character limit",
                                subtitle = null,
                                value = limit,
                                onCommit = viewModel::setContextCharLimit,
                                range = 0..Int.MAX_VALUE,
                            )
                        }
                    }
                }
            }
        }
        if (books.isNotEmpty()) {
            com.cherry.butler.ui.components.GlassKey(
                glass = keyGlass,
                choices = listOf(
                    com.cherry.butler.ui.components.GlassKeyChoice("New lorebook", Icons.AutoMirrored.Outlined.NoteAdd) { onEdit(null) },
                    com.cherry.butler.ui.components.GlassKeyChoice("Import JSON", Icons.Outlined.FileOpen) { importing = true },
                ),
                label = "New or import a lorebook",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
            )
        }
    }
    if (importing) {
        ImportLorebookDialog(
            onImport = viewModel::prepareImport,
            onPrepared = { draftId ->
                try {
                    onEditImport(draftId)
                } catch (e: Exception) {
                    viewModel.discardImport(draftId)
                    throw e
                }
                importing = false
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun ImportLorebookDialog(
    onImport: suspend (String, String) -> String,
    onPrepared: (draftId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runImport(action: suspend () -> String) {
        if (busy) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                val draftId = action()
                onPrepared(draftId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't import the lorebook."
            } finally {
                busy = false
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport {
            val (json, fileName) = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty()
                val contents = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(8192)
                    buildString {
                        while (true) {
                            val count = reader.read(buffer)
                            if (count == -1) break
                            require(length + count <= LorebookJson.MAX_JSON_CHARS) { "Lorebook JSON must be under 2 million characters." }
                            append(buffer, 0, count)
                        }
                    }
                } ?: error("Couldn't open the file.")
                contents to name.substringBeforeLast('.')
            }
            onImport(json, fileName)
        }
    }
    // Butler's own dialog and field, as renaming a chat or naming a group uses: a file, or
    // the JSON pasted in.
    com.cherry.butler.ui.components.ButlerDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(vertical = 16.dp),
        ) {
            Text(
                "Import a lorebook",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            KeyButton(
                if (busy) "Reading…" else "Choose a JSON file",
                onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                enabled = !busy,
            )
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                com.cherry.butler.feature.settings.FieldBlock(
                    label = "Or paste its JSON",
                    value = text,
                    onChange = { if (!busy) { text = it; error = null } },
                    singleLine = false,
                    minLines = 4,
                    error = error,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                KeyButton("Cancel", onClick = onDismiss, enabled = !busy)
                KeyButton("Continue", onClick = { runImport { onImport(text, "") } }, enabled = text.isNotBlank() && !busy, primary = true)
            }
        }
    }
}
