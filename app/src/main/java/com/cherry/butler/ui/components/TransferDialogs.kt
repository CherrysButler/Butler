package com.cherry.butler.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill

/** Where an export goes: Butler's own file, or SillyTavern's. */
enum class ExportFormat(val label: String, val detail: String, val mime: String, val extension: String) {
    Butler("Butler", "Keeps the character, so it imports back in one tap", "application/json", "json"),
    SillyTavern("SillyTavern", "A .jsonl that SillyTavern opens as a chat", "application/octet-stream", "jsonl"),
}

/** Where a persona export goes: Butler's own file, the one that imports back, or text to read. */
enum class PersonaExportFormat(val label: String, val detail: String, val mime: String, val extension: String) {
    Butler("Butler", "JSON that imports back into Butler", "application/json", "json"),
    Text("Plain text", "To read, or paste anywhere", "text/plain", "txt"),
    Markdown("Markdown", "Headings and lists, for notes", "text/markdown", "md"),
}

@Composable
fun ExportDialog(onPick: (ExportFormat) -> Unit, onDismiss: () -> Unit) =
    FormatDialog("Export chat", ExportFormat.entries, { it.label to it.detail }, onPick, onDismiss)

@Composable
fun PersonaExportDialog(title: String, onPick: (PersonaExportFormat) -> Unit, onDismiss: () -> Unit) =
    FormatDialog(title, PersonaExportFormat.entries, { it.label to it.detail }, onPick, onDismiss)

/** A list of formats to export in, each with a line on what it's for. */
@Composable
private fun <T> FormatDialog(title: String, formats: List<T>, describe: (T) -> Pair<String, String>, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                formats.forEach { f ->
                    val (label, detail) = describe(f)
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(f) }
                            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}

/**
 * Saves text to a file the user picks: the system's save picker on the phone, Windows' own on
 * the desktop. Remembered at the screen's level, so the picker's answer still lands after
 * the dialog that asked for it has gone. [onError] gets a line to show when the write fails.
 */
@Composable
fun rememberFileSaver(onError: (String) -> Unit): (name: String, text: String) -> Unit {
    var pending by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val text = pending
        pending = null
        if (uri != null && text != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Couldn't open the file.")
                    }
                }.onFailure { onError("Couldn't save the file. ${it.message.orEmpty()}") }
            }
        }
    }
    return { name, text -> pending = text; save.launch(name) }
}

/** A copy being written line by line; the bar fills as it goes. Not dismissable mid-way. */
@Composable
fun TransferProgressDialog(title: String, done: Int, total: Int, unit: String = "messages") {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    if (total > 0) "$done of $total $unit" else "Starting…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = ButlerTheme.colors.textMed,
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(Pill).background(ButlerTheme.colors.surfaceHigh)) {
                    Box(
                        Modifier.fillMaxWidth(if (total > 0) done / total.toFloat() else 0f).height(4.dp).clip(Pill)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
        confirmButton = {},
    )
}

/** Before an import: what's in the file and where it goes. */
@Composable
fun ImportConfirmDialog(count: Int, from: String, into: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Import as a new chat?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                "$count messages${if (from.isNotBlank()) " from \"$from\"" else ""} go into a new chat with $into, on Janitor too. " +
                    "Each one is posted in order; long chats take a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = Pill) { Text("Import", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}

/**
 * Before a persona import: each persona as it will be made, its picture, name, pronouns,
 * group and the start of its description (a tap shows all of it), and that each is made new
 * beside the ones there.
 */
@Composable
fun PersonaImportConfirmDialog(personas: List<com.cherry.butler.core.data.TransferPersona>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Only its own keys answer it: a stray click as a file dialog closed used to dismiss it unseen.
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                if (personas.size == 1) "Import “${personas.single().name}”?" else "Import ${personas.size} personas?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(personas.size) { i -> ImportedPersona(personas[i]) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    (if (personas.size == 1) "It is made as a new persona" else "Each one is made as a new persona") +
                        " on Janitor, beside the ones you have. Nothing you have is changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.textLow,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = Pill) { Text("Import", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}

/** One persona in the import's preview; its description opens in full on a tap. */
@Composable
private fun ImportedPersona(p: com.cherry.butler.core.data.TransferPersona) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .clickable(enabled = p.appearance.isNotBlank()) { open = !open }
            .padding(12.dp),
    ) {
        Avatar(url = p.avatarUrl, name = p.name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            val pronouns = p.pronouns?.let { listOfNotNull(it.subjective, it.objective).filter { w -> w.isNotBlank() }.joinToString("/") }.orEmpty()
            val facts = listOfNotNull(pronouns.takeIf { it.isNotEmpty() }, p.group?.name?.let { "in $it" }, "was your default".takeIf { p.isDefault })
            if (facts.isNotEmpty()) {
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
            if (p.appearance.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (open) p.appearance.trim() else plainExcerpt(p.appearance),
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.textMed,
                    maxLines = if (open) Int.MAX_VALUE else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The description's start as plain words: headings, stars and blank lines out. */
private fun plainExcerpt(text: String): String =
    text.lineSequence()
        .map { it.trim().trimStart('#', '>', '-', ' ').replace("**", "").replace("*", "").replace("__", "") }
        .filter { it.isNotBlank() }
        .joinToString(" ")
