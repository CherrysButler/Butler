package com.cherry.butler.ui.components

import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems

/**
 * The honest read of a mirror-backed paged list.
 *
 * With a RemoteMediator, `loadState.refresh` reports the *Room* query, which says
 * NotLoading with zero rows the moment the query returns — before the network refresh
 * has started. Reading that as "empty" is what flashes "nothing here" over a list that is
 * about to fill. Empty is only true once the mediator has also settled.
 */
class PagingStatus(items: LazyPagingItems<*>) {
    private val source = items.loadState.source
    private val mediator = items.loadState.mediator

    val itemCount: Int = items.itemCount

    /** A refresh is in flight anywhere in the pipeline. */
    val refreshing: Boolean =
        source.refresh is LoadState.Loading || mediator?.refresh is LoadState.Loading

    /** The first load, with nothing on disk to show meanwhile. */
    val initialLoading: Boolean = itemCount == 0 && refreshing

    val refreshError: Throwable? =
        (mediator?.refresh as? LoadState.Error)?.error ?: (source.refresh as? LoadState.Error)?.error

    /** Genuinely nothing: disk is empty and the network refresh has finished without error. */
    val empty: Boolean =
        itemCount == 0 && !refreshing && refreshError == null && (mediator == null || mediator.refresh is LoadState.NotLoading)

    val appending: Boolean = items.loadState.append is LoadState.Loading
    val appendError: Throwable? = (items.loadState.append as? LoadState.Error)?.error
}
