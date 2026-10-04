package com.cherry.butler.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchScope
import androidx.compose.foundation.lazy.grid.LazyGridPrefetchStrategy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Prefetches several items ahead of the scroll instead of one.
 *
 * The default strategy composes exactly the next item during idle time. A fast fling
 * on a list of heavy cards (image + four text blocks + icons) outruns that: the second
 * and third items enter the viewport before they exist and get composed, measured and
 * drawn inside the frame that shows them, which is the lag the user feels. Queuing
 * [ahead] items lets the idle gaps between frames absorb that work instead.
 *
 * Nothing here runs per frame when the list is still; prefetch only fires on scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class AheadPrefetchStrategy(private val ahead: Int = 3) : LazyListPrefetchStrategy {

    private var forward = true
    private val pending = ArrayList<Pair<Int, LazyLayoutPrefetchState.PrefetchHandle>>(ahead)

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return

        val scrollingForward = delta < 0
        if (scrollingForward != forward) {
            // Direction flipped: whatever was queued for the old direction is now wasted work.
            pending.forEach { it.second.cancel() }
            pending.clear()
            forward = scrollingForward
        }

        val edge = if (scrollingForward) visible.last().index + 1 else visible.first().index - 1
        val step = if (scrollingForward) 1 else -1
        val total = layoutInfo.totalItemsCount

        for (k in 0 until ahead) {
            val index = edge + k * step
            if (index !in 0 until total) break
            if (pending.none { it.first == index }) {
                pending += index to schedulePrefetch(index)
            }
        }

        // If the next item will cross into the viewport on this very frame, it can no
        // longer wait for idle time.
        val next = pending.firstOrNull { it.first == edge } ?: return
        val distance = if (scrollingForward) {
            val last = visible.last()
            last.offset + last.size + layoutInfo.mainAxisItemSpacing - layoutInfo.viewportEndOffset
        } else {
            layoutInfo.viewportStartOffset - visible.first().offset
        }
        if (distance < abs(delta)) next.second.markAsUrgent()
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || pending.isEmpty()) return
        val range = visible.first().index..visible.last().index
        // Anything now on screen was consumed; anything behind the scroll was skipped past.
        pending.removeAll { (index, handle) ->
            when {
                index in range -> true
                forward && index < range.first -> { handle.cancel(); true }
                !forward && index > range.last -> { handle.cancel(); true }
                else -> false
            }
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        repeat(2) { schedulePrefetch(firstVisibleItemIndex + it) }
    }
}

/** A list state for any scrolling list of heavy rows: the default state plus [AheadPrefetchStrategy]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberFlingListState(ahead: Int = 3): LazyListState {
    val strategy = remember(ahead) { AheadPrefetchStrategy(ahead) }
    return rememberLazyListState(prefetchStrategy = strategy)
}

/** The grid counterpart of [AheadPrefetchStrategy]: whole lines, [ahead] of them. */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class AheadGridPrefetchStrategy(private val ahead: Int = 2) : LazyGridPrefetchStrategy {

    private var forward = true
    private val pending = ArrayList<Pair<Int, List<LazyLayoutPrefetchState.PrefetchHandle>>>(ahead)

    private fun LazyGridLayoutInfo.line(item: LazyGridItemInfo): Int =
        if (orientation == Orientation.Vertical) item.row else item.column

    override fun LazyGridPrefetchScope.onScroll(delta: Float, layoutInfo: LazyGridLayoutInfo) {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return

        val scrollingForward = delta < 0
        if (scrollingForward != forward) {
            pending.forEach { (_, handles) -> handles.forEach { it.cancel() } }
            pending.clear()
            forward = scrollingForward
        }

        val inBounds = if (scrollingForward) {
            visible.last().index + 1 < layoutInfo.totalItemsCount
        } else {
            visible.first().index > 0
        }
        if (!inBounds) return

        val edge = if (scrollingForward) layoutInfo.line(visible.last()) + 1 else layoutInfo.line(visible.first()) - 1
        val step = if (scrollingForward) 1 else -1
        for (k in 0 until ahead) {
            val line = edge + k * step
            if (line < 0) break
            if (pending.none { it.first == line }) {
                val handles = scheduleLinePrefetch(line)
                if (handles.isEmpty()) break
                pending += line to handles
            }
        }

        val next = pending.firstOrNull { it.first == edge } ?: return
        val vertical = layoutInfo.orientation == Orientation.Vertical
        val distance = if (scrollingForward) {
            val last = visible.last()
            val end = if (vertical) last.offset.y + last.size.height else last.offset.x + last.size.width
            end + layoutInfo.mainAxisItemSpacing - layoutInfo.viewportEndOffset
        } else {
            val first = visible.first()
            layoutInfo.viewportStartOffset - (if (vertical) first.offset.y else first.offset.x)
        }
        if (distance < abs(delta)) next.second.forEach { it.markAsUrgent() }
    }

    override fun LazyGridPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyGridLayoutInfo) {
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || pending.isEmpty()) return
        val range = layoutInfo.line(visible.first())..layoutInfo.line(visible.last())
        pending.removeAll { (line, handles) ->
            when {
                line in range -> true
                forward && line < range.first -> { handles.forEach { it.cancel() }; true }
                !forward && line > range.last -> { handles.forEach { it.cancel() }; true }
                else -> false
            }
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        repeat(2) { schedulePrefetch(firstVisibleItemIndex + it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberFlingGridState(ahead: Int = 2): LazyGridState {
    val strategy = remember(ahead) { AheadGridPrefetchStrategy(ahead) }
    return rememberLazyGridState(prefetchStrategy = strategy)
}
