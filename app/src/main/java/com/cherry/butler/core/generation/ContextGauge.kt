package com.cherry.butler.core.generation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How full each chat's context was on its last generation, as Janitor reports it
 * (`x-context-usage-percent` on generateAlpha, `contextUsagePercent` on the JLLM socket;
 * docs/JANITOR_API.md §24, §30). Kept per chat so the meter shows the moment the chat opens.
 */
@Singleton
class ContextGauge @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("context_gauge", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow<Map<Long, Int>>(
        prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> (v as? Int)?.let { id to it } } }.toMap(),
    )

    fun forChat(chatId: Long): Flow<Int?> = _all.map { it[chatId] }

    fun clear() {
        _all.value = emptyMap()
        prefs.edit().clear().apply()
    }

    fun record(chatId: Long, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        if (_all.value[chatId] == clamped) return
        _all.value = _all.value + (chatId to clamped)
        prefs.edit().putInt(chatId.toString(), clamped).apply()
    }
}
