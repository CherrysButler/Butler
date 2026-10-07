package com.cherry.butler.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cherry.butler.core.data.CharacterRepository
import com.cherry.butler.core.data.remote.TagRemoteSource
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.NsfwMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.cherry.butler.core.data.LastPlace
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.SpecialMode
import androidx.paging.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val tagRemoteSource: TagRemoteSource,
    private val lastPlace: LastPlace,
) : ViewModel() {

    /** Opens on the search, sort and filters the user left, not the defaults. */
    private val _query = MutableStateFlow(lastPlace.browseQuery())
    val query: StateFlow<BrowseQuery> = _query.asStateFlow()

    /** What the text field shows, kept separate from the debounced [query] it feeds. */
    private val _searchInput = MutableStateFlow(_query.value.search.orEmpty())
    val searchInput: StateFlow<String> = _searchInput.asStateFlow()

    /**
     * Debounced so typing doesn't fire a request per keystroke, and
     * [distinctUntilChanged] so re-selecting the current sort is a no-op rather than a
     * refresh. `cachedIn` keeps the paged data alive across configuration changes.
     */
    private val pages: Flow<PagingData<Character>> = _query
        .map { it.server() }
        .debounce { if (it.search.isNullOrBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { repository.browse(it) }
        .cachedIn(viewModelScope)

    /** The server's pages, with the phone-side minimums applied as Janitor's app does. */
    val characters: Flow<PagingData<Character>> = combine(
        pages,
        _query.map { Triple(it.minMessages, it.minTokens, it.proxyOnly) }.distinctUntilChanged(),
    ) { paging, (minMessages, minTokens, proxyOnly) ->
        if (minMessages <= 0 && minTokens <= 0 && !proxyOnly) paging
        else paging.filter { it.messageCount >= minMessages && it.totalTokens >= minTokens && (!proxyOnly || it.isProxyEnabled) }
    }

    fun onSearchChanged(value: String) {
        _searchInput.value = value
        _query.value = _query.value.copy(search = value.takeIf { it.isNotBlank() })
    }

    fun onClearSearch() {
        _searchInput.value = ""
        _query.value = _query.value.copy(search = null)
    }

    /**
     * The tag vocabulary. Failing to load it must not break browsing, so a failure
     * leaves the list empty and the picker simply offers nothing — the rest of the
     * screen keeps working.
     */
    private val _tags = MutableStateFlow<List<TagDto>>(emptyList())
    val tags: StateFlow<List<TagDto>> = _tags.asStateFlow()

    init {
        viewModelScope.launch { _query.collect { lastPlace.saveBrowseQuery(it) } }
        viewModelScope.launch {
            runCatching { tagRemoteSource.tags() }
                .onSuccess { loaded -> _tags.value = loaded.sortedBy { it.name.lowercase() } }
        }
    }

    fun onTagToggled(tagId: Int) {
        val current = _query.value.tagIds
        val next = if (tagId in current) current - tagId else current + tagId
        // Server-enforced ceiling; going over is a 400.
        _query.value = _query.value.copy(tagIds = next.take(MAX_TAGS))
    }

    fun onClearTags() {
        _query.value = _query.value.copy(tagIds = emptyList())
    }

    /** The custom tags most used in the results on screen, offered as one-tap additions. */
    val topCustomTags: StateFlow<List<String>> get() = repository.topCustomTags

    /** Adds a creator's tag as typed: `#`, spaces and case don't matter. */
    fun onCustomTagAdded(raw: String) {
        val tag = normalizeCustomTag(raw) ?: return
        val current = _query.value.customTags
        if (tag in current || current.size >= MAX_CUSTOM_TAGS) return
        _query.value = _query.value.copy(customTags = current + tag)
    }

    fun onCustomTagRemoved(tag: String) {
        _query.value = _query.value.copy(customTags = _query.value.customTags - tag)
    }

    /** A "Popular" dropdown pick; leaves Trending and Hidden Gems. */
    fun onSortSelected(sort: CharacterSort) {
        _query.value = _query.value.copy(sort = sort, special = null)
    }

    /** A Trending window or Hidden Gems. */
    fun onSpecialSelected(special: SpecialMode) {
        _query.value = _query.value.copy(special = special)
    }

    fun onModeSelected(mode: NsfwMode) {
        _query.value = _query.value.copy(mode = mode)
    }

    fun onSourceSelected(source: BrowseSource) {
        _query.value = _query.value.copy(source = source)
    }

    fun onMinMessages(value: Long) {
        _query.value = _query.value.copy(minMessages = value.coerceAtLeast(0))
    }

    fun onProxyOnly(on: Boolean) {
        _query.value = _query.value.copy(proxyOnly = on)
    }

    fun onMinTokens(value: Int) {
        _query.value = _query.value.copy(minTokens = value.coerceAtLeast(0))
    }

    /** The filter sheet's eraser: back to Janitor's defaults, search and dropdowns untouched. */
    fun onResetFilters() {
        _query.value = _query.value.copy(mode = NsfwMode.All, minMessages = 0, minTokens = 0, proxyOnly = false)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        /** `tag_id[] must contain no more than 53 elements` — server-enforced. */
        private const val MAX_TAGS = 53
        /** A guard, not Janitor's limit (none seen): past a few, nothing matches anyway. */
        private const val MAX_CUSTOM_TAGS = 10

        /** Custom tags come back lowercase with no spaces (`kinktober2026`); typed ones match that. */
        fun normalizeCustomTag(raw: String): String? =
            raw.trim().removePrefix("#").lowercase().filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }
}
