package com.cherry.butler.feature.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.ChatFolder
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.feature.settings.FieldBlock
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim

/** Which slice of the chats list is showing: everything, one folder, or the archive. */
sealed interface ChatsTab {
    data object All : ChatsTab
    data class Folder(val id: String) : ChatsTab
    data object Archived : ChatsTab
}

/**
 * Telegram's folder strip: "All" and the user's folders. The current tab is lit red with a
 * short underline. Hidden until there is a folder, so the plain list looks as it always did.
 * (The archive is not a tab: as in Telegram it's a row at the top of All, see [ArchivedRow].)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTabs(
    folders: List<ChatFolder>,
    selected: ChatsTab,
    onSelect: (ChatsTab) -> Unit,
    onManage: (ChatFolder) -> Unit,
) {
    if (folders.isEmpty()) return
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab("All", selected == ChatsTab.All, onClick = { onSelect(ChatsTab.All) })
            folders.forEach { folder ->
                Tab(
                    label = folder.name.ifBlank { "Folder" },
                    on = selected == ChatsTab.Folder(folder.id),
                    onClick = { onSelect(ChatsTab.Folder(folder.id)) },
                    onLongClick = { onManage(folder) },
                )
            }
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tab(label: String, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val ink by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed, label = "tab")
    val bar by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, label = "tab-bar")
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp)
            .heightIn(min = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp, bottom = 9.dp),
        )
        Box(Modifier.width(24.dp).height(3.dp).clip(Pill).background(bar))
    }
}

/** What a long-press on a character's row offers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupActionsSheet(
    group: ChatGroup,
    archived: Boolean,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onFolders: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        SheetTitle(group.characterName.ifBlank { "Character" })
        if (!archived) ActionRow(Icons.Outlined.PushPin, if (group.pinned) "Unpin" else "Pin to the top", onPin)
        ActionRow(Icons.Outlined.Folder, "Add to folder…", onFolders)
        ActionRow(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (archived) "Unarchive" else "Archive", onArchive)
    }
}

/** Folders with a tick where this character's chats are; tap to add or take out, or make a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    folders: List<ChatFolder>,
    inFolders: Set<String>,
    busy: Boolean,
    onToggle: (ChatFolder) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    Sheet(onDismiss) {
        SheetTitle("Add to folder")
        folders.forEach { folder ->
            val on = folder.id in inFolders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { onToggle(folder) }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(folder.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Rounded.Check, contentDescription = "In this folder", tint = MaterialTheme.colorScheme.primary)
            }
        }
        FieldBlock("New folder", newName, { newName = it.take(40) }, placeholder = "Name")
        if (newName.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                KeyButton(label = if (busy) "Making…" else "Make and add", onClick = { onCreate(newName.trim()); newName = "" }, enabled = !busy, primary = true)
            }
        }
    }
}

/** A long-pressed folder tab: rename it, or delete it (its chats stay). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManageSheet(
    folder: ChatFolder,
    busy: Boolean,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var confirm by remember { mutableStateOf(false) }
    Sheet(onDismiss) {
        SheetTitle(folder.name)
        FieldBlock("Name", name, { name = it.take(40) })
        if (name.isNotBlank() && name.trim() != folder.name) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                KeyButton(label = "Rename", onClick = { onRename(name.trim()) }, enabled = !busy, primary = true)
            }
        }
        if (!confirm) {
            ActionRow(Icons.Outlined.DeleteOutline, "Delete folder", { confirm = true }, danger = true)
        } else {
            Text(
                "The folder goes, on Janitor too. Its chats stay where they are.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            ActionRow(Icons.Outlined.DeleteOutline, "Delete \"${folder.name}\"", onDelete, danger = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content() }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) ButlerTheme.colors.danger else ButlerTheme.colors.textMed, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (danger) ButlerTheme.colors.danger else MaterialTheme.colorScheme.onSurface)
    }
}

/** Telegram's "Archived Chats": one quiet row at the top of All that opens the archive. */
@Composable
fun ArchivedRow(count: Int, onOpen: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(ButlerTheme.colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Archive, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("Archived", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(if (count > 0) count.toString() else "Empty", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = ButlerTheme.colors.textLow)
            Spacer(Modifier.width(6.dp))
            Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(20.dp))
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Over the archive: back to All, and what this is. */
@Composable
fun ArchiveHeader(count: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to all chats", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text("Archived", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            if (count == 1) "1 character" else "$count characters",
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.textLow,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}
