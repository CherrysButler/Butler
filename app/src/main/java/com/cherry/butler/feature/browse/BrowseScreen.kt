package com.cherry.butler.feature.browse

import com.cherry.butler.ui.components.SearchBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.model.SpecialMode
import com.cherry.butler.core.model.BrowseSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.cherry.butler.core.design.ButlerTheme
import androidx.compose.ui.graphics.Color
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.ui.components.BrowseTileFooter
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.CharacterTile
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.PagingStatus
import com.cherry.butler.ui.components.SkeletonTile
import com.cherry.butler.ui.components.StickToTop
import com.cherry.butler.ui.components.TileGrid
import com.cherry.butler.ui.components.isRetryable
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.userTitle

/**
 * The cast, two cards to a row in Janitor's own layout: name, picture with the chat
 * count, creator, a few lines of description and tags.
 */
@Composable
fun BrowseScreen(
    contentPadding: PaddingValues,
    onCharacterClick: (String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
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
    val letGo = com.cherry.butler.ui.components.rememberLetGoOfField(focusManager)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchBox(
                value = searchInput,
                onValueChange = { if (it.isEmpty()) viewModel.onClearSearch() else viewModel.onSearchChanged(it) },
                placeholder = "Search",
                onSubmit = { focusManager.clearFocus() },
                modifier = Modifier.weight(1f),
            )
            FilterKey(active = query.filterCount, onClick = { showFilters = true })
        }
        ModeBar(
            sort = query.sort,
            special = query.special,
            onSort = viewModel::onSortSelected,
            onSpecial = viewModel::onSpecialSelected,
        )
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
        )
        val paged by viewModel.paged.collectAsStateWithLifecycle()
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
                letGo = letGo,
            )
        } else {
            CharacterRoster(
                characters = characters,
                hasSearch = !query.search.isNullOrBlank(),
                onCharacterClick = onCharacterClick,
                query = query,
                letGo = letGo,
                gridState = viewModel.gridState,
                freshQuery = viewModel::takeFreshQuery,
                hadItems = viewModel.hadItems(query),
                onItems = { viewModel.sawItems(query) },
            )
        }
    }
}

/** The sliders key that opens the filter sheet; a red dot says filters are on. */
@Composable
private fun FilterKey(active: Int, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Tune, contentDescription = if (active > 0) "Filters, $active on" else "Filters", tint = MaterialTheme.colorScheme.onSurface)
        }
        if (active > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/**
 * Janitor's three-part bar: "Popular ⌄" (Popular, Latest, Relevance), "Trending ⌄" (24h,
 * Weekly) and Hidden Gems. The lit part is what the grid shows; a part's menu opens on tap.
 */
@Composable
private fun ModeBar(
    sort: CharacterSort,
    special: SpecialMode?,
    onSort: (CharacterSort) -> Unit,
    onSpecial: (SpecialMode) -> Unit,
) {
    val trending = special == SpecialMode.Trending24 || special == SpecialMode.TrendingWeek
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp)
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
            modifier = Modifier.weight(1f),
        )
        DropSegment(
            label = if (trending) special!!.label else "Trending",
            icon = Icons.Rounded.LocalFireDepartment,
            selected = trending,
            options = listOf(SpecialMode.Trending24.label, SpecialMode.TrendingWeek.label),
            chosen = when (special) { SpecialMode.Trending24 -> 0; SpecialMode.TrendingWeek -> 1; else -> -1 },
            onPick = { onSpecial(if (it == 0) SpecialMode.Trending24 else SpecialMode.TrendingWeek) },
            modifier = Modifier.weight(1.05f),
        )
        Segment(
            label = SpecialMode.HiddenGems.label,
            icon = Icons.Rounded.Diamond,
            selected = special == SpecialMode.HiddenGems,
            onClick = { onSpecial(SpecialMode.HiddenGems) },
            modifier = Modifier.weight(1.25f),
        )
    }
}

/**
 * Under the sort bar: Favorites and Following (tap to narrow to them, again to undo), then tags:
 * a key to pick them (Janitor's own, or a creator's typed in), the
 * chosen ones (tap to drop), then the creators' tags most used in these results (tap to add).
 * Every chosen tag must match.
 */
