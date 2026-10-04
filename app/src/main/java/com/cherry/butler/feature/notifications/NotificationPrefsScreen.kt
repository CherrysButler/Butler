package com.cherry.butler.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.settings.SettingsSection
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Janitor's 20 notification workflows (§21.2), grouped and named for people. */
private val GROUPS: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Characters" to listOf(
        "new-character-created" to "New from creators you follow",
        "character-updated" to "Updates to characters",
        "character-favorited" to "Your characters favourited",
        "character-scheduled-countdown" to "Scheduled characters going live",
    ),
    "Chats" to listOf(
        "new-public-chat" to "New public chats",
        "chat-favorited" to "Your chats favourited",
        "chat-commented" to "Comments on your chats",
        "chat-comment-replied" to "Replies to your chat comments",
        "chat-comment-liked" to "Likes on your chat comments",
    ),
    "Character comments" to listOf(
        "new-review-to-creator" to "Comments on your characters",
        "review-liked" to "Likes on your comments",
        "review-pinned" to "Your comment pinned",
    ),
    "Scripts" to listOf(
        "script-commented" to "Comments on your scripts",
        "script-comment-replied" to "Replies to your script comments",
        "script-comment-liked" to "Likes on your script comments",
        "script-reply-liked" to "Likes on your script replies",
    ),
    "People" to listOf(
        "new-follower" to "New followers",
        "new-comment-to-creator" to "Comments to you as a creator",
        "comment-liked" to "Likes on your other comments",
        "community-poll-created" to "Community polls",
    ),
)

@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(private val repository: CommunityRepository) : ViewModel() {
    /** Null until loaded. Missing workflows are on. */
    private val _prefs = MutableStateFlow<Map<String, Boolean>?>(null)
    val prefs: StateFlow<Map<String, Boolean>?> = _prefs.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _error.value = null
            runCatching { repository.notificationPreferences() }
                .onSuccess { _prefs.value = it }
                .onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }

    /** Flips at once; a refusal flips it back and says why. */
    fun set(workflow: String, active: Boolean) {
        _prefs.update { it?.plus(workflow to active) }
        viewModelScope.launch {
            runCatching { repository.setNotificationPreference(workflow, active) }
                .onFailure { e ->
                    _prefs.update { it?.plus(workflow to !active) }
                    _error.value = e as? ApiError ?: ApiError.Unknown(e)
                }
        }
    }
}

@Composable
fun NotificationPrefsScreen(onBack: () -> Unit, viewModel: NotificationPrefsViewModel = hiltViewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Notifications", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            error?.let { e ->
                if (prefs == null) {
                    InlineErrorCard(e, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
                } else {
                    Text(
                        "Not changed. ${e.userMessage()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.cherry.butler.core.design.ButlerTheme.colors.danger,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
            }
            val p = prefs
            if (p == null && error == null) {
                Column(modifier = Modifier.padding(12.dp)) { repeat(6) { SkeletonRosterRow(withMargin = false) } }
            }
            if (p != null) {
                GROUPS.forEach { (title, rows) ->
                    SettingsSection(title = title) {
                        rows.forEach { (workflow, label) ->
                            SwitchRow(label, null, p[workflow] ?: true, { on -> viewModel.set(workflow, on) })
                        }
                    }
                }
            }
        }
    }
}
