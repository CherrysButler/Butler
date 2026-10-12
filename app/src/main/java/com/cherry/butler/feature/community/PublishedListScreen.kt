package com.cherry.butler.feature.community

import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.itemsIndexed
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

/**
 * Every published chat with one character: the "See all" behind the page's two, a page at a
 * time as the list nears its end, until there are as many as Janitor counts.
 */
@HiltViewModel
class PublishedListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommunityRepository,
) : ViewModel() {
    private val characterId: String = checkNotNull(savedStateHandle["characterId"])
    val name: String = savedStateHandle.get<String>("name").orEmpty()

    data class State(
        /** Null until the first page is in. */
        val chats: List<PublishedChatCard>? = null,
        val total: Int = 0,
        val page: Int = 0,
        val end: Boolean = false,
        val loading: Boolean = false,
        val error: ApiError? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: kotlinx.coroutines.Job? = null

    init {
        more()
    }

    /** From the top: Ctrl+R, or Retry on a first page that failed. */
    fun load() {
        job?.cancel()
        _state.value = State()
        more()
    }

    /** The next page, unless one is on its way or there are no more. */
    fun more() {
        val s = _state.value
        if (s.loading || s.end) return
        _state.value = s.copy(loading = true, error = null)
        job = viewModelScope.launch {
            val page = s.page + 1
            runCatching { repository.publishedChats(characterId, page) }
                .onSuccess { (cards, total) ->
                    val before = s.chats.orEmpty()
                    val all = (before + cards).distinctBy { it.slug }
                    _state.value = s.copy(
                        chats = all,
                        total = total,
                        page = page,
                        // A short page, the count reached, or a page with nothing new (should the
                        // page be ignored, the same 20 again): no more to ask for.
                        end = cards.size < com.cherry.butler.core.data.remote.CommunityRemoteSource.PUBLISHED_PAGE ||
                            all.size >= total || all.size == before.size,
                        loading = false,
                    )
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value = s.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e))
                }
        }
    }
}

@Composable
fun PublishedListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: PublishedListViewModel = hiltViewModel()) {
    // Ctrl+R / F5 on the desktop.
    com.cherry.butler.ui.components.RegisterRefresh { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val chats = state.chats
    val error = state.error
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Published chats", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                val sub = listOfNotNull(viewModel.name.takeIf { it.isNotBlank() }, state.total.takeIf { it > 0 }?.let { "%,d".format(it) })
                if (sub.isNotEmpty()) {
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val list = chats
        when {
            list == null && error != null -> InlineErrorCard(error, onRetry = viewModel::load, modifier = Modifier.padding(16.dp))
            list == null -> Column { repeat(5) { SkeletonRosterRow(withMargin = false) } }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(list, key = { _, c -> c.slug }, contentType = { _, _ -> "published" }) { i, chat ->
                    // Five from the end, the next page is asked for.
                    if (i >= list.size - 5) androidx.compose.runtime.LaunchedEffect(list.size) { viewModel.more() }
                    PublishedRow(chat) { onOpen(chat.slug) }
                }
                when {
                    state.error != null -> item(key = "error", contentType = "error") {
                        InlineErrorCard(state.error!!, onRetry = viewModel::more, modifier = Modifier.padding(16.dp))
                    }
                    !state.end -> item(key = "more", contentType = "skeleton") { SkeletonRosterRow(withMargin = false) }
                }
            }
        }
    }
}
