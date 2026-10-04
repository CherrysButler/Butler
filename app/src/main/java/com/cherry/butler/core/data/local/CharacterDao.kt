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
     */
    @Query("SELECT * FROM characters WHERE queryKey = :queryKey ORDER BY position ASC")
    fun pagingSource(queryKey: String): PagingSource<Int, CharacterEntity>

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
