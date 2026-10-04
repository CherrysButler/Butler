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
) {

    /**
     * Paged characters for [query], served from the Room mirror and refreshed behind it.
     *
     * `pageSize` must equal the server's fixed 34 — Paging treats any page smaller than
     * its configured size as the end of the list, so a mismatch would stop paging after
     * the first load.
     */
    @OptIn(ExperimentalPagingApi::class)
    fun browse(query: BrowseQuery): Flow<PagingData<Character>> = Pager(
        config = PagingConfig(
            pageSize = CharacterRemoteSource.PAGE_SIZE,
            prefetchDistance = CharacterRemoteSource.PAGE_SIZE / 2,
            initialLoadSize = CharacterRemoteSource.PAGE_SIZE,
            enablePlaceholders = false,
        ),
        remoteMediator = CharacterRemoteMediator(query, remote, db),
        pagingSourceFactory = { db.characterDao().pagingSource(query.cacheKey) },
    ).flow.map { paging -> paging.map(CharacterEntity::toDomain) }
}

private fun CharacterEntity.toDomain() = Character(
    id = id,
    name = name,
    description = description,
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
    blurb = description.plainPreview(220).fillNames(user = null, char = name),
    totalTokens = totalTokens,
    isProxyEnabled = isProxyEnabled,
    creatorColor = creatorColor,
)
