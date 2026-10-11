package com.cherry.butler.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.SpecialMode
import com.cherry.butler.feature.browse.BrowseViewModel
import com.cherry.butler.feature.browse.CharacterRoster
import com.cherry.butler.feature.browse.DropSegment
import com.cherry.butler.feature.browse.FilterKey
import com.cherry.butler.feature.browse.FilterSheet
import com.cherry.butler.feature.browse.PagedRoster
import com.cherry.butler.feature.browse.Segment
import com.cherry.butler.feature.browse.TagPickerSheet
import com.cherry.butler.feature.browse.TagStrip
import com.cherry.butler.ui.components.SearchBox
import kotlinx.coroutines.launch

/**
 * Browse on the desktop: one toolbar line (the title, a search box of a reading width, the
 * sort at its own size and the filter key), the tag row under it, then the grid, as many
 * tiles across as the pane holds. The pieces and the view model are the phone's.
 */
@Composable
fun DesktopBrowse(onCharacterClick: (String) -> Unit, viewModel: BrowseViewModel = hiltViewModel()) {
    val characters = viewModel.characters.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchInput by viewModel.searchInput.collectAsStateWithLifecycle()
    val topCustomTags by viewModel.topCustomTags.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var showTagPicker by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    if (showTagPicker) {
        TagPickerSheet(
            tags = tags,
            selected = query.tagIds,
            onToggle = viewModel::onTagToggled,
            onClearAll = viewModel::onClearTags,
            onDismiss = { showTagPicker = false },
            onAddCustom = viewModel::onCustomTagAdded,
        )
    }
    if (showFilters) {
        FilterSheet(
            query = query,
            onMode = viewModel::onModeSelected,
            onMinMessages = viewModel::onMinMessages,
            onMinTokens = viewModel::onMinTokens,
            onProxyOnly = viewModel::onProxyOnly,
            onReset = viewModel::onResetFilters,
            onDismiss = { showFilters = false },
        )
    }

    val focusManager = LocalFocusManager.current
    // The development benchmark scrolls this grid from here, where a frame clock is at hand.
    val benchScope = rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(viewModel.gridState) {
        val grid = viewModel.gridState
        com.cherry.butler.desktop.DesktopBench.scroller = { px, ms ->
            benchScope.launch {
                // A steady speed, frame by frame, that a stray wheel or hover can't interrupt.
                grid.scroll(androidx.compose.foundation.MutatePriority.PreventUserInput) {
                    val start = androidx.compose.runtime.withFrameNanos { it }
                    var done = 0f
                    while (true) {
                        val elapsed = (androidx.compose.runtime.withFrameNanos { it } - start) / 1_000_000f
                        val target = px * (elapsed / ms).coerceAtMost(1f)
                        done += scrollBy(target - done)
                        if (elapsed >= ms) break
                    }
                }
            }.join()
        }
        com.cherry.butler.desktop.DesktopBench.toTop = { benchScope.launch { grid.scrollToItem(0) }.join() }
        onDispose {
            com.cherry.butler.desktop.DesktopBench.scroller = null
            com.cherry.butler.desktop.DesktopBench.toTop = null
        }
    }
    // Four cards across at most, in a column down the middle; the room either side is the
    // door's dither, drifting and gathering under the pointer. A wall of portraits edge to edge
    // was more to take in (and to draw) than anyone browses at once. The field steps 12 times a
    // second: each step redraws the whole window (measured ~11% of a core at 30), and a wave
    // that takes 26 s to cross 8 px cells looks the same at 12.
    val pointer = remember { PointerTrack() }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().trackPointer(pointer).wheelScrolls(viewModel.gridState)) {
    val column = minOf(maxWidth, BrowseColumn)
    if (maxWidth > column + 48.dp) {
        DitherField(pointer, column, Modifier.fillMaxSize(), DitherShape.Column, driftFps = Integer.getInteger("butler.dither.fps", 12))
    }
    PaneSize(column, maxHeight, Modifier.align(Alignment.TopCenter).width(column)) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Browse", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(24.dp))
            SearchBox(
                value = searchInput,
                onValueChange = { if (it.isEmpty()) viewModel.onClearSearch() else viewModel.onSearchChanged(it) },
                placeholder = "Search characters",
                onSubmit = { focusManager.clearFocus() },
                modifier = Modifier.widthIn(min = 180.dp, max = 420.dp).weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
            SortBar(
                sort = query.sort,
                special = query.special,
                onSort = viewModel::onSortSelected,
                onSpecial = viewModel::onSpecialSelected,
            )
            Spacer(Modifier.width(4.dp))
            FilterKey(active = query.filterCount, onClick = { showFilters = true })
        }
        val tagRow = rememberLazyListState()
        TagStrip(
            source = query.source,
            onSource = { picked -> viewModel.onSourceSelected(if (query.source == picked) BrowseSource.All else picked) },
            chosen = remember(tags, query.tagIds) { tags.filter { it.id in query.tagIds }.map { it.id to it.name } },
            customTags = query.customTags,
            suggested = remember(topCustomTags, query.customTags) { topCustomTags.filter { it !in query.customTags } },
            onOpenPicker = { showTagPicker = true },
            onRemoveTag = viewModel::onTagToggled,
            onRemoveCustom = viewModel::onCustomTagRemoved,
            onAddCustom = viewModel::onCustomTagAdded,
            state = tagRow,
            modifier = Modifier.wheelSideways(tagRow).fadeEdges(),
        )
        val paged by viewModel.paged.collectAsStateWithLifecycle()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (paged) {
            val page by viewModel.page.collectAsStateWithLifecycle()
            PagedRoster(
                page = page,
                hasSearch = !query.search.isNullOrBlank(),
                gridState = viewModel.gridState,
                onPrevious = viewModel::previousPage,
                onNext = viewModel::nextPage,
                onRetry = viewModel::retryPage,
                onCharacterClick = onCharacterClick,
            )
        } else {
            CharacterRoster(
                characters = characters,
                hasSearch = !query.search.isNullOrBlank(),
                onCharacterClick = onCharacterClick,
                query = query,
                gridState = viewModel.gridState,
                freshQuery = viewModel::takeFreshQuery,
                hadItems = viewModel.hadItems(query),
                onItems = { viewModel.sawItems(query) },
            )
        }
            // A PC list shows where it is: a thin bar at the edge, brighter under the pointer.
            androidx.compose.foundation.VerticalScrollbar(
                adapter = androidx.compose.foundation.rememberScrollbarAdapter(viewModel.gridState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
    }
    }
    }
}

