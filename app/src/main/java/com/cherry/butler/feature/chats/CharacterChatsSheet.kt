package com.cherry.butler.feature.chats

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import com.cherry.butler.ui.components.CountPill
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.ChatPreviewRow
import com.cherry.butler.ui.components.PersonaSwitch
import com.cherry.butler.ui.components.SkeletonBlock
import com.cherry.butler.ui.components.userMessage

/**
 * The load screen for one character: every chat you have with them, numbered, over the
 * save list. Paints from the mirror the instant it opens and settles when the server
 * answers. Starting a new chat lives here too, where the thumb is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterChatsSheet(
    state: CharacterChatsSheetState,
    fallbackPersonaName: String?,
    persona: PersonaOption?,
    onPickPersona: () -> Unit,
    onDismiss: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onNewChat: () -> Unit,
    onImport: () -> Unit = {},
    onDeleteChat: (Long) -> Unit = {},
) {
    var deleting by remember { mutableStateOf<Long?>(null) }
    deleting?.let { id ->
        com.cherry.butler.feature.chat.DeleteConfirmDialog(
            count = 1,
            title = "Delete chat",
            body = "This chat and all its messages will be deleted from Janitor. This can't be undone.",
            onConfirm = { deleting = null; onDeleteChat(id) },
            onDismiss = { deleting = null },
        )
    }
    val sheetState = rememberModalBottomSheetState()
    // Janitor's Oldest / Latest switch; latest first, as the list arrives.
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = state.group.avatarUrl, name = state.group.characterName, size = 60.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.group.characterName.ifBlank { "Character" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    CountPill(
                        text = when {
                            state.group.characterDeleted -> "No longer available"
                            state.chats.size == 1 -> "1 chat"
                            else -> "${state.chats.size} chats"
                        },
                    )
                }
                if (!state.group.characterDeleted) {
                    IconButton(onClick = { onOpenCharacter(state.group.characterId) }) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Open character",
                            tint = ButlerTheme.colors.textMed,
                        )
                    }
                }
            }

            if (!state.group.characterDeleted) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonaSwitch(
                        current = persona,
                        onClick = onPickPersona,
                        modifier = Modifier.widthIn(max = 190.dp).height(48.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    KeyButton(
                        label = if (state.creating) "Starting…" else "New chat",
                        primary = true,
                        onClick = onNewChat,
                        enabled = !state.creating,
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // A chat from a file: Butler's own or SillyTavern's.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(ButlerTheme.colors.surfaceHigh)
                            .clickable(enabled = !state.creating, onClickLabel = "Import a chat file", onClick = onImport),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = "Import a chat file", tint = ButlerTheme.colors.textMed)
                    }
                }
                state.createError?.let { error ->
                    Text(
                        text = error.userMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            when {
                state.chats.isEmpty() && state.refreshing -> Column(modifier = Modifier.padding(16.dp)) {
                    repeat(3) {
                        SkeletonBlock(width = 260.dp, height = 12.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(width = 120.dp, height = 10.dp)
                        Spacer(Modifier.height(18.dp))
                    }
                }
                state.chats.isEmpty() -> Text(
                    text = state.refreshError?.userMessage() ?: "No chats with this character yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
                else -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    item(key = "order", contentType = "order") {
                        // Janitor's Oldest / Latest, as one small switch at the list's head.
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 2.dp), horizontalArrangement = Arrangement.End) {
                            Row(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { oldestFirst = !oldestFirst }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (oldestFirst) "Oldest first" else "Latest first",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ButlerTheme.colors.textMed,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Numbered in the order they were started: the first chat is Chat 1, whichever way the list runs.
                    val shown = if (oldestFirst) state.chats.asReversed() else state.chats
                    itemsIndexed(shown, key = { _, chat -> chat.id }, contentType = { _, _ -> "chat" }) { i, chat ->
                        ChatPreviewRow(
                            number = if (oldestFirst) i + 1 else shown.size - i,
                            preview = chat.lastMessagePreview?.fillNames(
                                user = chat.personaName ?: fallbackPersonaName,
                                char = state.group.characterName,
                                markUser = true,
                            ),
                            messageCount = chat.messageCount,
                            updatedAt = chat.lastMessageAt,
                            onClick = { onOpenChat(chat.id) },
                            onLongClick = { deleting = chat.id },
                        )
                    }
                    state.refreshError?.let { error ->
                        item(contentType = "error") {
                            Text(
                                text = "Showing what's saved on this phone. ${error.userMessage()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ButlerTheme.colors.textLow,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
