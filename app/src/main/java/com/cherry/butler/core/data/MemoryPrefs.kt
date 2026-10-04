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

    companion object {
        /** Messages past the summary before Butler summarizes again on its own. */
        const val AUTO_EVERY = 30
        private const val KEY_REPLACES = "memory_replaces_history"
        private const val KEY_AUTO = "memory_auto_summarize"
    }
}
