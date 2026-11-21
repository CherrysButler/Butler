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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onEditImport: () -> Unit,
    viewModel: LorebooksViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle(initialValue = emptyList())
    val limit by viewModel.contextCharLimit.collectAsStateWithLifecycle(initialValue = LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT)
    var importing by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            PlateText("Local Lorebooks", PlateLevel.Name, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                NumberRow(
                    title = "Lore character limit",
                    subtitle = "0 = unlimited",
                    value = limit,
                    onCommit = viewModel::setContextCharLimit,
                    range = 0..Int.MAX_VALUE,
                )
            }
            item {
                Text(
                    "All your lorebooks on this device. Tap a book to edit it. Enable books in a chat or attach them to a persona.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (books.isEmpty()) item {
                Text(
                    "No lorebooks yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }
            items(books, key = { it.id }) { book ->
                ListItem(
                    modifier = Modifier.clickable { onEdit(book.id) },
                    headlineContent = { Text(book.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${book.entryCount} entries") },
                    leadingContent = {
                        IconButton(onClick = {
                            viewModel.toggleFavorite(book.id)
                        }) {
                            Icon(
                                if (book.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (book.favorite) "Remove ${book.name} from favorites" else "Favorite ${book.name}",
                                tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyButton("Add Lorebook", onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth())
                    KeyButton("Import JSON", onClick = { importing = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (importing) {
        ImportLorebookDialog(
            onImport = viewModel::prepareImport,
            onPrepared = {
                importing = false
                onEditImport()
            },
            onDismiss = { importing = false },
        )
    }
}

@Composable
private fun ImportLorebookDialog(
    onImport: suspend (String, String) -> Unit,
    onPrepared: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runImport(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        coroutineScope.launch {
            try {
                action()
                onPrepared()
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
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import JSON") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyButton(
                    "Choose JSON file",
                    onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Or paste lorebook JSON") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                    enabled = !busy,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && !busy, onClick = { runImport { onImport(text, "") } }) {
                Text(if (busy) "Reading…" else "Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
