package com.cherry.butler.core.auth

import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking

/**
 * clearAllTables() is Android-only in Room; sign-out on the desktop empties every table the
 * same way, on the writer connection.
 */
internal fun RoomDatabase.clearAllTables() = runBlocking {
    useWriterConnection { transactor ->
        transactor.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')") { st ->
            val names = mutableListOf<String>()
            while (st.step()) names += st.getText(0)
            names
        }.forEach { table -> transactor.usePrepared("DELETE FROM `$table`") { it.step() } }
    }
}
