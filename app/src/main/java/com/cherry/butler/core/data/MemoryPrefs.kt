package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two memory switches. Both are client-owned on Janitor too: the website's toggles
 * fire no network call (docs/JANITOR_API.md §19.3), so they live on the phone.
 *
 * - [replacesHistory]: sent as `memoryReplacesHistory: true` on every generation of a
 *   chat that has a summary, so the server swaps the summarized messages for the summary.
 * - [autoSummarize]: Butler refreshes the summary on its own once [AUTO_EVERY] new
 *   messages have piled up past it. Only meaningful with [replacesHistory] on.
 * - [compact] (Compact mode, a Butler special): once a reply finds the chat's context
 *   [compactAt] percent full, Butler folds all but the last [compactKeep] messages into the
 *   summary, from the summary before. Needs [replacesHistory], and takes over from
 *   [autoSummarize] while it is on.
 */
@Singleton
class MemoryPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _replacesHistory = MutableStateFlow(prefs.getBoolean(KEY_REPLACES, false))
    val replacesHistory: StateFlow<Boolean> = _replacesHistory.asStateFlow()

    private val _autoSummarize = MutableStateFlow(prefs.getBoolean(KEY_AUTO, false))
    val autoSummarize: StateFlow<Boolean> = _autoSummarize.asStateFlow()

    fun setReplacesHistory(on: Boolean) {
        _replacesHistory.value = on
        prefs.edit().putBoolean(KEY_REPLACES, on).apply()
    }

    fun setAutoSummarize(on: Boolean) {
        _autoSummarize.value = on
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
    }

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
        /** Messages past the summary before Butler summarizes again on its own. */
        const val AUTO_EVERY = 30
        const val DEFAULT_KEEP = 12
        const val DEFAULT_AT = 85
        val KEEP_RANGE = 2..200
        val AT_RANGE = 50..95
        private const val KEY_REPLACES = "memory_replaces_history"
        private const val KEY_AUTO = "memory_auto_summarize"
        private const val KEY_COMPACT = "memory_compact"
        private const val KEY_COMPACT_KEEP = "memory_compact_keep"
        private const val KEY_COMPACT_AT = "memory_compact_at"
    }
}
