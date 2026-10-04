package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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

@Composable
fun ExportDialog(onPick: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Export chat", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                ExportFormat.entries.forEach { f ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(f) }
                            .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Text(f.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}

/** A copy being written line by line; the bar fills as it goes. Not dismissable mid-way. */
@Composable
fun TransferProgressDialog(title: String, done: Int, total: Int) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    if (total > 0) "$done of $total messages" else "Starting…",
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
