package android.database

import java.io.Closeable

class Cursor(private val columns: List<String>, private val rows: List<List<String?>>) : Closeable {
    private var at = -1
    fun moveToFirst(): Boolean { at = 0; return rows.isNotEmpty() }
    fun moveToNext(): Boolean { at++; return at < rows.size }
    fun getColumnIndex(name: String): Int = columns.indexOf(name)
    fun getColumnIndexOrThrow(name: String): Int = getColumnIndex(name).also { require(it >= 0) { "no column $name" } }
    fun getString(index: Int): String? = rows.getOrNull(at)?.getOrNull(index)
    fun getLong(index: Int): Long = getString(index)?.toLongOrNull() ?: 0L
    fun isNull(index: Int): Boolean = getString(index) == null
    val count: Int get() = rows.size
    override fun close() = Unit
}
