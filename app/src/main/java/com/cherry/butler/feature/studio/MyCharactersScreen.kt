package com.cherry.butler.feature.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.remote.CommentMode
import com.cherry.butler.core.data.remote.StudioRemoteSource.Privacy
import com.cherry.butler.core.data.remote.StudioRemoteSource.Sort
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.ui.components.CharacterTile
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SearchBox
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.SkeletonTile
import com.cherry.butler.ui.components.TileGrid
import com.cherry.butler.ui.components.card

/**
 * The characters you made, private ones too: All, Public or Private, newest or most chatted,
 * and a search. Each card says whether it's public; its menu edits it, publishes or hides it,
 * opens its settings, or deletes it. New starts one from blank.
 */
@Composable
fun MyCharactersScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    /** A character's id to edit, or "new". */
    onEdit: (String) -> Unit,
    /** Bumped by the caller when the editor saved, so the list asks again. */
    refreshKey: Int = 0,
    viewModel: MyCharactersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<MyCharacter?>(null) }
    var droppingDraft by remember { mutableStateOf<com.cherry.butler.core.data.StoredDraft?>(null) }
    droppingDraft?.let { kept ->
        DeleteConfirmDialog(
            count = 1,
            title = "Delete this draft",
            body = if (kept.isNew) "\u201C${kept.draft.name.ifBlank { "Untitled" }}\u201D was never saved to Janitor, so it's gone for good."
            else "The unsaved changes go; \u201C${kept.draft.name.ifBlank { "the character" }}\u201D on Janitor stays as it is.",
            onConfirm = { droppingDraft = null; viewModel.deleteDraft(kept.id) },
            onDismiss = { droppingDraft = null },
        )
    }
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.reload() }

    deleting?.let { item ->
        DeleteConfirmDialog(
            count = 1,
            title = "Delete “${item.character.name}”",
            body = "It's removed from Janitor for good, with its page. Chats people had with it go too. This can't be undone.",
            onConfirm = { deleting = null; viewModel.delete(item) },
            onDismiss = { deleting = null },
        )
    }
    settings?.let { s ->
        SettingsSheet(
            settings = s,
            onSwitch = viewModel::setSwitch,
            onComments = viewModel::setComments,
            onDismiss = viewModel::closeSettings,
        )
    }

    val gridState = rememberLazyGridState()
    val tileWidth = TileGrid.tileWidth()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.more() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                state.total?.let { "Your characters · $it" } ?: "Your characters",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            KeyButton("New character", onClick = { onEdit("new") }, primary = true)
        }
        SearchBox(
            value = state.search,
            onValueChange = viewModel::setSearch,
            placeholder = "Search your characters",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        SegmentedChoice(
            options = listOf("All", "Public", "Private"),
            selected = state.privacy.ordinal,
            onSelect = { viewModel.setPrivacy(Privacy.entries[it]) },
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sort", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Sort.entries.forEach { sort ->
                val on = sort == state.sort
                Text(
                    sort.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(Pill)
                        .clickable { viewModel.setSort(sort) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        state.notice?.let { note ->
            LaunchedEffect(note) { kotlinx.coroutines.delay(3_500); viewModel.dismissNotice() }
            Text(note, style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(TileGrid.columns()),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = TileGrid.gutter, end = TileGrid.gutter, top = 8.dp, bottom = TileGrid.gutter),
            horizontalArrangement = Arrangement.spacedBy(TileGrid.gap),
            verticalArrangement = Arrangement.spacedBy(TileGrid.gap),
        ) {
            if (drafts.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "drafts", contentType = "drafts") {
                    DraftsShelf(
                        drafts = drafts,
                        onOpen = { onEdit(CharacterEditorViewModel.DRAFT_PREFIX + it.id) },
                        onDelete = { droppingDraft = it },
                    )
                }
            }
            items(state.items.size, key = { state.items[it].character.id }, contentType = { "mine" }) { i ->
                val item = state.items[i]
                val c = item.character
                CharacterTile(
                    name = c.name,
                    avatarUrl = c.avatarUrl,
                    width = tileWidth,
                    onClick = { onOpenCharacter(c.id) },
                    obscured = c.isImageNsfw,
                    nsfw = c.isNsfw,
                    chatCount = c.chatCount.compactCount(),
                ) {
                    OwnerFooter(
                        item = item,
                        busy = c.id in state.busy,
                        onEdit = { onEdit(c.id) },
                        onTogglePublic = { viewModel.togglePublic(item) },
                        onSettings = { viewModel.openSettings(item) },
                        onDelete = { deleting = item },
                    )
                }
            }
            if (state.loading) items(2, contentType = { "skeleton" }) { SkeletonTile(tileWidth) }
            state.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { InlineErrorCard(e, onRetry = viewModel::more) } }
            if (state.end && state.items.isEmpty() && state.error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state.search.isNotBlank() || state.privacy != Privacy.All) "Nothing matches." else "You haven't made a character yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ButlerTheme.colors.textLow,
                        )
                    }
                }
            }
        }
    }
}

