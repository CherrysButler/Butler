package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which model writes the user's own lines ("write for me", "enhance my draft"), apart from
 * the one that plays the character. Kept on the phone; Janitor has no such setting.
 */
sealed interface Writer {
    /** Whatever the chat itself uses. */
    data object SameAsChat : Writer
    data object Jllm : Writer
    /** A saved proxy (its legacy id), optionally with another [model] than the one saved in it. */
    data class Proxy(val id: String, val model: String? = null) : Writer
}

@Singleton
class WriterPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _writer = MutableStateFlow(decode(prefs.getString(KEY, null)))
    val writer: StateFlow<Writer> = _writer.asStateFlow()

    fun set(writer: Writer) {
        _writer.value = writer
        prefs.edit().putString(KEY, encode(writer)).apply()
    }

    private fun encode(w: Writer) = when (w) {
        Writer.SameAsChat -> "same"
        Writer.Jllm -> "jllm"
        is Writer.Proxy -> "proxy:${w.id}" + (w.model?.let { "$SEP$it" } ?: "")
    }

    private fun decode(s: String?): Writer = when {
        s == "jllm" -> Writer.Jllm
        s != null && s.startsWith("proxy:") -> s.removePrefix("proxy:").split(SEP, limit = 2).let { parts ->
            Writer.Proxy(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
        }
        else -> Writer.SameAsChat
    }

    private companion object {
        const val KEY = "writer"
        /** Unit Separator: cannot occur in an id or a model name. */
        const val SEP = "\u001F"
    }
}
