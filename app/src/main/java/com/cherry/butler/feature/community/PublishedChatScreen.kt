package com.cherry.butler.feature.community

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.cherry.butler.core.data.ChatComment
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.data.Emoji
import com.cherry.butler.core.data.PublishedSocial
import com.cherry.butler.core.data.PublishedTranscript
import com.cherry.butler.core.data.ReactionTally
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.SkeletonBlock
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The comments sheet's state: one page at a time, a draft, and what is in flight. */
data class PublishedCommentsState(
    val open: Boolean = false,
    val comments: List<ChatComment> = emptyList(),
    val nextPage: Int = 1,
    val end: Boolean = false,
    val loading: Boolean = false,
    val error: ApiError? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
    /** The signed-in user's id, so their own comments offer Delete. */
    val me: String? = null,
    val deleting: String? = null,
)

/** Someone else's published chat: read it, react to a line, favourite it, talk about it. */
@HiltViewModel
class PublishedChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
) : ViewModel() {
    private val slug: String = checkNotNull(savedStateHandle[ARG])

    private val _transcript = MutableStateFlow<PublishedTranscript?>(null)
    val transcript: StateFlow<PublishedTranscript?> = _transcript.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    private val _social = MutableStateFlow<PublishedSocial?>(null)
    val social: StateFlow<PublishedSocial?> = _social.asStateFlow()

    private val _emojis = MutableStateFlow<List<Emoji>>(emptyList())
    val emojis: StateFlow<List<Emoji>> = _emojis.asStateFlow()

    /** The line a reaction is being picked for, or null. */
    private val _picking = MutableStateFlow<Long?>(null)
    val picking: StateFlow<Long?> = _picking.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _comments = MutableStateFlow(PublishedCommentsState())
    val comments: StateFlow<PublishedCommentsState> = _comments.asStateFlow()

    init {
        load()
        viewModelScope.launch { runCatching { repository.myUserId() }.getOrNull()?.let { id -> _comments.update { it.copy(me = id) } } }
    }

    fun load() {
        _error.value = null
        viewModelScope.launch {
            runCatching { repository.publishedChat(slug) }
                .onSuccess { t ->
                    _transcript.value = t
                    if (t.id != 0L) {
                        launch { repository.registerView(t.id) }
                        refreshSocial(t.id)
                    }
                }
                .onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }

    private suspend fun refreshSocial(chatId: Long) {
        runCatching { repository.publishedSocial(chatId) }.onSuccess { _social.value = it }
    }

    fun toggleFavorite() {
        val id = _transcript.value?.id ?: return
        val s = _social.value ?: return
        val on = !s.favorited
        _social.value = s.copy(favorited = on, favoriteCount = (s.favoriteCount + if (on) 1 else -1).coerceAtLeast(0))
        viewModelScope.launch {
            runCatching { repository.setPublishedFavorite(id, on) }
                .onFailure { e -> _social.value = s; _notice.value = "Not saved. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}" }
        }
    }

    fun pickReaction(messageId: Long) {
        _picking.value = messageId
        if (_emojis.value.isEmpty()) viewModelScope.launch { runCatching { repository.emojis() }.onSuccess { _emojis.value = it } }
    }

    fun closePicker() { _picking.value = null }

    /**
     * Adds a reaction, or takes one of the viewer's own back. Shown at once; Janitor's own
     * count replaces the guess a moment later.
     */
    fun react(messageId: Long, emojiId: String) {
        val id = _transcript.value?.id ?: return
        _picking.value = null
        val line = _social.value?.reactions?.get(messageId).orEmpty()
        val had = line.firstOrNull { it.emoji == emojiId }
        val on = had?.mine != true
        _social.update { s ->
            s ?: return@update s
            val next = when {
                had == null -> line + ReactionTally(emojiId, 1, true)
                on -> line.map { if (it.emoji == emojiId) it.copy(count = it.count + 1, mine = true) else it }
                else -> line.mapNotNull { if (it.emoji == emojiId) (if (it.count > 1) it.copy(count = it.count - 1, mine = false) else null) else it }
            }
            s.copy(reactions = s.reactions + (messageId to next))
        }
        viewModelScope.launch {
            runCatching { repository.react(id, messageId, emojiId, on) }
                .onFailure { e -> _notice.value = "Not sent. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}" }
            refreshSocial(id)
        }
    }

    fun dismissNotice() { _notice.value = null }

    fun openComments() {
        _comments.update { it.copy(open = true) }
        if (_comments.value.comments.isEmpty() && !_comments.value.end) loadMoreComments()
    }

    fun closeComments() = _comments.update { it.copy(open = false) }

    fun loadMoreComments() {
        val id = _transcript.value?.id ?: return
        val s = _comments.value
        if (s.loading || s.end) return
        _comments.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.chatComments(id, s.nextPage) }
                .onSuccess { (page, more) ->
                    _comments.update { it.copy(comments = (it.comments + page).distinctBy { c -> c.id }, nextPage = it.nextPage + 1, loading = false, end = !more) }
                }
                .onFailure { e -> _comments.update { it.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun onCommentDraft(text: String) = _comments.update { it.copy(draft = text, sendError = null) }

    /** Posts publicly as the user; the list starts over so the comment shows where Janitor puts it. */
    fun sendComment() {
        val id = _transcript.value?.id ?: return
        val s = _comments.value
        val text = s.draft.trim()
        if (text.isEmpty() || s.sending) return
        _comments.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            runCatching { repository.postChatComment(id, text) }
                .onSuccess {
                    _comments.update { it.copy(sending = false, draft = "", comments = emptyList(), nextPage = 1, end = false) }
                    loadMoreComments()
                    _social.update { so -> so?.copy(commentCount = so.commentCount + 1) }
                }
                .onFailure { e -> _comments.update { it.copy(sending = false, sendError = "Not sent. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    /** One tap toggles; the count moves at once and Janitor's answer settles it. */
    fun likeComment(comment: ChatComment) {
        fun flip(c: ChatComment, liked: Boolean) = c.copy(liked = liked, likes = (c.likes + if (liked == c.liked) 0 else if (liked) 1 else -1).coerceAtLeast(0))
        _comments.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) flip(it, !comment.liked) else it }) }
        viewModelScope.launch {
            runCatching { repository.likeChatComment(comment.id, on = !comment.liked) }
                .onSuccess { count -> if (count != null) _comments.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) it.copy(likes = count) else it }) } }
                .onFailure { _comments.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) flip(it, comment.liked) else it }) } }
        }
    }

    /** Deletes the user's own comment on Janitor, then drops it here. */
    fun deleteComment(comment: ChatComment) {
        if (_comments.value.deleting != null) return
        _comments.update { it.copy(deleting = comment.id, sendError = null) }
        viewModelScope.launch {
            runCatching { repository.deleteChatComment(comment.id) }
                .onSuccess {
                    _comments.update { s -> s.copy(deleting = null, comments = s.comments.filterNot { it.id == comment.id }) }
                    _social.update { so -> so?.copy(commentCount = (so.commentCount - 1).coerceAtLeast(0)) }
                }
                .onFailure { e -> _comments.update { it.copy(deleting = null, sendError = "Not deleted. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    companion object {
        const val ARG = "slug"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PublishedChatScreen(onBack: () -> Unit, viewModel: PublishedChatViewModel = hiltViewModel()) {
    val t by viewModel.transcript.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val social by viewModel.social.collectAsStateWithLifecycle()
    val emojis by viewModel.emojis.collectAsStateWithLifecycle()
    val picking by viewModel.picking.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val emojiById = remember(emojis) { emojis.associateBy { it.id } }

    picking?.let { messageId ->
        EmojiPickerSheet(emojis = emojis, onPick = { viewModel.react(messageId, it.id) }, onDismiss = viewModel::closePicker)
    }
    if (comments.open) {
        PublishedCommentsSheet(
            state = comments,
            total = social?.commentCount ?: 0,
            onLoadMore = viewModel::loadMoreComments,
            onDraft = viewModel::onCommentDraft,
            onSend = viewModel::sendComment,
            onLike = viewModel::likeComment,
            onDelete = viewModel::deleteComment,
            onDismiss = viewModel::closeComments,
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Avatar(url = t?.characterAvatar, name = t?.characterName.orEmpty(), size = 36.dp, initialStyle = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(t?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = t?.let { "${it.characterName} · by @${it.publisherName}" }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ButlerTheme.colors.textLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            social?.let { s ->
                Row(
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = viewModel::toggleFavorite).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (s.favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (s.favorited) "Unfavourite" else "Favourite",
                        tint = if (s.favorited) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                        modifier = Modifier.size(22.dp),
                    )
                    if (s.favoriteCount > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = s.favoriteCount.toLong().compactCount(),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = if (s.favorited) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                        )
                    }
                }
            }
        }
        notice?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.danger,
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissNotice).padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        val transcript = t
        Box(modifier = Modifier.weight(1f)) {
            when {
                transcript == null && error != null -> InlineErrorCard(error!!, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
                transcript == null -> Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) { SkeletonBlock(width = 300.dp, height = 14.dp) }
                }
                else -> {
                    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.78f
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (transcript.description.isNotBlank()) {
                            item(contentType = "about") {
                                Text(
                                    text = transcript.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ButlerTheme.colors.textMed,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                            }
                        }
                        itemsIndexed(transcript.lines, key = { _, l -> l.messageId }, contentType = { _, l -> if (l.isBot) "bot" else "user" }) { _, line ->
                            val tallies = social?.reactions?.get(line.messageId).orEmpty()
                            if (line.isBot) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                        Avatar(url = transcript.characterAvatar, name = transcript.characterName, size = 30.dp, initialStyle = MaterialTheme.typography.labelMedium)
                                        Spacer(Modifier.width(10.dp))
                                        Text(transcript.characterName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    RpText(text = line.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, paragraphSpacing = 8.dp)
                                    ReactionRow(tallies, emojiById, onReact = { viewModel.react(line.messageId, it) }, onAdd = { viewModel.pickReaction(line.messageId) })
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                                    Box(modifier = Modifier.widthIn(max = maxWidth).hairlineFrame(ButlerTheme.colors.rule)) {
                                        RpText(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            paragraphSpacing = 8.dp,
                                        )
                                    }
                                    ReactionRow(tallies, emojiById, onReact = { viewModel.react(line.messageId, it) }, onAdd = { viewModel.pickReaction(line.messageId) })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (transcript != null) {
            HairlineRule(color = ButlerTheme.colors.cardOutline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = viewModel::openComments)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                val n = social?.commentCount ?: 0
                Text(
                    text = when (n) { 0 -> "Comments"; 1 -> "1 comment"; else -> "$n comments" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A line's reactions as small picture-and-count chips, and a key to add one. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReactionRow(tallies: List<ReactionTally>, emojiById: Map<String, Emoji>, onReact: (String) -> Unit, onAdd: () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tallies.forEach { r ->
            val emoji = emojiById[r.emoji]
            Row(
                modifier = Modifier
                    .clip(Pill)
                    .background(if (r.mine) MaterialTheme.colorScheme.primaryContainer else ButlerTheme.colors.surfaceHigh)
                    .then(if (r.mine) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), Pill) else Modifier)
                    .clickable(onClickLabel = if (r.mine) "Take back your ${emoji?.label ?: "reaction"}" else "React with ${emoji?.label ?: r.emoji}") { onReact(r.emoji) }
                    .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (emoji != null) {
                    AsyncImage(model = emoji.imageUrl, contentDescription = emoji.label, modifier = Modifier.size(18.dp))
                } else {
                    Text(r.emoji.substringAfter('-').take(8), style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textMed)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = r.count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = if (r.mine) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textMed,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(Pill)
                .background(ButlerTheme.colors.surfaceHigh)
                .clickable(onClickLabel = "Add a reaction", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AddReaction, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(16.dp))
        }
    }
}

/** Janitor's reaction set: the quick ones in a row, the rest in a grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPickerSheet(emojis: List<Emoji>, onPick: (Emoji) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("React", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (emojis.isEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { SkeletonBlock(width = 40.dp, height = 40.dp, radius = 20.dp) }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(56.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(emojis, key = { it.id }) { e ->
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onPick(e) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AsyncImage(model = e.imageUrl, contentDescription = e.label, modifier = Modifier.size(36.dp))
                            Text(e.label, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The comments on a published chat, newest page first, with a box to add one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishedCommentsSheet(
    state: PublishedCommentsState,
    total: Int,
    onLoadMore: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onLike: (ChatComment) -> Unit,
    onDelete: (ChatComment) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 640.dp)) {
            Text(
                text = if (total > 0) "Comments · $total" else "Comments",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                if (state.comments.isEmpty() && state.end) {
                    item { Text("Nobody has said anything yet.", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(20.dp)) }
                }
                items(state.comments, key = { it.id }) { c ->
                    CommentLine(
                        c,
                        onLike = { onLike(c) },
                        onDelete = if (state.me != null && c.userId == state.me) ({ onDelete(c) }) else null,
                        deleting = state.deleting == c.id,
                    )
                }
                item {
                    LaunchedEffect(state.comments.size) { if (!state.end) onLoadMore() }
                    when {
                        state.error != null -> InlineErrorCard(state.error, onRetry = onLoadMore, modifier = Modifier.padding(16.dp))
                        !state.end -> SkeletonRosterRow(withMargin = false)
                    }
                }
            }
            state.sendError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .card(color = ButlerTheme.colors.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = state.draft,
                        onValueChange = onDraft,
                        enabled = !state.sending,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (state.draft.isEmpty()) Text("Write a comment…", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textLow)
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                val canSend = state.draft.isNotBlank() && !state.sending
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh)
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = if (state.sending) "Sending" else "Post comment",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentLine(c: ChatComment, onLike: () -> Unit, onDelete: (() -> Unit)? = null, deleting: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = c.author.avatarUrl, name = c.author.name, size = 32.dp, initialStyle = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(10.dp))
            Text("@${c.author.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (c.author.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = ButlerTheme.colors.speech, modifier = Modifier.size(14.dp))
            }
            c.createdAt?.let {
                Spacer(Modifier.width(8.dp))
                Text(RelativeTime.short(it), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
            }
        }
        Text(c.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 42.dp, top = 4.dp))
        Row(modifier = Modifier.padding(start = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onLike).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (c.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (c.liked) "Unlike" else "Like",
                    tint = if (c.liked) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(c.likes.toString(), style = MaterialTheme.typography.labelMedium, color = if (c.liked) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow)
            }
            if (onDelete != null) {
                Text(
                    text = if (deleting) "Deleting…" else "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    color = ButlerTheme.colors.danger,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = !deleting, onClick = onDelete).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (c.replyCount > 0) {
                Text(
                    text = if (c.replyCount == 1) "1 reply" else "${c.replyCount} replies",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