/** Four of the widest tiles with their gaps and gutters (TileGrid), and a hair to spare. */
private val BrowseColumn = 196.dp * 4 + 10.dp * 3 + 12.dp * 2 + 2.dp

/** Janitor's three ways to rank, each at its own width rather than a third of the window. */
@Composable
private fun SortBar(
    sort: CharacterSort,
    special: SpecialMode?,
    onSort: (CharacterSort) -> Unit,
    onSpecial: (SpecialMode) -> Unit,
) {
    val trending = special == SpecialMode.Trending24 || special == SpecialMode.TrendingWeek
    Row(
        modifier = Modifier
            .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.medium)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DropSegment(
            label = sort.label,
            icon = Icons.Rounded.StarBorder,
            selected = special == null,
            options = CharacterSort.entries.map { it.label },
            chosen = if (special == null) CharacterSort.entries.indexOf(sort) else -1,
            onPick = { onSort(CharacterSort.entries[it]) },
            modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 112.dp),
        )
        DropSegment(
            label = if (trending) special!!.label else "Trending",
            icon = Icons.Rounded.LocalFireDepartment,
            selected = trending,
            options = listOf(SpecialMode.Trending24.label, SpecialMode.TrendingWeek.label),
            chosen = when (special) { SpecialMode.Trending24 -> 0; SpecialMode.TrendingWeek -> 1; else -> -1 },
            onPick = { onSpecial(if (it == 0) SpecialMode.Trending24 else SpecialMode.TrendingWeek) },
            modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 104.dp),
        )
        Segment(
            label = SpecialMode.HiddenGems.label,
            icon = Icons.Rounded.Diamond,
            selected = special == SpecialMode.HiddenGems,
            onClick = { onSpecial(SpecialMode.HiddenGems) },
            modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 4.dp),
        )
    }
}

/** A mouse wheel has only one axis: over a sideways row, it scrolls the row (and only the row). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.wheelSideways(state: LazyListState): Modifier {
    val scope = rememberCoroutineScope()
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy != 0f) {
            event.changes.forEach { it.consume() }
            scope.launch { state.animateScrollBy(dy * 64f) }
        }
    }
}

/** The row's ends fade into the ground, so a row that runs on reads as one that runs on. */
internal fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx() / size.width
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Transparent,
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
