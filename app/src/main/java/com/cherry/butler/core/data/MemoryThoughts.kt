package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the model thought while it wrote a chat's memory (a summary or a compact), kept on this
 * phone so it can be read afterwards as a reply's thought can. Janitor keeps only the summary.
 *
 * Each thought is tied to the summary it produced: once the summary is rewritten anywhere else
 * (by hand, on the website) the thought no longer belongs to it and is not shown ([forSummary]).
 */
@Singleton
class MemoryThoughts @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("memory_thoughts", Context.MODE_PRIVATE)

    /** A thought, how long it took (when that was seen), and a fingerprint of the summary it led to. */
    data class Thought(val text: String, val ms: Long?, val of: Int)

    private val _all = MutableStateFlow(
        prefs.all.keys.mapNotNull { k -> k.removeSuffix(TEXT).takeIf { k.endsWith(TEXT) }?.toLongOrNull() }
            .associateWith { id -> read(id) }
            .filterValues { it != null }
            .mapValues { it.value!! },
    )

    fun forChat(chatId: Long): Flow<Thought?> = _all.map { it[chatId] }

    fun record(chatId: Long, summary: String, thinking: String, ms: Long?) {
        if (thinking.isBlank()) {
            forget(chatId)
            return
        }
        val thought = Thought(thinking, ms, fingerprint(summary))
        _all.value = _all.value + (chatId to thought)
        prefs.edit()
            .putString("$chatId$TEXT", thinking)
            .putLong("$chatId$MS", ms ?: -1L)
            .putInt("$chatId$OF", thought.of)
            .apply()
    }

    fun forget(chatId: Long) {
        if (chatId !in _all.value) return
        _all.value = _all.value - chatId
        prefs.edit().remove("$chatId$TEXT").remove("$chatId$MS").remove("$chatId$OF").apply()
    }

    fun clear() {
        _all.value = emptyMap()
        prefs.edit().clear().apply()
    }

    private fun read(chatId: Long): Thought? {
        val text = prefs.getString("$chatId$TEXT", null) ?: return null
        val ms = prefs.getLong("$chatId$MS", -1L).takeIf { it >= 0 }
        return Thought(text, ms, prefs.getInt("$chatId$OF", 0))
    }

    companion object {
        private const val TEXT = ".text"
        private const val MS = ".ms"
        private const val OF = ".of"

        fun fingerprint(summary: String?): Int = summary.orEmpty().trim().hashCode()

        /** [thought] if it was written for [summary] as it stands, else null. */
        fun forSummary(thought: Thought?, summary: String?): Thought? =
            thought?.takeIf { !summary.isNullOrBlank() && it.of == fingerprint(summary) }
    }
}