@Composable
private fun TagStrip(
    source: BrowseSource,
    onSource: (BrowseSource) -> Unit,
    chosen: List<Pair<Int, String>>,
    customTags: List<String>,
    suggested: List<String>,
    onOpenPicker: () -> Unit,
    onRemoveTag: (Int) -> Unit,
    onRemoveCustom: (String) -> Unit,
    onAddCustom: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Your favourites and the creators you follow, where they can be seen (they used to sit in
        // the filter sheet, and people couldn't find them: asked on Reddit, 0.2 thread).
        item(key = "favorites") {
            val on = source == BrowseSource.Favorites
            StripChip(
                text = "Favorites",
                selected = on,
                onClick = { onSource(BrowseSource.Favorites) },
                icon = if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            )
        }
        item(key = "following") {
            StripChip(text = "Following", selected = source == BrowseSource.Following, onClick = { onSource(BrowseSource.Following) })
        }
        item(key = "pick") {
            StripChip(text = if (chosen.isEmpty() && customTags.isEmpty()) "+ Tags" else "+", selected = false, emphasis = true, onClick = onOpenPicker)
        }
        items(chosen.size, key = { "t${chosen[it].first}" }) { i ->
            val (id, name) = chosen[i]
            StripChip(text = "$name  ×", selected = true, onClick = { onRemoveTag(id) })
        }
        items(customTags.size, key = { "c${customTags[it]}" }) { i ->
            val tag = customTags[i]
            StripChip(text = "#$tag  ×", selected = true, tint = com.cherry.butler.ui.components.tagTint(tag), onClick = { onRemoveCustom(tag) })
        }
        items(suggested.size, key = { "s${suggested[it]}" }) { i ->
            val tag = suggested[i]
            StripChip(text = "#$tag", selected = false, tint = com.cherry.butler.ui.components.tagTint(tag), onClick = { onAddCustom(tag) })
        }
    }
}

@Composable
private fun StripChip(text: String, selected: Boolean, onClick: () -> Unit, emphasis: Boolean = false, tint: Color? = null, icon: ImageVector? = null) {
    val shape = com.cherry.butler.core.design.Pill
    val edge = when {
        tint != null -> if (selected) tint else tint.copy(alpha = 0.45f)
        selected || emphasis -> MaterialTheme.colorScheme.primary
        else -> ButlerTheme.colors.rule
    }
    val fill = when {
        tint != null && selected -> tint.copy(alpha = 0.18f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val ink = when {
        tint != null -> tint
        selected -> ButlerTheme.colors.onAccentSoft
        emphasis -> MaterialTheme.colorScheme.primary
        else -> ButlerTheme.colors.textMed
    }
    Row(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, edge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

@Composable
private fun Segment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = Motion.enter(Motion.SHORT),
        label = "segment-fill",
    )
    val ink = if (selected) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textMed
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        trailing?.invoke()
    }
}

/** A segment with a menu: tapping it lights it and opens its choices. */
@Composable
private fun DropSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    options: List<String>,
    chosen: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Segment(
            label = label,
            icon = icon,
            selected = selected,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (selected) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.titleMedium) },
                    trailingIcon = if (i == chosen) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

