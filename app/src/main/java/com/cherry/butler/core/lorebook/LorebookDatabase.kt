package com.cherry.butler.core.lorebook

import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "lorebooks")
data class LorebookRow(@PrimaryKey val id: String, val name: String, val scanDepth: Int, val favorite: Boolean) {
    fun toBook(entries: List<LoreEntry>) = Lorebook(id, name, entries, favorite, scanDepth)
}

@Entity(
    tableName = "lore_entries",
    primaryKeys = ["bookId", "position"],
    foreignKeys = [ForeignKey(
        entity = LorebookRow::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class LoreEntryRow(val bookId: String, val position: Int, @Embedded val entry: LoreEntry)

@Entity(
    tableName = "lore_bindings",
    primaryKeys = ["scope", "bookId"],
    indices = [Index("bookId")],
    foreignKeys = [ForeignKey(
        entity = LorebookRow::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE,
    )],
)
data class LoreBindingRow(val scope: String, val bookId: String, val enabled: Boolean)

@Entity(tableName = "lore_settings")
data class LoreSettingsRow(val contextCharLimit: Int, @PrimaryKey val id: Int = 1)

/** Only keyword lists are encoded; each entry and its matching options occupy their own row. */
class LoreKeywordConverter {
    @TypeConverter fun encode(keys: List<String>): String = Json.encodeToString(keys)
    @TypeConverter fun decode(keys: String): List<String> = Json.decodeFromString(keys)
}

/** Device library shared across accounts. Signing out clears the separate chat database. */
@Database(entities = [LorebookRow::class, LoreEntryRow::class, LoreBindingRow::class, LoreSettingsRow::class], version = 1)
@TypeConverters(LoreKeywordConverter::class, SecondaryKeywordRuleMapper::class)
abstract class LorebookDatabase : RoomDatabase() {
    abstract fun lorebooks(): LorebookDao

    companion object {
        const val NAME = "lorebooks.db"
    }
}
