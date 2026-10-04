package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.design.RpLook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The reader's text styling ([RpLook]), kept on the phone. */
@Singleton
class TextLookPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _look = MutableStateFlow(read())
    val look: StateFlow<RpLook> = _look.asStateFlow()

    fun update(change: (RpLook) -> RpLook) {
        val next = change(_look.value)
        _look.value = next
        prefs.edit().apply {
            putColor(K_NARRATION, next.narration)
            putColor(K_SPEECH, next.speech)
            putColor(K_ACTION, next.action)
            putColor(K_THOUGHT, next.thought)
            putColor(K_STRONG, next.strong)
            putBoolean(K_ITALIC, next.italicActions)
            putBoolean(K_QUOTES, next.showQuotes)
            putInt(K_SIZE, next.textSize)
        }.apply()
    }

    fun reset() = update { RpLook() }

    private fun read() = RpLook(
        narration = color(K_NARRATION),
        speech = color(K_SPEECH),
        action = color(K_ACTION),
        thought = color(K_THOUGHT),
        strong = color(K_STRONG),
        italicActions = prefs.getBoolean(K_ITALIC, true),
        showQuotes = prefs.getBoolean(K_QUOTES, true),
        textSize = prefs.getInt(K_SIZE, 16).coerceIn(12, 24),
    )

    private fun color(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private fun android.content.SharedPreferences.Editor.putColor(key: String, value: Long?) {
        if (value == null) remove(key) else putLong(key, value)
    }

    private companion object {
        const val K_NARRATION = "look_narration"
        const val K_SPEECH = "look_speech"
        const val K_ACTION = "look_action"
        const val K_THOUGHT = "look_thought"
        const val K_STRONG = "look_strong"
        const val K_ITALIC = "look_italic_actions"
        const val K_QUOTES = "look_show_quotes"
        const val K_SIZE = "look_text_size"
    }
}