@Composable
private fun CharacterRoster(
    characters: LazyPagingItems<Character>,
    hasSearch: Boolean,
    onCharacterClick: (String) -> Unit,
    query: com.cherry.butler.core.model.BrowseQuery,
    letGo: NestedScrollConnection? = null,
    gridState: LazyGridState,
    /** Whether [query] is new since the grid last went to the top (once per query, not per visit). */
    freshQuery: (com.cherry.butler.core.model.BrowseQuery) -> Boolean,
    hadItems: Boolean,
    onItems: () -> Unit,
) {
    val status = PagingStatus(characters)
    StickToTop(gridState, firstKey = if (characters.itemCount > 0) characters.peek(0)?.id else null)
    // A new search, sort or filter starts at the top: once now, and again when its first
    // results replace the old ones (the old list may still be showing for a moment). Coming
    // back from a character is not a new query: the grid stays where it was.
    var awaitingTop by remember { mutableStateOf(false) }
    val firstId = if (characters.itemCount > 0) characters.peek(0)?.id else null
    LaunchedEffect(query) {
        if (freshQuery(query)) {
            awaitingTop = true
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(characters.itemCount) { if (characters.itemCount > 0) onItems() }
    LaunchedEffect(firstId) {
        if (awaitingTop && firstId != null) {
            gridState.scrollToItem(0)
            awaitingTop = false
        }
    }

    val tileWidth = TileGrid.tileWidth()
    // peek, not get: looking ahead must not make Paging load pages early.
    com.cherry.butler.ui.components.PrefetchTilePortraits(gridState, characters.itemCount, tileWidth) { i ->
        characters.peek(i)?.let { it.avatarUrl to it.isImageNsfw }
    }

    // Only a genuinely empty mirror justifies taking over the screen. If anything is
    // cached we keep showing it and surface the problem inline — that is the whole point
    // of the offline mirror.
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            status.initialLoading -> SkeletonGrid(tileWidth)
            status.itemCount == 0 && status.refreshError != null -> ErrorState(status.refreshError, onRetry = characters::retry)
            // Back from a character the cached pages take a moment to re-read: blank, not "nothing found".
            status.empty && hadItems -> Unit
            status.empty -> EmptyState(hasSearch)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().then(if (letGo != null) Modifier.nestedScroll(letGo) else Modifier),
                contentPadding = PaddingValues(start = TileGrid.gutter, end = TileGrid.gutter, top = 6.dp, bottom = TileGrid.gutter),
                horizontalArrangement = Arrangement.spacedBy(TileGrid.gap),
                verticalArrangement = Arrangement.spacedBy(TileGrid.gap),
            ) {
                items(
                    count = characters.itemCount,
                    key = { index -> characters.peek(index)?.id ?: index },
                    contentType = { "character" },
                ) { index ->
                    characters[index]?.let { character -> RosterTile(character, tileWidth) { onCharacterClick(character.id) } }
                }
                if (status.appending) {
                    items(2, contentType = { "skeleton" }) { SkeletonTile(tileWidth) }
                }
                status.appendError?.let { error ->
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
                        InlineErrorCard(error, onRetry = characters::retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RosterTile(character: Character, tileWidth: Dp, onClick: () -> Unit) {
    CharacterTile(
        name = character.name,
        avatarUrl = character.avatarUrl,
        width = tileWidth,
        onClick = onClick,
        // The server flags image NSFW separately from character NSFW; obscuring follows the image.
        obscured = character.isImageNsfw,
        nsfw = character.isNsfw,
        chatCount = character.chatCount.compactCount(),
    ) {
        BrowseTileFooter(
            creatorName = character.creatorName,
            creatorVerified = character.creatorVerified,
            creatorColor = character.creatorColor,
            blurb = character.blurb,
            tags = character.tagNames,
        )
    }
}

/**
 * Home one page at a time: this page's tiles, then "Previous · Page N · Next" under them.
 * A page change goes back to the top; the grid state is the same one the endless list keeps.
 */
@Composable
private fun PagedRoster(
    page: BrowseViewModel.Page,
    hasSearch: Boolean,
    gridState: LazyGridState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onCharacterClick: (String) -> Unit,
    letGo: NestedScrollConnection? = null,
) {
    val tileWidth = TileGrid.tileWidth()
    LaunchedEffect(page.number, page.loading) { if (!page.loading) gridState.scrollToItem(0) }
    com.cherry.butler.ui.components.PrefetchTilePortraits(gridState, page.items.size, tileWidth) { i ->
        page.items.getOrNull(i)?.let { it.avatarUrl to it.isImageNsfw }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            page.loading -> SkeletonGrid(tileWidth)
            page.error != null -> ErrorState(page.error, onRetry = onRetry)
            page.items.isEmpty() && page.number == 1 -> EmptyState(hasSearch)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize().then(if (letGo != null) Modifier.nestedScroll(letGo) else Modifier),
                contentPadding = PaddingValues(start = TileGrid.gutter, end = TileGrid.gutter, top = 6.dp, bottom = TileGrid.gutter),
                horizontalArrangement = Arrangement.spacedBy(TileGrid.gap),
                verticalArrangement = Arrangement.spacedBy(TileGrid.gap),
            ) {
                items(page.items, key = { it.id }, contentType = { "character" }) { character ->
                    RosterTile(character, tileWidth) { onCharacterClick(character.id) }
                }
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "pager") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        com.cherry.butler.feature.chats.KeyButton(label = "Previous", onClick = onPrevious, enabled = page.number > 1, modifier = Modifier.weight(1f))
                        Text(
                            text = "Page ${page.number}",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        com.cherry.butler.feature.chats.KeyButton(label = "Next", onClick = onNext, enabled = page.hasMore, primary = page.hasMore, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The grid's own shape, breathing, until the first page lands. Never a spinner. */
@Composable
private fun SkeletonGrid(tileWidth: Dp) {
    Column(
        modifier = Modifier.fillMaxSize().padding(TileGrid.gutter),
        verticalArrangement = Arrangement.spacedBy(TileGrid.gap),
    ) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGrid.gap)) {
                SkeletonTile(tileWidth)
                SkeletonTile(tileWidth)
            }
        }
    }
}

@Composable
private fun ErrorState(error: Throwable, onRetry: () -> Unit) {
    CenteredMessage(
        icon = Icons.Rounded.CloudOff,
        title = error.userTitle(),
        body = error.userMessage(),
    ) {
        if (error.isRetryable()) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("Try again") }
        }
    }
}

@Composable
private fun EmptyState(hasSearch: Boolean) {
    CenteredMessage(
        icon = Icons.Rounded.SearchOff,
        title = if (hasSearch) "No matches" else "Nothing here yet",
        body = if (hasSearch) {
            "No characters matched that search. Try a different term or widen the filters."
        } else {
            "No characters came back for these filters."
        },
    )
}
