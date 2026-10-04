package com.cherry.butler.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.data.PublishedChatCard
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.character.PublishedRow
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonRosterRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Every published chat with one character: the "See all" behind the page's two. */
@HiltViewModel
class PublishedListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
) : ViewModel() {
    private val characterId: String = checkNotNull(savedStateHandle["characterId"])
    val name: String = savedStateHandle.get<String>("name").orEmpty()

    private val _chats = MutableStateFlow<List<PublishedChatCard>?>(null)
    val chats: StateFlow<List<PublishedChatCard>?> = _chats.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        _error.value = null
        viewModelScope.launch {
            runCatching { repository.publishedChats(characterId).first }
                .onSuccess { _chats.value = it }
                .onFailure { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
        }
    }
}

@Composable
fun PublishedListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: PublishedListViewModel = hiltViewModel()) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Published chats", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (viewModel.name.isNotBlank()) {
                    Text(viewModel.name, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val list = chats
        when {
            list == null && error != null -> InlineErrorCard(error!!, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
            list == null -> Column { repeat(5) { SkeletonRosterRow(withMargin = false) } }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { it.slug }, contentType = { "published" }) { chat -> PublishedRow(chat) { onOpen(chat.slug) } }
            }
        }
    }
}
