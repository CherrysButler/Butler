package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rich typing: keys for "speech", *action* and **bold** over the message box, the marks
 * drawn as you type. Kept on the phone. [defaultMark] is what typing opens on its own,
 * by its key ("action", "speech", "bold"), or null for plain.
 */
@Singleton
class RichTypingPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ON, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _defaultMark = MutableStateFlow(
        if (prefs.contains(KEY_DEFAULT)) prefs.getString(KEY_DEFAULT, null)?.takeIf { it.isNotEmpty() } else "action",
    )
    val defaultMark: StateFlow<String?> = _defaultMark.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ON, on).apply()
    }

    fun setDefaultMark(key: String?) {
        _defaultMark.value = key
        prefs.edit().putString(KEY_DEFAULT, key.orEmpty()).apply()
    }

    private companion object {
        const val KEY_ON = "rich_typing"
        const val KEY_DEFAULT = "rich_typing_default"
    }
}
