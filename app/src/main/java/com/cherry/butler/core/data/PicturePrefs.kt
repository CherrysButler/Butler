package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which model looks at a picture put into a message and writes it into the line (beta).
 * Null means the one Write for me uses. Only a proxy can see pictures, so the choice is a
 * saved proxy preset with, optionally, another model than the one saved in it.
 */
@Singleton
class PicturePrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _describer = MutableStateFlow(decode(prefs.getString(KEY, null)))
    /** The preset that describes pictures, or null for Write for me's. */
    val describer: StateFlow<Writer.Proxy?> = _describer.asStateFlow()

    fun set(proxy: Writer.Proxy?) {
        _describer.value = proxy
        prefs.edit().putString(KEY, proxy?.let { "proxy:${it.id}" + (it.model?.let { m -> "$SEP$m" } ?: "") }).apply()
    }

    private fun decode(s: String?): Writer.Proxy? = s?.takeIf { it.startsWith("proxy:") }
        ?.removePrefix("proxy:")?.split(SEP, limit = 2)
        ?.let { parts -> Writer.Proxy(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() }) }

    private companion object {
        const val KEY = "picture_describer"
        const val SEP = "\u001F"
    }
}
