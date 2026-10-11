package androidx.paging.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * paging-compose has no desktop build for Compose 1.7, so this is its LazyPagingItems: the same
 * members Butler's screens use (itemCount, get, peek, loadState, refresh, retry), over paging's
 * own PagingDataPresenter, the way the AndroidX version is built.
 */
class LazyPagingItems<T : Any> internal constructor(private val flow: Flow<PagingData<T>>) {

    private val presenter = object : PagingDataPresenter<T>(
        mainContext = Dispatchers.Main,
        cachedPagingData = (flow as? SharedFlow<PagingData<T>>)?.replayCache?.firstOrNull(),
    ) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            itemSnapshotList = snapshot()
        }
    }

    var itemSnapshotList: ItemSnapshotList<T> by mutableStateOf(presenter.snapshot())
        private set

    val itemCount: Int get() = itemSnapshotList.size

    /** The item at [index], telling paging it was shown so the next page loads in time. */
    operator fun get(index: Int): T? {
        presenter[index]
        return itemSnapshotList[index]
    }

    /** The item at [index] without counting as shown. */
    fun peek(index: Int): T? = itemSnapshotList[index]

    fun retry() = presenter.retry()

    fun refresh() = presenter.refresh()

    var loadState: CombinedLoadStates by mutableStateOf(presenter.loadStateFlow.value ?: initial)
        private set

    internal suspend fun collectLoadState() {
        presenter.loadStateFlow.filterNotNull().collect { loadState = it }
    }

    /**
     * Pages are taken in off the UI thread (as AndroidX's collectAsLazyPagingItems allows with
     * a context); the presenter hands each result to the UI thread itself.
     */
    internal suspend fun collectPagingData() = kotlinx.coroutines.withContext(Dispatchers.Default) {
        flow.collectLatest { presenter.collectFrom(it) }
    }

    private companion object {
        val idle = LoadStates(
            refresh = LoadState.NotLoading(false),
            prepend = LoadState.NotLoading(false),
            append = LoadState.NotLoading(false),
        )
        val initial = CombinedLoadStates(
            refresh = LoadState.Loading,
            prepend = idle.prepend,
            append = idle.append,
            source = idle,
        )
    }
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(): LazyPagingItems<T> {
    val items = remember(this) { LazyPagingItems(this) }
    LaunchedEffect(items) { items.collectPagingData() }
    LaunchedEffect(items) { items.collectLoadState() }
    return items
}
