package com.cherry.butler.core.lorebook

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class LorebookSummary(
    val id: String, val name: String, val favorite: Boolean, val entryCount: Int,
    val selected: Boolean, val inherited: Boolean,
)

@Dao
abstract class LorebookDao {
    @Query("""
        SELECT b.id, b.name, b.favorite,
               (SELECT COUNT(*) FROM lore_entries WHERE bookId = b.id) AS entryCount,
               COALESCE(direct.enabled, persona.enabled, 0) AS selected,
               COALESCE(persona.enabled, 0) AS inherited
        FROM lorebooks b
        LEFT JOIN lore_bindings direct ON direct.bookId = b.id AND direct.scope = :scope
        LEFT JOIN lore_bindings persona ON persona.bookId = b.id AND persona.scope = :personaScope
        ORDER BY b.rowid
    """)
    abstract fun observeBooks(scope: String?, personaScope: String?): Flow<List<LorebookSummary>>

    @Query("SELECT * FROM lorebooks WHERE id = :id")
    protected abstract suspend fun metadata(id: String): LorebookRow?

    @Transaction
    open suspend fun book(id: String): Lorebook? =
        metadata(id)?.toBook(entries(id).map { it.entry })

    @Query("""
        SELECT b.* FROM lorebooks b
        WHERE b.id IN (
            SELECT bookId FROM lore_bindings WHERE scope IN (:chatScope, :personaScope) AND enabled = 1
            EXCEPT SELECT bookId FROM lore_bindings WHERE scope = :chatScope AND enabled = 0
        )
        ORDER BY b.rowid
    """)
    abstract suspend fun activeBooks(chatScope: String, personaScope: String?): List<LorebookRow>

    @Query("SELECT * FROM lore_entries WHERE bookId = :id AND enabled = 1 ORDER BY position")
    abstract suspend fun enabledEntries(id: String): List<LoreEntryRow>

    @Query("UPDATE lorebooks SET favorite = NOT favorite WHERE id = :id")
    abstract suspend fun toggleFavorite(id: String)

    @Query("DELETE FROM lorebooks WHERE id = :id")
    abstract suspend fun deleteBook(id: String)

    @Query("INSERT OR REPLACE INTO lore_bindings(scope, bookId, enabled) SELECT :scope, id, :enabled FROM lorebooks WHERE id = :id")
    abstract suspend fun setEnabled(scope: String, id: String, enabled: Boolean)

    @Query("DELETE FROM lore_bindings WHERE scope = :scope")
    abstract suspend fun clearBindings(scope: String)

    @Transaction
    open suspend fun setPersonaBooks(scope: String, ids: Set<String>) {
        clearBindings(scope)
        for (id in ids) setEnabled(scope, id, true)
    }

    @Query("SELECT contextCharLimit FROM lore_settings WHERE id = 1")
    abstract fun observeContextLimit(): Flow<Int?>

    @Query("SELECT contextCharLimit FROM lore_settings WHERE id = 1")
    abstract suspend fun contextLimit(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun setSettings(settings: LoreSettingsRow)

    @Insert
    protected abstract suspend fun insertBook(book: LorebookRow)

    @Query("UPDATE lorebooks SET name = :name, scanDepth = :depth WHERE id = :id")
    protected abstract suspend fun updateBook(id: String, name: String, depth: Int): Int

    @Query("SELECT * FROM lore_entries WHERE bookId = :id ORDER BY position")
    protected abstract suspend fun entries(id: String): List<LoreEntryRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putEntries(entries: List<LoreEntryRow>)

    @Query("DELETE FROM lore_entries WHERE bookId = :id AND position >= :size")
    protected abstract suspend fun trimEntries(id: String, size: Int)

    @Transaction
    open suspend fun save(book: Lorebook, isNew: Boolean, enableInScope: String?) {
        val previous = if (isNew) {
            insertBook(LorebookRow(book.id, book.name, book.scanDepth, book.favorite))
            emptyList()
        } else {
            check(updateBook(book.id, book.name, book.scanDepth) == 1) { "This lorebook was deleted. Close the editor and create a new one." }
            entries(book.id)
        }
        val changed = book.entries.mapIndexedNotNull { index, entry ->
            if (previous.getOrNull(index)?.entry == entry) null else LoreEntryRow(book.id, index, entry)
        }
        if (changed.isNotEmpty()) putEntries(changed)
        if (previous.size > book.entries.size) trimEntries(book.id, book.entries.size)
        if (isNew && enableInScope != null) setEnabled(enableInScope, book.id, true)
    }
}
