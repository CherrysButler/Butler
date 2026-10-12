package com.cherry.butler.core.data

import com.cherry.butler.core.data.remote.dto.creatorColorOrNull
import com.cherry.butler.core.data.remote.dto.shownDescription
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.CharacterEntity
import com.cherry.butler.core.data.local.RemoteKeyEntity
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.dto.CharacterDto
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.network.ApiError

/**
 * Keeps the Room mirror in step with the server for one [query].
 *
 * Offline-first: Paging reads from Room, so the screen paints from disk immediately and
 * this only ever refreshes behind it. A network failure therefore degrades to *stale
 * content plus an error affordance*, never a blank screen — which is the specific failure
 * mode Butler exists to avoid.
 */
@OptIn(ExperimentalPagingApi::class)
class CharacterRemoteMediator(
    private val query: BrowseQuery,
    private val remote: CharacterRemoteSource,
    private val db: ButlerDatabase,
    /** The custom tags most used in these results, from each page Janitor sends. */
    private val onTopCustomTags: (List<String>) -> Unit = {},
) : RemoteMediator<Int, CharacterEntity>() {

    private val characterDao = db.characterDao()
    private val remoteKeyDao = db.remoteKeyDao()

    /**
     * Skips the automatic refresh when the mirror is still fresh, so returning to the tab
     * doesn't re-fetch what is already on screen.
     */
    override suspend fun initialize(): InitializeAction {
        val oldest = characterDao.oldestCachedAt(query.cacheKey)
            ?: return InitializeAction.LAUNCH_INITIAL_REFRESH
        val age = System.currentTimeMillis() - oldest
        return if (age > CACHE_TIMEOUT_MS) {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        } else {
            InitializeAction.SKIP_INITIAL_REFRESH
        }
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, CharacterEntity>,
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> FIRST_PAGE
            // The catalogue only grows at the end; there is no backwards pagination.
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val key = remoteKeyDao.get(query.cacheKey)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                if (!key.hasMore) return MediatorResult.Success(endOfPaginationReached = true)
                key.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            var next = page
            var endReached: Boolean
            var added: Int
            // A page that brings nothing new writes nothing, and nothing would ask again:
            // the next is fetched at once, a few at most.
            var tries = 0
            do {
                val response = remote.browse(query, next)
                if (next == FIRST_PAGE) onTopCustomTags(response.topCustomTags)

                // `total` is a lower bound capped at 10000, so it cannot tell us when to stop.
                // A short page is the only trustworthy end-of-list signal.
                endReached = response.data.size < CharacterRemoteSource.PAGE_SIZE
                val fresh = loadType == LoadType.REFRESH && next == page
                added = store(response.data, fresh, next, endReached)
                next++
                tries++
            } while (added == 0 && !endReached && tries < MAX_EMPTY_PAGES)

            MediatorResult.Success(endOfPaginationReached = endReached)
        } catch (e: ApiError) {
            // Hand the typed error to Paging so the UI can distinguish "retry might work"
            // from "this will never work", instead of showing one generic failure.
            MediatorResult.Error(e)
        }
    }

    /**
     * One page into the mirror. Janitor's order shifts while the reader scrolls (a character
     * gains chats, a new one is posted), so a page can repeat characters an earlier page
     * brought. Those stay where they were first shown; only the new ones are added, after the
     * last. Nothing already on screen moves: a repeat used to be moved to its new place, every
     * card between slid along the grid, and the list jumped as a page landed. Returns how many
     * were added.
     */
    private suspend fun store(data: List<CharacterDto>, fresh: Boolean, page: Int, endReached: Boolean): Int {
        val now = System.currentTimeMillis()
        var added = 0
        db.withTransaction {
            if (fresh) {
                characterDao.clearQuery(query.cacheKey)
                remoteKeyDao.clear(query.cacheKey)
                // Keep only the active partition so the mirror can't grow unbounded
                // as the user tries filter combinations.
                characterDao.clearAllExcept(query.cacheKey)
            }
            val unique = data.distinctBy { it.id }
            val held = if (fresh) emptySet() else characterDao.heldIds(query.cacheKey, unique.map { it.id }).toSet()
            val start = (characterDao.lastPosition(query.cacheKey) ?: -1) + 1
            val entities = unique.filterNot { it.id in held }.mapIndexed { index, dto ->
                dto.toEntity(query.cacheKey, start + index, now)
            }
            characterDao.upsertAll(entities)
            added = entities.size
            remoteKeyDao.upsert(
                RemoteKeyEntity(
                    queryKey = query.cacheKey,
                    nextPage = if (endReached) null else page + 1,
                    hasMore = !endReached,
                    lastRefreshedAt = now,
                ),
            )
        }
        return added
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val CACHE_TIMEOUT_MS = 15 * 60 * 1000L

        /** Pages in a row with nothing new before the mediator stops asking for this scroll. */
        const val MAX_EMPTY_PAGES = 3
    }
}

internal fun CharacterDto.toEntity(queryKey: String, position: Int, now: Long) = CharacterEntity(
    queryKey = queryKey,
    id = id,
    position = position,
    name = name,
    description = shownDescription,
    avatar = avatar,
    creatorId = creatorId,
    creatorName = creatorName,
    creatorVerified = creatorVerified,
    creatorPlusBadge = creatorPlusBadge,
    chatCount = stats.chat,
    messageCount = stats.message,
    publicChatCount = publicChatCount,
    totalTokens = totalTokens,
    isNsfw = isNsfw,
    isImageNsfw = isImageNsfw,
    tagSlugs = tags.map { it.slug },
    tagNames = tags.map { it.name },
    customTags = customTags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    cachedAt = now,
    isProxyEnabled = isProxyEnabled,
    creatorColor = creatorColorOrNull,
)
