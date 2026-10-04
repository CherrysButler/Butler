package com.cherry.butler.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.data.Notice
import com.cherry.butler.core.data.NotificationTarget
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.feature.chats.ScreenHead
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.Roster
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.userTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repository: CommunityRepository) : ViewModel() {
    private val _items = MutableStateFlow<List<Notice>?>(null)
    val items: StateFlow<List<Notice>?> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            runCatching { repository.notifications() }
                .onSuccess { _items.value = it }
                .onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
            _refreshing.value = false
        }
    }
}

/**
 * Janitor's bell: likes, replies, follows and new characters from people you follow.
 * Read-only; a notice that points at a published chat or a character opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues,
    onOpenPublished: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHead(title = "Notifications")
        PullToRefreshBox(isRefreshing = refreshing && items != null, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            val list = items
            when {
                list == null && error != null -> CenteredMessage(icon = Icons.Rounded.CloudOff, title = error!!.userTitle(), body = error!!.userMessage())
                list == null -> Column { repeat(6) { SkeletonRosterRow(withMargin = false) } }
                list.isEmpty() -> CenteredMessage(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Nothing new",
                    body = "Likes, replies and follows show up here.",
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(list, key = { it.id }, contentType = { "notice" }) { n ->
                        NoticeRow(n) {
                            when (val t = n.target) {
                                is NotificationTarget.PublishedChat -> onOpenPublished(t.slug)
                                is NotificationTarget.Character -> onOpenCharacter(t.id)
                                null -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(n: Notice, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(enabled = n.target != null, onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = Roster.gutter, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box {
                Avatar(url = n.imageUrl, name = n.subject, size = 44.dp, initialStyle = MaterialTheme.typography.titleMedium)
                if (!n.isRead) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = n.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (n.isRead) FontWeight.Medium else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    n.createdAt?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(RelativeTime.short(it), style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(n.body, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(horizontal = Roster.gutter))
    }
}
