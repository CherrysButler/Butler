package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which look the app wears. Kept on the phone; Janitor has no such setting. */
@Singleton
class ThemePrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        AppTheme.entries.firstOrNull { it.key == prefs.getString(KEY, null) } ?: AppTheme.JanitorClassic,
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun set(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY, theme.key).apply()
    }

    /** How chat lines are laid out. Story by default; an earlier "bubbles" switch carries over. */
    private val _chatStyle = MutableStateFlow(
        ChatStyle.entries.firstOrNull { it.key == prefs.getString(KEY_CHAT_STYLE, null) }
            ?: if (prefs.getBoolean(KEY_BUBBLES, false)) ChatStyle.Bubbles else ChatStyle.Story,
    )
    val chatStyle: StateFlow<ChatStyle> = _chatStyle.asStateFlow()

    fun setChatStyle(style: ChatStyle) {
        _chatStyle.value = style
        prefs.edit().putString(KEY_CHAT_STYLE, style.key).remove(KEY_BUBBLES).apply()
    }

    private companion object {
        const val KEY = "app_theme"
        const val KEY_BUBBLES = "chat_bubbles"
        const val KEY_CHAT_STYLE = "chat_style"
    }
}
