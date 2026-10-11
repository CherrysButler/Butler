package com.cherry.butler.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.FontPrefs
import com.cherry.butler.core.data.LookProfile
import com.cherry.butler.core.data.LookProfileFormat
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Look profiles: the looks saved under a name, the one being worn marked, each put on with
 * a tap. Each can be renamed, shared as a file or deleted; a shared file comes back in as a
 * new profile beside the others, and is only put on when tapped.
 */
@Composable
internal fun ProfilesSection(viewModel: SettingsViewModel) {
    val profiles by viewModel.lookProfiles.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val chatStyle by viewModel.chatStyle.collectAsStateWithLifecycle()
    val thinkingLook by viewModel.thinkingLook.collectAsStateWithLifecycle()
    val thoughtLook by viewModel.thoughtLook.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    val text by viewModel.textLookNow.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val chatFont by viewModel.chatFont.collectAsStateWithLifecycle()
    val now = LookProfile(
        id = "", name = "", theme = theme, chatStyle = chatStyle, thinkingLook = thinkingLook, thoughtLook = thoughtLook,
        custom = custom, text = text, appFont = appFont, chatFont = chatFont,
    )

    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LookProfile?>(null) }
    var deleting by remember { mutableStateOf<LookProfile?>(null) }

    // Export: the system's own save screen, then the file is written; import: its open screen.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var exportText by remember { mutableStateOf<String?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val body = exportText
        exportText = null
        if (uri != null && body != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } ?: error("Couldn't open the file.")
                    }
                }.onFailure { viewModel.reportLookTransfer("Couldn’t save the file. ${it.message.orEmpty()}") }
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                }
                viewModel.importLookProfile(read)
            }
        }
    }

    SettingsSection(
        title = "Profiles",
        icon = Icons.Rounded.Bookmarks,
        footnote = "A profile keeps the theme and its colours, the chat layout, the thinking line, how chat text looks, and the two fonts.",
    ) {
        if (profiles.isEmpty()) {
            Text(
                "No profiles yet. Save the look you have on to put it back with one tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )
            RowDivider()
        }
        profiles.forEach { p ->
            ChoiceRow(
                title = p.name,
                subtitle = summary(p),
                selected = p.sameLookAs(now),
                onClick = { viewModel.applyLookProfile(p.id) },
                trailing = {
                    ProfileMenu(
                        name = p.name,
                        onRename = { renaming = p },
                        onExport = {
                            viewModel.exportLookProfile(p.id)?.let { (file, body) ->
                                exportText = body
                                saveFile.launch(file)
                            }
                        },
                        onDelete = { deleting = p },
                    )
                },
            )
        }
    }
    SettingsSection(title = "Add", footnote = "An imported profile is added here; it goes on only when you tap it.") {
        LinkRow(title = "Save the look you have on", subtitle = null, onClick = { naming = true }, icon = Icons.Rounded.Add)
        LinkRow(
            title = "Import a profile",
            subtitle = "A .json file shared from Butler",
            onClick = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            icon = Icons.Rounded.FileOpen,
        )
    }

    if (naming) {
        ProfileNameDialog(
            title = "Save this look",
            initial = "",
            confirmLabel = "Save",
            onConfirm = { name -> naming = false; viewModel.saveLookProfile(name) },
            onDismiss = { naming = false },
        )
    }
    renaming?.let { p ->
        ProfileNameDialog(
            title = "Rename",
            initial = p.name,
            confirmLabel = "Rename",
            onConfirm = { name -> renaming = null; viewModel.renameLookProfile(p.id, name) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { p ->
        DeleteConfirmDialog(
            count = 1,
            title = "Delete ${p.name}?",
            body = "The profile goes; the look you have on stays as it is.",
            confirmLabel = "Delete",
            onConfirm = { deleting = null; viewModel.deleteLookProfile(p.id) },
            onDismiss = { deleting = null },
        )
    }
}

/** A profile in a line: its theme, its layout and its chat font. */
private fun summary(p: LookProfile): String =
    listOfNotNull(p.theme.label, p.chatStyle.label, p.chatFont?.let(FontPrefs::nameOf)).joinToString(" · ")

/** A profile's quiet tools behind its menu key. */
@Composable
private fun ProfileMenu(name: String, onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More for $name", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ProfileMenuItem(Icons.Outlined.Edit, "Rename") { open = false; onRename() }
            ProfileMenuItem(Icons.Outlined.FileUpload, "Export") { open = false; onExport() }
            DropdownMenuItem(
                text = { Text("Delete", style = MaterialTheme.typography.titleSmall, color = ButlerTheme.colors.danger) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ButlerTheme.colors.danger, modifier = Modifier.size(20.dp)) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.titleSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

/** A profile's name, typed in; an empty one can't be saved. */
@Composable
private fun ProfileNameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val ok = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            FieldBlock(
                label = "Name",
                value = name,
                onChange = { name = it.take(LookProfileFormat.NAME_MAX) },
                placeholder = "Night reading",
                onSubmit = { if (ok) onConfirm(name) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = ok, shape = Pill) {
                Text(confirmLabel, color = if (ok) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}
