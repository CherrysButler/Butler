package com.cherry.butler.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A character as mirrored to disk.
 *
 * The mirror exists so the browse screen renders instantly from the last known state
 * instead of showing a spinner — that is the whole point of Butler over the official
 * client. It is a cache of the *server's ordering* as much as of the characters, hence
 * [queryKey] and [position]: the same character appears under several queries with a
 * different position in each.
 */
@Entity(
    tableName = "characters",
    primaryKeys = ["queryKey", "id"],
    indices = [Index(value = ["queryKey", "position"])],
)
data class CharacterEntity(
    /** [com.cherry.butler.core.model.BrowseQuery.cacheKey] this row was fetched under. */
    val queryKey: String,
    val id: String,
    /** Index within [queryKey]'s result order, so the mirror replays the server's sort. */
    val position: Int,

    val name: String,
    val description: String,
    val avatar: String?,

    val creatorId: String,
    val creatorName: String,
    val creatorVerified: Boolean,
    val creatorPlusBadge: Boolean,

    val chatCount: Long,
    val messageCount: Long,
    val publicChatCount: Int,
    val totalTokens: Int,

    val isNsfw: Boolean,
    val isImageNsfw: Boolean,

    /** Denormalised for list rendering — the browse card only needs the labels. */
    val tagSlugs: List<String>,
    val tagNames: List<String>,
    val customTags: List<String>,

    val createdAt: String?,
    val updatedAt: String?,

    /** When this row was mirrored, for staleness decisions. */
    val cachedAt: Long,

    /** Whether the creator allows proxies: the filter sheet's Proxy switch reads it. */
    @ColumnInfo(defaultValue = "0") val isProxyEnabled: Boolean = false,
    /** The creator's chosen username colour (`creator_display_prefs.username_color`), e.g. `#b3ff8f`. */
    @ColumnInfo(defaultValue = "NULL") val creatorColor: String? = null,
)
