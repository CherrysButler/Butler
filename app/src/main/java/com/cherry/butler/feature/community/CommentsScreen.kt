package com.cherry.butler.feature.community

import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.Comment
import com.cherry.butler.core.data.CommentAuthor
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.data.Reply
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommentsState(
    val comments: List<Comment> = emptyList(),
    val nextPage: Int = 1,
    val loading: Boolean = false,
    val end: Boolean = false,
    val error: ApiError? = null,
    val replies: Map<String, List<Reply>> = emptyMap(),
    val openReplies: Set<String> = emptySet(),
    val loadingReplies: Set<String> = emptySet(),
    val replyingTo: Comment? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val sendError: String? = null,
    /** A new comment's thumb: Janitor keeps a like or a dislike with each one. */
    val likes: Boolean = true,
    val me: String? = null,
    val deleting: String? = null,
)

/** A character's comments (Janitor's "reviews"), 20 a page, with their replies on demand. */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
) : ViewModel() {
    val characterId: String = checkNotNull(savedStateHandle[ARG])
    val characterName: String = savedStateHandle.get<String>(ARG_NAME).orEmpty()

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()

    init {
        loadMore()
        viewModelScope.launch { repository.myUserName()?.let { me -> _state.update { it.copy(me = me) } } }
    }

    fun flipThumb() = _state.update { it.copy(likes = !it.likes) }

    /** Deletes the user's own comment on Janitor, then drops it here. */
    fun delete(comment: Comment) {
        if (_state.value.deleting != null) return
        _state.update { it.copy(deleting = comment.id, sendError = null) }
        viewModelScope.launch {
            runCatching { repository.deleteComment(comment.id) }
                .onSuccess { _state.update { s -> s.copy(deleting = null, comments = s.comments.filterNot { it.id == comment.id }) } }
                .onFailure { e -> _state.update { it.copy(deleting = null, sendError = "Not deleted. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    /** Starts the list over, so a comment just posted shows where Janitor puts it. */
    private fun reload() {
        _state.update { it.copy(comments = emptyList(), nextPage = 1, end = false, loading = false) }
        loadMore()
    }

    /** One tap toggles; the count moves at once and Janitor's answer settles it. */
    fun toggleLike(comment: Comment) {
        fun flip(c: Comment, liked: Boolean) = c.copy(liked = liked, likes = (c.likes + if (liked == c.liked) 0 else if (liked) 1 else -1).coerceAtLeast(0))
        _state.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) flip(it, !comment.liked) else it }) }
        viewModelScope.launch {
            runCatching { repository.toggleLike(comment.id) }
                .onSuccess { liked -> if (liked != null) _state.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) flip(it, liked) else it }) } }
                .onFailure { _state.update { s -> s.copy(comments = s.comments.map { if (it.id == comment.id) flip(it, comment.liked) else it }) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.end) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.comments(characterId, s.nextPage) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            comments = (it.comments + page).distinctBy { c -> c.id },
                            nextPage = it.nextPage + 1,
                            loading = false,
                            end = page.size < PAGE_SIZE,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun toggleReplies(comment: Comment) {
        val s = _state.value
        if (comment.id in s.openReplies) {
            _state.update { it.copy(openReplies = it.openReplies - comment.id) }
            return
        }
        _state.update { it.copy(openReplies = it.openReplies + comment.id) }
        if (comment.id in s.replies) return
        loadReplies(comment.id)
    }

    private fun loadReplies(commentId: String) {
        _state.update { it.copy(loadingReplies = it.loadingReplies + commentId) }
        viewModelScope.launch {
            val result = runCatching { repository.replies(commentId) }.getOrDefault(emptyList())
            _state.update { it.copy(replies = it.replies + (commentId to result), loadingReplies = it.loadingReplies - commentId) }
        }
    }

    fun startReply(comment: Comment) = _state.update { it.copy(replyingTo = comment, sendError = null) }

    fun cancelReply() = _state.update { it.copy(replyingTo = null, draft = "", sendError = null) }

    fun onDraft(text: String) = _state.update { it.copy(draft = text, sendError = null) }

    /**
     * Posts publicly: a reply to [CommentsState.replyingTo] when there is one, which then
     * shows in place, or else a new comment on the character, after which the list reloads.
     */
    fun send() {
        val s = _state.value
        val text = s.draft.trim()
        if (text.isEmpty() || s.sending) return
        val target = s.replyingTo
        if (target == null) {
            _state.update { it.copy(sending = true, sendError = null) }
            viewModelScope.launch {
                runCatching { repository.postComment(characterId, text, s.likes) }
                    .onSuccess { _state.update { it.copy(sending = false, draft = "") }; reload() }
                    .onFailure { e -> _state.update { it.copy(sending = false, sendError = "Not sent. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
            }
            return
        }
        _state.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            runCatching { repository.reply(target.id, text) }
                .onSuccess {
                    _state.update { st ->
                        st.copy(
                            sending = false, draft = "", replyingTo = null,
                            openReplies = st.openReplies + target.id,
                            comments = st.comments.map { if (it.id == target.id) it.copy(replyCount = it.replyCount + 1) else it },
                        )
                    }
                    loadReplies(target.id)
                }
                .onFailure { e -> _state.update { it.copy(sending = false, sendError = "Not sent. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    companion object {
        const val ARG = "characterId"
        const val ARG_NAME = "name"
        private const val PAGE_SIZE = 20
    }
}

@Composable
fun CommentsScreen(onBack: () -> Unit, viewModel: CommentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (viewModel.characterName.isNotBlank()) {
                    Text(viewModel.characterName, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.comments.isEmpty() && state.loading -> Column { repeat(6) { SkeletonRosterRow(withMargin = false) } }
                state.comments.isEmpty() && state.error != null -> InlineErrorCard(state.error!!, onRetry = viewModel::loadMore, modifier = Modifier.padding(16.dp))
                state.comments.isEmpty() && state.end -> CenteredMessage(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = "No comments yet",
                    body = "Nobody has said anything about this character.",
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    items(state.comments, key = { it.id }, contentType = { "comment" }) { comment ->
                        CommentItem(
                            comment = comment,
                            open = comment.id in state.openReplies,
                            replies = state.replies[comment.id],
                            loadingReplies = comment.id in state.loadingReplies,
                            onToggleReplies = { viewModel.toggleReplies(comment) },
                            onReply = { viewModel.startReply(comment) },
                            onLike = { viewModel.toggleLike(comment) },
                            onDelete = if (state.me != null && comment.author.name.equals(state.me, ignoreCase = true)) ({ viewModel.delete(comment) }) else null,
                            deleting = state.deleting == comment.id,
                        )
                    }
                    item(contentType = "end") {
                        LaunchedEffect(state.comments.size) { if (!state.end) viewModel.loadMore() }
                        when {
                            state.error != null -> InlineErrorCard(state.error!!, onRetry = viewModel::loadMore, modifier = Modifier.padding(16.dp))
                            !state.end -> SkeletonRosterRow(withMargin = false)
                        }
                    }
                }
            }
        }
        ReplyBar(
            target = state.replyingTo?.author?.name,
            draft = state.draft,
            sending = state.sending,
            error = state.sendError,
            likes = state.likes,
            onFlipThumb = viewModel::flipThumb,
            onDraft = viewModel::onDraft,
            onSend = viewModel::send,
            onCancel = viewModel::cancelReply,
        )
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    open: Boolean,
    replies: List<Reply>?,
    loadingReplies: Boolean,
    onToggleReplies: () -> Unit,
    onReply: () -> Unit,
    onLike: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    deleting: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        AuthorLine(comment.author, comment.createdAt, pinned = comment.pinned)
        Text(
            text = comment.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 44.dp, top = 4.dp),
        )
        Row(modifier = Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onLike).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (comment.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (comment.liked) "Unlike" else "Like",
                    tint = if (comment.liked) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(comment.likes.toString(), style = MaterialTheme.typography.labelMedium, color = if (comment.liked) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow)
            }
            TextButton(onClick = onReply, shape = MaterialTheme.shapes.small) { Text("Reply", color = ButlerTheme.colors.textMed) }
            if (onDelete != null) {
                TextButton(onClick = onDelete, enabled = !deleting, shape = MaterialTheme.shapes.small) {
                    Text(if (deleting) "Deleting…" else "Delete", color = ButlerTheme.colors.danger)
                }
            }
            if (comment.replyCount > 0) {
                TextButton(onClick = onToggleReplies, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = if (open) "Hide replies" else if (comment.replyCount == 1) "View 1 reply" else "View ${comment.replyCount} replies",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (open) {
            Column(
                modifier = Modifier.padding(start = 44.dp, top = 2.dp).fillMaxWidth().card(color = MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    loadingReplies && replies == null -> Text("Loading replies…", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
                    replies.isNullOrEmpty() -> Text("No replies to show.", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
                    else -> replies.forEach { r ->
                        Column {
                            AuthorLine(r.author, r.createdAt, small = true)
                            Text(r.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 34.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorLine(author: CommentAuthor, at: Long?, pinned: Boolean = false, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(url = author.avatarUrl, name = author.name, size = if (small) 26.dp else 34.dp, initialStyle = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape))
        Spacer(Modifier.width(if (small) 8.dp else 10.dp))
        Text(
            text = "@${author.name}",
            style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (author.verified) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = ButlerTheme.colors.speech, modifier = Modifier.size(14.dp))
        }
        at?.let {
            Spacer(Modifier.width(8.dp))
            Text(RelativeTime.short(it), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
        }
        if (pinned) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * The comment box at the foot: a new comment on the character, with its thumb, or — after
 * Reply — an answer to one comment. Both post publicly as the user.
 */
@Composable
private fun ReplyBar(
    target: String?,
    draft: String,
    sending: Boolean,
    error: String?,
    likes: Boolean,
    onFlipThumb: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().imePadding().padding(12.dp)) {
        if (target != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Replying to @$target", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(18.dp))
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.danger, modifier = Modifier.padding(bottom = 6.dp)) }
        Row(verticalAlignment = Alignment.Bottom) {
            if (target == null) {
                // A comment on the character carries a like or a dislike.
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ButlerTheme.colors.surfaceHigh)
                        .clickable(enabled = !sending, onClickLabel = "Switch between like and dislike", onClick = onFlipThumb),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (likes) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbDown,
                        contentDescription = if (likes) "Liked" else "Disliked",
                        tint = if (likes) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .card(shape = RoundedCornerShape(23.dp), color = ButlerTheme.colors.surfaceHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    enabled = !sending,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) Text(if (target == null) "Write a comment…" else "Write a reply…", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textLow)
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = draft.isNotBlank() && !sending
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canSend) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh)
                    .clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = if (sending) "Sending" else if (target == null) "Post comment" else "Send reply",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
