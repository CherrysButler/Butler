package com.cherry.butler.feature.chats

import com.cherry.butler.ui.components.SearchBox
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.cherry.butler.ui.components.ImportConfirmDialog
import com.cherry.butler.ui.components.TransferProgressDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.cherry.butler.ui.components.CountPill
import com.cherry.butler.ui.components.PreviewText
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.MarginFigure
import com.cherry.butler.ui.components.PagingStatus
import com.cherry.butler.ui.components.PersonaPickerSheet
import com.cherry.butler.ui.components.RosterRow
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.StickToTop
import com.cherry.butler.ui.components.isRetryable
import com.cherry.butler.ui.components.rememberFlingListState
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.userTitle

/**
 * The save screen. One line per character you have talked to, newest activity first;
 * a line opens the sheet of every chat with that character. Chats are never listed flat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    contentPadding: PaddingValues,
    onChatClick: (Long) -> Unit,
    onCharacterClick: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val allGroups = viewModel.groups.collectAsLazyPagingItems()
    val searchResults = viewModel.searchResults.collectAsLazyPagingItems()
    val tabGroups = viewModel.tabGroups.collectAsLazyPagingItems()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val searching = search.isNotBlank()
    val groups = when {
        searching -> searchResults
        tab != ChatsTab.All -> tabGroups
        else -> allGroups
    }
    // Each list keeps its own scroll; the full list's survives a search or a folder.
    val allListState = rememberFlingListState()
    val searchListState = rememberFlingListState()
    val tabListState = rememberFlingListState()

    val acting by viewModel.acting.collectAsStateWithLifecycle()
    val picking by viewModel.picking.collectAsStateWithLifecycle()
    val inFolders by viewModel.inFolders.collectAsStateWithLifecycle()
    val managing by viewModel.managing.collectAsStateWithLifecycle()
    val organizeBusy by viewModel.organizeBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    acting?.let { g ->
        if (picking) {
            FolderPickerSheet(
                folders = folders,
                inFolders = inFolders,
                busy = organizeBusy,
                onToggle = viewModel::toggleFolder,
                onCreate = viewModel::createFolder,
                onDismiss = viewModel::stopActing,
            )
        } else {
            GroupActionsSheet(
                group = g,
                archived = tab == ChatsTab.Archived,
                onPin = viewModel::togglePin,
                onArchive = viewModel::toggleArchive,
                onFolders = viewModel::pickFolders,
                onDismiss = viewModel::stopActing,
            )
        }
    }
    managing?.let { f ->
        FolderManageSheet(
            folder = f,
            busy = organizeBusy,
            onRename = viewModel::renameFolder,
            onDelete = viewModel::deleteFolder,
            onDismiss = viewModel::stopManaging,
        )
    }
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val fallbackPersona by viewModel.fallbackPersonaName.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val personaOptions by viewModel.personaOptions.collectAsStateWithLifecycle()
    var pickingPersona by remember { mutableStateOf(false) }
    val status = PagingStatus(groups)

    if (pickingPersona) {
        PersonaPickerSheet(
            options = personaOptions,
            selected = persona,
            onPick = { viewModel.choosePersona(it); pickingPersona = false },
            onDismiss = { pickingPersona = false },
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val openFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null) viewModel.readImport(text)
        }
    }
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    importing?.let { file ->
        ImportConfirmDialog(
            count = file.lines.size,
            from = file.characterName,
            into = sheet?.group?.characterName ?: "this character",
            onConfirm = { viewModel.confirmImport(onChatClick) },
            onDismiss = viewModel::cancelImport,
        )
    }
    importProgress?.let { (done, total) -> TransferProgressDialog("Importing…", done, total) }

    sheet?.let { state ->
        CharacterChatsSheet(
            state = state,
            fallbackPersonaName = fallbackPersona,
            persona = persona,
            onPickPersona = { pickingPersona = true },
            onDismiss = viewModel::close,
            onOpenChat = { id -> viewModel.close(); onChatClick(id) },
            onOpenCharacter = { id -> viewModel.close(); onCharacterClick(id) },
            onNewChat = { viewModel.newChat { id -> viewModel.close(); onChatClick(id) } },
            onImport = { openFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
            onDeleteChat = viewModel::deleteChat,
        )
    }

    val tabActive = com.cherry.butler.ui.navigation.LocalTabActive.current
    androidx.activity.compose.BackHandler(enabled = tabActive && tab == ChatsTab.Archived) { viewModel.selectTab(ChatsTab.All) }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val letGo = com.cherry.butler.ui.components.rememberLetGoOfField(focusManager)
    // A new search starts its results at the top.
    LaunchedEffect(search) { if (search.isNotBlank()) searchListState.scrollToItem(0) }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).nestedScroll(letGo)) {
        ScreenHead(title = "Chats")
        SearchBox(
            value = search,
            onValueChange = viewModel::onSearch,
            placeholder = "Search your chats",
            onSubmit = { focusManager.clearFocus() },
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
        )
        when {
            searching -> {}
            tab == ChatsTab.Archived -> ArchiveHeader(archivedCount, onBack = { viewModel.selectTab(ChatsTab.All) })
            else -> FolderTabs(
                folders = folders,
                selected = tab,
                onSelect = viewModel::selectTab,
                onManage = viewModel::manage,
            )
        }
        notice?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.danger,
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissNotice).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = status.refreshing && status.itemCount > 0,
            onRefresh = groups::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val refreshError = status.refreshError
            when {
                status.initialLoading -> SkeletonList()
                status.itemCount == 0 && refreshError != null -> CenteredMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = refreshError.userTitle(),
                    body = refreshError.userMessage(),
                ) {
                    if (refreshError.isRetryable()) {
                        Spacer(Modifier.height(16.dp))
                        KeyButton(label = "Try again", onClick = groups::retry)
                    }
                }
                status.empty && search.isNotBlank() -> CenteredMessage(
                    icon = Icons.Rounded.SearchOff,
                    title = "No chats with \"${search.trim()}\"",
                    body = "No character you've talked to has that in their name.",
                )
                status.empty && tab is ChatsTab.Folder -> CenteredMessage(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "Nothing in this folder",
                    body = "Long-press a chat in All to add it here.",
                )
                status.empty && tab == ChatsTab.Archived -> CenteredMessage(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "Nothing archived",
                    body = "Long-press a chat to archive it.",
                )
                status.empty -> CenteredMessage(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "No chats yet",
                    body = "Pick a character in Browse and your conversations will live here, readable even offline.",
                ) {
                    Spacer(Modifier.height(16.dp))
                    KeyButton(label = "Browse characters", onClick = onBrowse)
                }
                else -> SlotList(
                    groups = groups,
                    status = status,
                    listState = when {
                        searching -> searchListState
                        tab != ChatsTab.All -> tabListState
                        else -> allListState
                    },
                    fallbackPersonaName = fallbackPersona,
                    onOpen = viewModel::open,
                    onLongPress = viewModel::actOn,
                    archivedCount = archivedCount,
                    showArchive = !searching && tab == ChatsTab.All,
                    onOpenArchive = { viewModel.selectTab(ChatsTab.Archived) },
                    onRefresh = groups::refresh,
                )
            }
        }
    }
}

/** A screen's head: its name, set bold and tight, as a plate. */
@Composable
fun ScreenHead(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * A key: a rounded block, never a pill. Primary (start, send) is red; everything else
 * sits on the raised surface.
 */
@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = ButlerTheme.colors.surfaceHigh,
            disabledContentColor = ButlerTheme.colors.textLow,
        ),
    ) { Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SlotList(
    groups: LazyPagingItems<ChatGroup>,
    status: PagingStatus,
    listState: androidx.compose.foundation.lazy.LazyListState,
    fallbackPersonaName: String?,
    onOpen: (ChatGroup) -> Unit,
    onLongPress: (ChatGroup) -> Unit,
    archivedCount: Int = 0,
    onOpenArchive: () -> Unit = {},
    showArchive: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    StickToTop(listState, firstKey = if (groups.itemCount > 0) groups.peek(0)?.characterId else null)
    // One pull, two stops: a little brings the archive, further refreshes.
    val pull = rememberChatsPull(archive = showArchive, onRefresh = onRefresh)
    Box(modifier = Modifier.fillMaxSize().nestedScroll(pull.connection)) {
    Column(modifier = Modifier.fillMaxSize()) {
    androidx.compose.animation.AnimatedVisibility(
        visible = pull.archiveOpen,
        enter = androidx.compose.animation.expandVertically(com.cherry.butler.core.design.Motion.enter()) + androidx.compose.animation.fadeIn(com.cherry.butler.core.design.Motion.enter()),
        exit = androidx.compose.animation.shrinkVertically(com.cherry.butler.core.design.Motion.exit()) + androidx.compose.animation.fadeOut(com.cherry.butler.core.design.Motion.exit()),
    ) {
        ArchivedRow(archivedCount, onOpenArchive)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(
            count = groups.itemCount,
            key = { index -> groups.peek(index)?.characterId ?: index },
            contentType = { "group" },
        ) { index ->
            groups[index]?.let { group ->
                SlotLine(group = group, fallbackPersonaName = fallbackPersonaName, onClick = { onOpen(group) }, onLongClick = { onLongPress(group) })
            }
        }
        if (status.appending) {
            item(contentType = "skeleton") { SkeletonRosterRow(withMargin = false) }
        }
        status.appendError?.let { error ->
            item(contentType = "error") {
                InlineErrorCard(error, onRetry = groups::retry, modifier = Modifier.padding(16.dp))
            }
        }
    }
    }
    PullBadge(pull)
    }
}

@Composable
private fun SlotLine(group: ChatGroup, fallbackPersonaName: String?, onClick: () -> Unit, onLongClick: () -> Unit) {
    RosterRow(
        name = group.characterName.ifBlank { "Character" },
        avatarUrl = group.avatarUrl,
        onClick = onClick,
        onLongClick = onLongClick,
        pinned = group.pinned,
        trailing = group.lastMessageAt?.let { at -> { MarginFigure(RelativeTime.short(at)) } },
    ) {
        val preview = group.lastMessagePreview?.takeIf { it.isNotBlank() }
        if (preview != null) {
            PreviewText(
                text = remember(preview, group.personaName, fallbackPersonaName) {
                    preview.fillNames(user = group.personaName ?: fallbackPersonaName, char = group.characterName, markUser = true)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CountPill(text = "${group.chatCount} " + if (group.chatCount == 1) "chat" else "chats")
            if (group.characterDeleted) {
                Spacer(Modifier.width(8.dp))
                Text("Unavailable", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
        }
    }
}

@Composable
private fun SkeletonList() {
    Column(modifier = Modifier.fillMaxSize()) {
        repeat(7) { SkeletonRosterRow(withMargin = false) }
    }
}

/**
 * Telegram's pull, two stops on one gesture. At the top of All, pulling down brings a small
 * badge with the archive in it; let go there and the Archived row opens above the list.
 * Pull on past the second stop and the badge turns to refresh; let go and the list refreshes.
 * The finger travels against resistance (under half its distance). Scrolling the list down
 * folds the archive away again. Outside All (a folder, the archive) the pull only refreshes.
 */
private class ChatsPullState(
    private val archiveAtPx: Float,
    private val refreshAtPx: Float,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var archive by mutableStateOf(true)
    var onRefresh: () -> Unit = {}
    var pull by mutableFloatStateOf(0f)
        private set
    var archiveOpen by mutableStateOf(false)
    private var settling: kotlinx.coroutines.Job? = null

    /** 0 → 1 toward the first stop, then 1 → 2 toward the second. */
    val stage: Float
        get() = if (pull <= archiveAtPx) pull / archiveAtPx else 1f + ((pull - archiveAtPx) / (refreshAtPx - archiveAtPx)).coerceAtMost(1f)
    val showsRefresh: Boolean get() = !archive || pull >= refreshAtPx

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f) {
                // The list is being scrolled down: fold the archive away.
                if (archiveOpen && source == NestedScrollSource.UserInput) archiveOpen = false
                // Finger going back up mid-pull: the badge retreats before the list moves.
                if (pull > 0f) {
                    settling?.cancel()
                    val use = maxOf(available.y, -pull)
                    pull += use
                    return Offset(0f, use)
                }
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            settling?.cancel()
            pull = (pull + available.y * RESISTANCE).coerceAtMost(refreshAtPx * 1.25f)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            release()
            return Velocity.Zero
        }
    }

    private fun release() {
        if (pull <= 0f) return
        when {
            showsRefresh && pull >= (if (archive) refreshAtPx else archiveAtPx) -> onRefresh()
            archive && pull >= archiveAtPx -> archiveOpen = true
        }
        settling?.cancel()
        settling = scope.launch {
            androidx.compose.animation.core.animate(pull, 0f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 600f)) { v, _ -> pull = v }
        }
    }

    companion object {
        const val RESISTANCE = 0.55f
    }
}

@Composable
private fun rememberChatsPull(archive: Boolean, onRefresh: () -> Unit): ChatsPullState {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember { ChatsPullState(with(density) { 40.dp.toPx() }, with(density) { 96.dp.toPx() }, scope) }
    state.archive = archive
    state.onRefresh = onRefresh
    LaunchedEffect(archive) { if (!archive) state.archiveOpen = false }
    return state
}

/** The badge that comes down with the pull: the archive first, then refresh. */
@Composable
private fun BoxScope.PullBadge(state: ChatsPullState) {
    if (state.pull <= 0f) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stage = state.stage
    val refresh = state.showsRefresh
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { androidx.compose.ui.unit.IntOffset(0, (state.pull - with(density) { 40.dp.toPx() }).toInt()) }
            .size(40.dp)
            .graphicsLayer {
                val grow = stage.coerceAtMost(1f)
                alpha = grow
                scaleX = 0.6f + 0.4f * grow
                scaleY = 0.6f + 0.4f * grow
            }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (refresh) Icons.Rounded.Refresh else Icons.Outlined.Archive,
            contentDescription = null,
            tint = if (stage >= 1f) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = if (refresh) (stage - 1f) * 270f else 0f },
        )
    }
}
