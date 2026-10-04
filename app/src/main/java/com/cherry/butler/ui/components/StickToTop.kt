package com.cherry.butler.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * A lazy list keeps its first visible item fixed when rows are inserted above it, so a
 * refresh that brings a newly active chat to the top leaves it just out of sight. When the
 * reader was at the top as the first row changed, this puts them back at the top.
 *
 * The "was at the top" reading is taken during the composition that sees the new first
 * key — before the list re-measures — so it describes where the reader actually was.
 * It lives in its own composable so the scroll reads only recompose this, not the list.
 */
@Composable
fun StickToTop(state: LazyListState, firstKey: Any?) {
    val wasAtTop = remember(firstKey) { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < TOLERANCE_PX }
    LaunchedEffect(firstKey) { if (firstKey != null && wasAtTop) state.scrollToItem(0) }
}

@Composable
fun StickToTop(state: LazyGridState, firstKey: Any?) {
    val wasAtTop = remember(firstKey) { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < TOLERANCE_PX }
    LaunchedEffect(firstKey) { if (firstKey != null && wasAtTop) state.scrollToItem(0) }
}

private const val TOLERANCE_PX = 24
