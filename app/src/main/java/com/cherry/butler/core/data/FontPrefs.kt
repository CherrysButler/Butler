package com.cherry.butler.core.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two fonts: the chat's (replies and your lines) and the app's (everything else). Each is
 * one of the built-ins ([BUILT_IN]) or a font file the user added, copied into the app so it
 * stays when the original is moved. Nothing is downloaded: Butler talks only to Janitor.
 * Keys: "atkinson", "system", "serif", "mono", or "file:<name>".
 */
@Singleton
class FontPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "fonts")

    private val _chat = MutableStateFlow(prefs.getString(KEY_CHAT, null) ?: DEFAULT_CHAT)
    val chat: StateFlow<String> = _chat.asStateFlow()

    private val _app = MutableStateFlow(prefs.getString(KEY_APP, null) ?: DEFAULT_APP)
    val app: StateFlow<String> = _app.asStateFlow()

    private val _appScale = MutableStateFlow(prefs.getFloat(KEY_APP_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE))

    /** How large the app's own text is, 1 = as designed. Chat text has its own size (RpLook). */
    val appScale: StateFlow<Float> = _appScale.asStateFlow()

    fun setAppScale(scale: Float) {
        val s = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _appScale.value = s
        prefs.edit().putFloat(KEY_APP_SCALE, s).apply()
    }

    private val _added = MutableStateFlow(listAdded())

    /** Font files the user added, by file name. */
    val added: StateFlow<List<String>> = _added.asStateFlow()

    fun setChat(key: String) {
        _chat.value = key
        prefs.edit().putString(KEY_CHAT, key).apply()
    }

    fun setApp(key: String) {
        _app.value = key
        prefs.edit().putString(KEY_APP, key).apply()
    }

    /** The file behind a "file:" key, if it is still there. */
    fun fileOf(key: String): File? =
        key.takeIf { it.startsWith(FILE) }?.let { File(dir, it.removePrefix(FILE)) }?.takeIf { it.isFile }

    /**
     * Copies a picked font file in and returns its key. Refuses anything Android can't read as
     * a font, so a wrong file never turns the app's text into boxes.
     */
    suspend fun add(uri: Uri): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "font.ttf"
        val ext = shown.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
        val base = shown.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Font" }.take(40)
        var name = "$base.$ext"
        var n = 2
        while (File(dir, name).exists()) name = "$base $n.$ext".also { n++ }
        val target = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("couldn't open that file")
        val readable = runCatching { Typeface.Builder(target).build() }.getOrNull()
        if (readable == null) {
            target.delete()
            error("that isn't a font file Android can read")
        }
        _added.value = listAdded()
        FILE + name
    }

    /** Removes an added font; anything set to it goes back to the default. */
    fun remove(name: String) {
        File(dir, name).delete()
        if (_chat.value == FILE + name) setChat(DEFAULT_CHAT)
        if (_app.value == FILE + name) setApp(DEFAULT_APP)
        _added.value = listAdded()
    }

    private fun listAdded(): List<String> = dir.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()

    companion object {
        const val FILE = "file:"
        const val DEFAULT_CHAT = "atkinson"
        const val DEFAULT_APP = "system"

        /** The built-ins, by key and name: the bundled reading face and the phone's own families. */
        val BUILT_IN = listOf(
            "atkinson" to "Atkinson Hyperlegible",
            "system" to "Phone's sans",
            "lora" to "Lora",
            "nunito" to "Nunito",
            "lexend" to "Lexend",
            "comic" to "Comic Neue",
            "serif" to "Phone's serif",
            "mono" to "Monospace",
        )

        /** A line on what each built-in is like, for the picker. */
        val BLURB = mapOf(
            "lora" to "A book serif",
            "nunito" to "Soft and rounded",
            "lexend" to "Made for easy reading",
            "comic" to "Casual, hand-lettered",
        )

        fun nameOf(key: String): String =
            BUILT_IN.firstOrNull { it.first == key }?.second ?: key.removePrefix(FILE).substringBeforeLast('.')

        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.3f
        private const val KEY_APP_SCALE = "font_app_scale"
        private const val KEY_CHAT = "font_chat"
        private const val KEY_APP = "font_app"
    }
}
