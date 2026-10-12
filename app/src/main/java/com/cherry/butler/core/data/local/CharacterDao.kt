package com.cherry.butler.core.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    /**
     * Ordered by [CharacterEntity.position] so the mirror replays the server's sort
     * rather than an arbitrary disk order — "popular" has to still look popular offline.
     *
     * The phone-side minimums (Janitor's app applies them on the phone; the server ignores
     * them) are applied here, in the query, so the list's length is what it holds. Filtering
     * the pages after they were read left the count changing as the read window moved, and
     * every card after the change moved with it: the list jumped while it loaded.
     */
    @Query(
        """SELECT * FROM characters WHERE queryKey = :queryKey
           AND messageCount >= :minMessages AND totalTokens >= :minTokens AND (:proxyOnly = 0 OR isProxyEnabled = 1)
           ORDER BY position ASC""",
    )
    fun pagingSource(queryKey: String, minMessages: Long, minTokens: Int, proxyOnly: Boolean): PagingSource<Int, CharacterEntity>

    /** Which of [ids] this query already holds. */
    @Query("SELECT id FROM characters WHERE queryKey = :queryKey AND id IN (:ids)")
    suspend fun heldIds(queryKey: String, ids: List<String>): List<String>

    /** The last place taken in this query's order, null when it holds nothing. */
    @Query("SELECT MAX(position) FROM characters WHERE queryKey = :queryKey")
    suspend fun lastPosition(queryKey: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(characters: List<CharacterEntity>)

    /**
     * Any mirrored row for this character, whichever browse query fetched it. The detail
     * screen paints from this instantly and refreshes the full object behind it.
     */
    @Query("SELECT * FROM characters WHERE id = :characterId ORDER BY cachedAt DESC LIMIT 1")
    fun observeAny(characterId: String): Flow<CharacterEntity?>

    @Query("DELETE FROM characters WHERE queryKey = :queryKey")
    suspend fun clearQuery(queryKey: String)

    @Query("SELECT COUNT(*) FROM characters WHERE queryKey = :queryKey")
    suspend fun countFor(queryKey: String): Int

    @Query("SELECT MIN(cachedAt) FROM characters WHERE queryKey = :queryKey")
    suspend fun oldestCachedAt(queryKey: String): Long?

    /**
     * Drops every query partition except the one in use. Without this the mirror grows
     * without bound — each filter combination the user tries leaves its own copy behind.
     */
    @Query("DELETE FROM characters WHERE queryKey != :keepQueryKey")
    suspend fun clearAllExcept(keepQueryKey: String)

    @Transaction
    suspend fun replaceQuery(queryKey: String, characters: List<CharacterEntity>) {
        clearQuery(queryKey)
        upsertAll(characters)
    }
}

@Dao
interface RemoteKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: RemoteKeyEntity)

    @Query("SELECT * FROM remote_keys WHERE queryKey = :queryKey")
    suspend fun get(queryKey: String): RemoteKeyEntity?

    @Query("DELETE FROM remote_keys WHERE queryKey = :queryKey")
    suspend fun clear(queryKey: String)
}
