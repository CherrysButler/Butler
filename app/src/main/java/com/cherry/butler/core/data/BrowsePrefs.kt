package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How Home lists characters: an endless scroll (Janitor's way) or one page at a time. */
@Singleton
class BrowsePrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _paged = MutableStateFlow(prefs.getBoolean(KEY_PAGED, false))
    val paged: StateFlow<Boolean> = _paged.asStateFlow()

    fun setPaged(on: Boolean) {
        _paged.value = on
        prefs.edit().putBoolean(KEY_PAGED, on).apply()
    }

    private companion object {
        const val KEY_PAGED = "browse_paged"
    }
}
