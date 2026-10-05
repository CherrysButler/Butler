package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.design.CustomColors
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

    /** What the Custom look is made from. */
    private val _custom = MutableStateFlow(
        CustomColors(
            accent = prefs.getLong(KEY_ACCENT, CustomColors.Default.accent),
            ground = prefs.getLong(KEY_GROUND, CustomColors.Default.ground),
            overrides = runCatching {
                val o = org.json.JSONObject(prefs.getString(KEY_OVERRIDES, "{}") ?: "{}")
                o.keys().asSequence().associateWith { o.getLong(it) }
            }.getOrDefault(emptyMap()),
            corners = prefs.getFloat(KEY_CORNERS, 1f),
        ),
    )
    val custom: StateFlow<CustomColors> = _custom.asStateFlow()

    fun setCustom(colors: CustomColors) {
        _custom.value = colors
        val overrides = org.json.JSONObject().apply { colors.overrides.forEach { (k, v) -> put(k, v) } }
        prefs.edit()
            .putLong(KEY_ACCENT, colors.accent)
            .putLong(KEY_GROUND, colors.ground)
            .putString(KEY_OVERRIDES, overrides.toString())
            .putFloat(KEY_CORNERS, colors.corners)
            .apply()
    }

    /** The last colours picked on the wheel, newest first, to pick again with one tap. */
    private val _recent = MutableStateFlow(
        prefs.getString(KEY_RECENT, null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty(),
    )
    val recentColors: StateFlow<List<Long>> = _recent.asStateFlow()

    fun addRecent(argb: Long) {
        val next = (listOf(argb) + _recent.value.filter { it != argb }).take(RECENT_MAX)
        _recent.value = next
        prefs.edit().putString(KEY_RECENT, next.joinToString(",")).apply()
    }

    private companion object {
        const val KEY_ACCENT = "custom_accent"
        const val KEY_GROUND = "custom_ground"
        const val KEY_OVERRIDES = "custom_overrides"
        const val KEY_CORNERS = "custom_corners"
        const val KEY_RECENT = "recent_colors"
        const val RECENT_MAX = 10
        const val KEY = "app_theme"
        const val KEY_BUBBLES = "chat_bubbles"
        const val KEY_CHAT_STYLE = "chat_style"
    }
}
