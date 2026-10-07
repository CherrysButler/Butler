package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.feature.chat.ThinkingWords
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small chat habits kept on the phone: whether the keyboard goes down when a message is
 * sent, and the thinking words (what the thinking line says while a reply is on its way).
 *
 * The words come in lists: Butler's own, which can be switched off, and any number of the
 * user's, each on or off. Every list that is on goes into one pool; with none on, Butler's
 * is used anyway, so the line never has nothing to say.
 */
@Singleton
class ChatPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /** One of the user's lists. */
    @Serializable
    data class WordList(val id: String, val name: String, val words: List<String>, val on: Boolean = true)

    private val _closeKeyboardOnSend = MutableStateFlow(prefs.getBoolean(KEY_CLOSE_KEYBOARD, true))
    val closeKeyboardOnSend: StateFlow<Boolean> = _closeKeyboardOnSend.asStateFlow()

    private val _butlerWordsOn = MutableStateFlow(prefs.getBoolean(KEY_BUTLER_ON, true))
    /** Whether Butler's own words are in the pool. */
    val butlerWordsOn: StateFlow<Boolean> = _butlerWordsOn.asStateFlow()

    private val _lists = MutableStateFlow(load())
    /** The user's lists, in the order made. */
    val thinkingLists: StateFlow<List<WordList>> = _lists.asStateFlow()

    init {
        apply()
    }

    fun setCloseKeyboardOnSend(on: Boolean) {
        _closeKeyboardOnSend.value = on
        prefs.edit().putBoolean(KEY_CLOSE_KEYBOARD, on).apply()
    }

    fun setButlerWordsOn(on: Boolean) {
        _butlerWordsOn.value = on
        prefs.edit().putBoolean(KEY_BUTLER_ON, on).apply()
        apply()
    }

    /**
     * Saves a list: a new one when [id] is null. [text] is one word per line or comma; blanks
     * and repeats are dropped. A list left with no words is removed.
     */
    fun saveList(id: String?, name: String, text: String) {
        val words = parse(text)
        val lists = _lists.value.toMutableList()
        val at = lists.indexOfFirst { it.id == id }
        when {
            words.isEmpty() -> if (at >= 0) lists.removeAt(at)
            at >= 0 -> lists[at] = lists[at].copy(name = name.trim().ifEmpty { "My words" }, words = words)
            else -> lists += WordList(id = UUID.randomUUID().toString(), name = name.trim().ifEmpty { "My words" }, words = words)
        }
        store(lists)
    }

    fun setListOn(id: String, on: Boolean) = store(_lists.value.map { if (it.id == id) it.copy(on = on) else it })

    fun deleteList(id: String) = store(_lists.value.filter { it.id != id })

    private fun store(lists: List<WordList>) {
        _lists.value = lists
        prefs.edit().putString(KEY_LISTS, json.encodeToString(ListSerializer(WordList.serializer()), lists)).apply()
        apply()
    }

    /** Hands the pool to the thinking line. */
    private fun apply() {
        ThinkingWords.butlerOn = _butlerWordsOn.value
        ThinkingWords.custom = _lists.value.filter { it.on }.flatMap { it.words }.distinct()
    }

    private fun load(): List<WordList> {
        val saved = prefs.getString(KEY_LISTS, null)
        if (saved != null) {
            return runCatching { json.decodeFromString(ListSerializer(WordList.serializer()), saved) }.getOrDefault(emptyList())
        }
        // Before lists, one plain text of words that replaced Butler's: it becomes the first list.
        val old = parse(prefs.getString(KEY_OLD_WORDS, null).orEmpty())
        if (old.isEmpty()) return emptyList()
        val lists = listOf(WordList(id = UUID.randomUUID().toString(), name = "My words", words = old))
        prefs.edit()
            .putString(KEY_LISTS, json.encodeToString(ListSerializer(WordList.serializer()), lists))
            .remove(KEY_OLD_WORDS)
            .apply()
        return lists
    }

    private fun parse(text: String): List<String> =
        text.split('\n', ',').map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(300)

    private companion object {
        const val KEY_CLOSE_KEYBOARD = "close_keyboard_on_send"
        const val KEY_BUTLER_ON = "thinking_words_butler"
        const val KEY_LISTS = "thinking_word_lists"
        const val KEY_OLD_WORDS = "thinking_words"
    }
}
