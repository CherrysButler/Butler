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
import kotlinx.coroutines.flow.drop
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val tagRemoteSource: TagRemoteSource,
    private val lastPlace: LastPlace,
    browsePrefs: com.cherry.butler.core.data.BrowsePrefs,
) : ViewModel() {

    /**
     * The grid's scroll state lives here, not in the screen: opening a character takes the
     * tab out of composition, and a state remembered there came back at the top.
     */
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val gridState = androidx.compose.foundation.lazy.grid.LazyGridState(
        prefetchStrategy = com.cherry.butler.ui.components.AheadGridPrefetchStrategy(2),
    )

    private var scrolledFor: BrowseQuery? = null

    /** True once per new search, sort or filter, when the grid should go back to the top. */
    fun takeFreshQuery(query: BrowseQuery): Boolean {
        if (scrolledFor == query) return false
        scrolledFor = query
        return true
    }

    private var itemsSeenFor: BrowseQuery? = null
    fun sawItems(query: BrowseQuery) { itemsSeenFor = query }
    /** Results for [query] were on screen before: a momentary empty list is a re-read, not "nothing". */
    fun hadItems(query: BrowseQuery): Boolean = itemsSeenFor == query

    /** One page at a time instead of the endless scroll (Settings › Look). */
    val paged: StateFlow<Boolean> = browsePrefs.paged

    /** The paged Home's page: its number, what's on it, and whether another follows. */
    data class Page(
        val number: Int = 1,
        val items: List<Character> = emptyList(),
        val loading: Boolean = true,
        val error: Throwable? = null,
        val hasMore: Boolean = false,
    )

    private val _page = MutableStateFlow(Page())
    val page: StateFlow<Page> = _page.asStateFlow()
    private var pageJob: kotlinx.coroutines.Job? = null
    private var pageQuery: BrowseQuery? = null

    fun nextPage() { if (_page.value.hasMore && !_page.value.loading) loadPage(_page.value.number + 1) }
    fun previousPage() { if (_page.value.number > 1 && !_page.value.loading) loadPage(_page.value.number - 1) }
    fun retryPage() = loadPage(_page.value.number)

    private fun loadPage(number: Int) {
        val query = pageQuery ?: _query.value.server()
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _page.value = Page(number = number, loading = true)
            val result = runCatching { repository.page(query, number) }
            val filters = _query.value
            _page.value = result.fold(
                onSuccess = { (items, more) ->
                    val kept = items.filter {
                        it.messageCount >= filters.minMessages && it.totalTokens >= filters.minTokens && (!filters.proxyOnly || it.isProxyEnabled)
                    }
                    Page(number = number, items = kept, loading = false, hasMore = more)
                },
                onFailure = { Page(number = number, loading = false, error = it) },
            )
        }
    }

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
        // Paged Home: a new query starts over at page 1; the phone-side minimums re-filter in place.
        viewModelScope.launch {
            combine(
                _query.map { it.server() }.debounce { if (it.search.isNullOrBlank()) 0L else SEARCH_DEBOUNCE_MS }.distinctUntilChanged(),
                paged,
            ) { q, on -> q to on }.collect { (q, on) ->
                pageQuery = q
                if (on) loadPage(1)
            }
        }
        viewModelScope.launch {
            _query.map { Triple(it.minMessages, it.minTokens, it.proxyOnly) }.distinctUntilChanged().drop(1)
                .collect { if (paged.value) loadPage(_page.value.number) }
        }
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
