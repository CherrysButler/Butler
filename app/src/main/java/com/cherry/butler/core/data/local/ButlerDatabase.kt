package com.cherry.butler.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Tracks how far the mirror has been paged for a given query, so a scroll position
 * survives process death instead of restarting from page 1.
 */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val queryKey: String,
    val nextPage: Int?,
    /** False once the server returns a short page — there is no reliable total to use. */
    val hasMore: Boolean,
    val lastRefreshedAt: Long,
)

/**
 * String lists are stored as a delimited blob rather than a join table: they are only
 * ever read back whole for a list card, never queried across, so a table would buy
 * nothing and cost a join on every page.
 */
class StringListConverter {
    @TypeConverter
    fun fromList(value: List<String>?): String = value.orEmpty().joinToString(DELIMITER)

    @TypeConverter
    fun toList(value: String?): List<String> =
        value?.takeIf { it.isNotEmpty() }?.split(DELIMITER) ?: emptyList()

    private companion object {
        /** Unit Separator — cannot occur in a tag name or slug. */
        const val DELIMITER = ""
    }
}

/**
 * Version history — every bump ships a migration, because from v2 on this database holds
 * user-authored text (the outbox) and may never be dropped on upgrade.
 *
 * 1. character mirror + remote keys
 * 2. chats, messages, send_jobs (additive; auto-migrated)
 * 3. chats.personaName (additive; auto-migrated)
 */
@Database(
    entities = [
        CharacterEntity::class,
        RemoteKeyEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        SendJobEntity::class,
        GroupMarkEntity::class,
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9)],
)
@TypeConverters(StringListConverter::class)
abstract class ButlerDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun sendJobDao(): SendJobDao
    abstract fun groupMarkDao(): GroupMarkDao

    companion object {
        const val NAME = "butler.db"
    }
}
