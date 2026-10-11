package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks saved under a name ([LookProfile]), to put one back on in a tap, and to share as a
 * file. Kept on the phone with the look itself. A profile names its fonts only when they are
 * built-ins or here; putting one on leaves a font that isn't here as it is.
 */
@Singleton
class LookProfiles @Inject constructor(
    @ApplicationContext context: Context,
    private val themes: ThemePrefs,
    private val textLook: TextLookPrefs,
    private val fonts: FontPrefs,
) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow(LookProfileFormat.decodeAll(prefs.getString(KEY, null)))
    val profiles: StateFlow<List<LookProfile>> = _profiles.asStateFlow()

    /** The look worn now, as a profile named [name]. */
    fun current(name: String = "", id: String = ""): LookProfile = LookProfile(
        id = id,
        name = name,
        theme = themes.theme.value,
        chatStyle = themes.chatStyle.value,
        thinkingLook = themes.thinkingLook.value,
        thoughtLook = themes.thoughtLook.value,
        custom = themes.custom.value,
        text = textLook.look.value,
        appFont = fonts.app.value.takeIf { fonts.present(it) != null },
        chatFont = fonts.chat.value.takeIf { fonts.present(it) != null },
    )

    fun saveCurrent(name: String): LookProfile =
        current(unique(name.trim().take(LookProfileFormat.NAME_MAX).ifEmpty { "My look" }), UUID.randomUUID().toString())
            .also { write(_profiles.value + it) }

    /** Puts a profile on. Returns the names of the fonts it names that aren't here, left as they were. */
    fun apply(id: String): List<String> {
        val p = _profiles.value.firstOrNull { it.id == id } ?: return emptyList()
        themes.set(p.theme)
        themes.setChatStyle(p.chatStyle)
        themes.setThinkingLook(p.thinkingLook)
        themes.setThoughtLook(p.thoughtLook)
        themes.setCustom(p.custom)
        textLook.update { p.text }
        val missing = mutableListOf<String>()
        p.appFont?.let { key -> fonts.present(key)?.let(fonts::setApp) ?: missing.add(FontPrefs.nameOf(key)) }
        p.chatFont?.let { key -> fonts.present(key)?.let(fonts::setChat) ?: missing.add(FontPrefs.nameOf(key)) }
        return missing.distinct()
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().take(LookProfileFormat.NAME_MAX).takeIf { it.isNotEmpty() } ?: return
        write(_profiles.value.map { if (it.id == id) it.copy(name = unique(clean, except = id)) else it })
    }

    fun delete(id: String) = write(_profiles.value.filterNot { it.id == id })

    /** A profile as a file: a name for it and its text. */
    fun export(id: String): Pair<String, String>? = _profiles.value.firstOrNull { it.id == id }?.let { p ->
        val file = p.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Look" }
        "$file.butler-look.json" to LookProfileFormat.toJson(p)
    }

    /**
     * Adds the profile in a shared file, as a new one beside the others (never put on by
     * itself). Throws [IllegalArgumentException] when the file isn't a Butler look.
     */
    fun import(text: String): LookProfile {
        val read = LookProfileFormat.parse(text)
        return read.copy(id = UUID.randomUUID().toString(), name = unique(read.name)).also { write(_profiles.value + it) }
    }

    /** A name not yet taken: "Night", then "Night 2", ... */
    private fun unique(name: String, except: String? = null): String {
        val taken = _profiles.value.filter { it.id != except }.map { it.name.lowercase() }.toSet()
        var candidate = name
        var n = 2
        while (candidate.lowercase() in taken) candidate = "$name ${n++}"
        return candidate
    }

    private fun write(next: List<LookProfile>) {
        _profiles.value = next
        prefs.edit().putString(KEY, LookProfileFormat.encodeAll(next)).apply()
    }

    private companion object {
        const val KEY = "look_profiles"
    }
}
