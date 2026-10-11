package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory's one switch, Compact mode (a Butler special), client-owned on Janitor too: the
 * website's memory toggles fire no network call (docs/JANITOR_API.md §19.3).
 *
 * Once a reply finds the chat's context [compactAt] percent full, Butler folds all but the
 * last [compactKeep] messages into the summary, from the summary before, and the summary
 * stands in for them ([replacesHistory], sent as `memoryReplacesHistory: true`). The old
 * pair of switches (the summary replacing old messages, and a summary every 30 messages)
 * are gone into it: the second summarized up to the newest message, so the next reply lost
 * even the last exchange word for word.
 */
@Singleton
class MemoryPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    /** Whether a chat's summary stands in for the messages it covers: while Compact mode is on. */
    val replacesHistory: StateFlow<Boolean> get() = compact

    private val _compact = MutableStateFlow(prefs.getBoolean(KEY_COMPACT, false))
    val compact: StateFlow<Boolean> = _compact.asStateFlow()

    private val _compactKeep = MutableStateFlow(prefs.getInt(KEY_COMPACT_KEEP, DEFAULT_KEEP).coerceIn(KEEP_RANGE))
    /** How many of the newest messages a compact leaves as they are. */
    val compactKeep: StateFlow<Int> = _compactKeep.asStateFlow()

    private val _compactAt = MutableStateFlow(prefs.getInt(KEY_COMPACT_AT, DEFAULT_AT).coerceIn(AT_RANGE))
    /** How full the context is, in percent, when a compact begins. */
    val compactAt: StateFlow<Int> = _compactAt.asStateFlow()

    fun setCompact(on: Boolean) {
        _compact.value = on
        prefs.edit().putBoolean(KEY_COMPACT, on).apply()
    }

    fun setCompactKeep(n: Int) {
        val kept = n.coerceIn(KEEP_RANGE)
        _compactKeep.value = kept
        prefs.edit().putInt(KEY_COMPACT_KEEP, kept).apply()
    }

    fun setCompactAt(percent: Int) {
        val kept = percent.coerceIn(AT_RANGE)
        _compactAt.value = kept
        prefs.edit().putInt(KEY_COMPACT_AT, kept).apply()
    }

    companion object {
        const val DEFAULT_KEEP = 12
        const val DEFAULT_AT = 85
        val KEEP_RANGE = 2..200
        val AT_RANGE = 50..95
        private const val KEY_COMPACT = "memory_compact"
        private const val KEY_COMPACT_KEEP = "memory_compact_keep"
        private const val KEY_COMPACT_AT = "memory_compact_at"
    }
}
