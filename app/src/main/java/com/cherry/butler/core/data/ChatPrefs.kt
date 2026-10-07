package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.feature.chat.ThinkingWords
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small chat habits kept on the phone: whether the keyboard goes down when a message is
 * sent, and the user's own thinking words (what the thinking line says while a reply is
 * on its way; empty means Butler's own list).
 */
@Singleton
class ChatPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _closeKeyboardOnSend = MutableStateFlow(prefs.getBoolean(KEY_CLOSE_KEYBOARD, true))
    val closeKeyboardOnSend: StateFlow<Boolean> = _closeKeyboardOnSend.asStateFlow()

    private val _thinkingWords = MutableStateFlow(parse(prefs.getString(KEY_THINKING, null).orEmpty()))
    /** The user's own words, in their order; empty for Butler's. */
    val thinkingWords: StateFlow<List<String>> = _thinkingWords.asStateFlow()

    init {
        ThinkingWords.custom = _thinkingWords.value
    }

    fun setCloseKeyboardOnSend(on: Boolean) {
        _closeKeyboardOnSend.value = on
        prefs.edit().putBoolean(KEY_CLOSE_KEYBOARD, on).apply()
    }

    /** One word per line or comma; blanks and repeats are dropped. Empty text: Butler's own. */
    fun setThinkingWords(text: String) {
        val words = parse(text)
        _thinkingWords.value = words
        ThinkingWords.custom = words
        prefs.edit().putString(KEY_THINKING, words.joinToString("\n")).apply()
    }

    private fun parse(text: String): List<String> =
        text.split('\n', ',').map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(200)

    private companion object {
        const val KEY_CLOSE_KEYBOARD = "close_keyboard_on_send"
        const val KEY_THINKING = "thinking_words"
    }
}
