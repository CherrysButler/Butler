package com.cherry.butler.core.data

import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.core.markdown.plainPreview

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.cherry.butler.core.data.local.ButlerDatabase
import com.cherry.butler.core.data.local.CharacterEntity
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.Character
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val remote: CharacterRemoteSource,
    private val db: ButlerDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) {
    private val prefs = context.getSharedPreferences("butler_prefs", android.content.Context.MODE_PRIVATE)

    // Kept on the phone: results served from the cache bring no new ones, and the cache only
    // ever holds the latest query, so the latest suggestions are the ones that belong to it.
    private val _topCustomTags = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString(KEY_TOP_TAGS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
    )

    /** The custom tags most used in the latest results (`top_custom_tags`), for suggestions. */
    val topCustomTags: kotlinx.coroutines.flow.StateFlow<List<String>> = _topCustomTags

    private fun keepTopCustomTags(tags: List<String>) {
        _topCustomTags.value = tags
        prefs.edit().putString(KEY_TOP_TAGS, tags.joinToString(",")).apply()
    }


    /**
     * Paged characters for [query], served from the Room mirror and refreshed behind it.
     *
     * `pageSize` must equal the server's fixed 34 — Paging treats any page smaller than
     * its configured size as the end of the list, so a mismatch would stop paging after
     * the first load.
     */
    private companion object {
        const val KEY_TOP_TAGS = "browse_top_custom_tags"
    }

    /**
     * [query]'s characters, paged from the mirror. The server is asked with [query]'s server
     * side only; its phone-side minimums narrow the mirror's reading (CharacterDao.pagingSource).
     */
    @OptIn(ExperimentalPagingApi::class)
    fun browse(query: BrowseQuery): Flow<PagingData<Character>> = Pager(
        config = PagingConfig(
            pageSize = CharacterRemoteSource.PAGE_SIZE,
            // Two pages ahead: a full-screen desktop grid is twelve cards a row, and half a page
            // ahead was barely one row, so a quick scroll ran into cards not fetched yet.
            prefetchDistance = CharacterRemoteSource.PAGE_SIZE * 2,
            // A page landing re-reads the list from the database around where the reader is,
            // and only this window comes back loaded; every card outside it turns back into a
            // skeleton until it is read again (and then settles in again, as if new). A
            // full-screen grid with the rows built ahead of it holds about a hundred cards, so
            // three pages (102) left its edges flickering. Eight pages is one cheap local read.
            initialLoadSize = CharacterRemoteSource.PAGE_SIZE * 8,
            // Placeholders keep every character at its own position: a re-read that starts
            // part-way down the list would otherwise make its first row item 0 and slide every
            // card along the grid's rows whenever that start isn't a multiple of the row.
            enablePlaceholders = true,
        ),
        remoteMediator = CharacterRemoteMediator(query.server(), remote, db, ::keepTopCustomTags),
        pagingSourceFactory = {
            db.characterDao().pagingSource(query.server().cacheKey, query.minMessages, query.minTokens, query.proxyOnly)
        },
    ).flow.map { paging -> paging.map(CharacterEntity::toDomain) }

    /**
     * One page of [query] straight from Janitor, for the paged Home: its characters and
     * whether a page follows (a full page says there may be one; the server's total is only
     * a lower bound, so no page count is ever derived from it).
     */
    suspend fun page(query: BrowseQuery, page: Int): Pair<List<Character>, Boolean> {
        val dto = remote.browse(query, page)
        if (dto.topCustomTags.isNotEmpty()) keepTopCustomTags(dto.topCustomTags)
        val now = System.currentTimeMillis()
        val items = dto.data.mapIndexed { i, c -> c.toEntity(query.cacheKey, i, now).toDomain() }
        return items to (dto.data.size >= CharacterRemoteSource.PAGE_SIZE)
    }
}

/**
 * A card's blurb, worked out once per description. Cleaning it takes several regex passes,
 * and the mapping runs on the main thread each time a page lands and the list re-reads
 * itself; without this, every landing cleaned every card in the window again.
 */
private object Blurbs {
    /** What a blurb was made from, so a changed description is noticed without keeping it. */
    private class Entry(val length: Int, val name: String, val blurb: String)

    // Keyed by character id: the key used to be the whole description joined to the name,
    // a new string as long as the description (often thousands of characters), built and
    // hashed for every card on every re-read, only to find a short line.
    private val cache = object : LinkedHashMap<String, Entry>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 2000
    }

    fun of(id: String, description: String, name: String): String {
        synchronized(cache) { cache[id] }?.let { if (it.length == description.length && it.name == name) return it.blurb }
        val blurb = description.plainPreview(220).fillNames(user = null, char = name)
        synchronized(cache) { cache[id] = Entry(description.length, name, blurb) }
        return blurb
    }
}

internal fun CharacterEntity.toDomain() = Character(
    id = id,
    name = name,
    avatar = avatar,
    creatorName = creatorName,
    creatorVerified = creatorVerified,
    creatorPlusBadge = creatorPlusBadge,
    chatCount = chatCount,
    messageCount = messageCount,
    isNsfw = isNsfw,
    isImageNsfw = isImageNsfw,
    tagNames = tagNames,
    customTags = customTags,
    blurb = Blurbs.of(id, description, name),
    totalTokens = totalTokens,
    isProxyEnabled = isProxyEnabled,
    creatorColor = creatorColor,
)