/** Under a card: public or private, the token count, and the creator's menu. */
@Composable
private fun OwnerFooter(
    item: MyCharacter,
    busy: Boolean,
    onEdit: () -> Unit,
    onTogglePublic: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (item.isPublic) Icons.Rounded.Public else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (item.isPublic) ButlerTheme.colors.success else ButlerTheme.colors.textLow,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            buildString {
                append(if (item.isPublic) "Public" else "Private")
                if (item.character.totalTokens > 0) append(" · ${item.character.totalTokens.toLong().compactCount()} tokens")
            },
            style = MaterialTheme.typography.labelSmall,
            color = ButlerTheme.colors.textMed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }, enabled = !busy) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Manage", tint = ButlerTheme.colors.textMed)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                MenuRow("Edit", Icons.Rounded.Edit) { menu = false; onEdit() }
                MenuRow(if (item.isPublic) "Make private" else "Make public", if (item.isPublic) Icons.Rounded.Lock else Icons.Rounded.Public) { menu = false; onTogglePublic() }
                MenuRow("Settings", Icons.Rounded.Tune) { menu = false; onSettings() }
                MenuRow("Delete", Icons.Rounded.Delete, danger = true) { menu = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) ButlerTheme.colors.danger else ButlerTheme.colors.textMed
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.titleSmall, color = if (danger) ButlerTheme.colors.danger else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}

/** A character's switches and who may comment, each sent the moment it changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    settings: CharacterSettings,
    onSwitch: (String, Boolean) -> Unit,
    onComments: (CommentMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                settings.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (!settings.loaded) {
                Text(settings.error ?: "Loading…", style = MaterialTheme.typography.bodyMedium, color = if (settings.error != null) ButlerTheme.colors.danger else ButlerTheme.colors.textLow, modifier = Modifier.padding(20.dp))
                return@Column
            }
            SwitchRow("Proxies allowed", "People can chat with it on their own proxy, not just JLLM", settings.allowProxy, { onSwitch(MyCharactersViewModel.SWITCH_PROXY, it) })
            SwitchRow("Definition shown", "Its personality, scenario and examples are on its page for everyone", settings.showDefinition, { onSwitch(MyCharactersViewModel.SWITCH_DEFINITION, it) })
            SwitchRow("Published chats allowed", "People can publish their chats with it", settings.allowPublishedChats, { onSwitch(MyCharactersViewModel.SWITCH_PUBLISHED_CHATS, it) })
            Text(
                "Comments",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = CommentMode.entries.map { it.label },
                selected = settings.comments.ordinal,
                onSelect = { onComments(CommentMode.entries[it]) },
            )
            settings.error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
    }
}

/**
 * What's being written on this device: new characters not on Janitor yet, and changes to
 * yours not saved. A tap picks one up where it was left.
 */
@Composable
private fun DraftsShelf(
    drafts: List<com.cherry.butler.core.data.StoredDraft>,
    onOpen: (com.cherry.butler.core.data.StoredDraft) -> Unit,
    onDelete: (com.cherry.butler.core.data.StoredDraft) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            "Drafts on this device",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ButlerTheme.colors.textMed,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().card()) {
            drafts.forEachIndexed { i, kept ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(kept) }
                        .heightIn(min = 64.dp)
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.cherry.butler.ui.components.Avatar(url = kept.pictureUrl, name = kept.draft.name.ifBlank { "?" }, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            kept.draft.name.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (if (kept.isNew) "New, not on Janitor yet" else "Unsaved changes") +
                                " \u00B7 " + com.cherry.butler.core.util.RelativeTime.short(kept.updatedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = ButlerTheme.colors.textLow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onDelete(kept) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete draft", tint = ButlerTheme.colors.textLow)
                    }
                }
                if (i < drafts.lastIndex) com.cherry.butler.feature.settings.RowDivider()
            }
        }
    }
}
