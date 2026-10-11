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
 * one of the built-ins ([BUILT_IN]) or a font the user added, copied into the app so it stays
 * when the original is moved. Nothing is downloaded: Butler talks only to Janitor.
 * Keys: "atkinson", "system", "serif", "mono", ..., "family:<name>", or "file:<name>".
 *
 * An added font is a family of up to four files (regular, bold, italic, bold italic), each
 * file's weight and slant read from the file itself ([FontFaceReader]), so bold and italic
 * text use the real faces. A family is a folder under filesDir/fonts holding faces named by
 * what they are ("700-italic.ttf"). Before families, an added font was one file straight in
 * filesDir/fonts with a "file:" key; those still work, as a family of one regular face, and
 * become a real family when a face is added to them.
 */
@Singleton
class FontPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "fonts")

    /** Picked files wait here, read and checked, until the user says which family they join. */
    private val staging = File(context.cacheDir, "font-picks")

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

    /** One face of an added font: its file, and the weight and slant the file declares. */
    data class Face(val file: File, val weight: Int, val italic: Boolean) {
        val label: String get() = faceName(weight, italic)
    }

    /** A font the user added, by key and name, with its faces (regular first). */
    data class Added(val key: String, val name: String, val faces: List<Face>)

    private val _added = MutableStateFlow(listAdded())

    /** The fonts the user added, by name. */
    val added: StateFlow<List<Added>> = _added.asStateFlow()

    fun setChat(key: String) {
        _chat.value = key
        prefs.edit().putString(KEY_CHAT, key).apply()
    }

    fun setApp(key: String) {
        _app.value = key
        prefs.edit().putString(KEY_APP, key).apply()
    }

    /** The faces behind an added font's key, empty when it isn't here (or is a built-in). */
    fun facesOf(key: String): List<Face> = when {
        key.startsWith(FAMILY) -> familyFaces(File(dir, key.removePrefix(FAMILY)))
        key.startsWith(FILE) -> File(dir, key.removePrefix(FILE)).takeIf { it.isFile }?.let { listOf(Face(it, 400, false)) }.orEmpty()
        else -> emptyList()
    }

    /**
     * The key to use for [key] on this device: itself when it is a built-in or here, the
     * family an old single-file font was moved into, or null when it isn't here at all.
     */
    fun present(key: String): String? = when {
        BUILT_IN.any { it.first == key } -> key
        facesOf(key).isNotEmpty() -> key
        else -> prefs.getString(KEY_MOVED + key, null)?.takeIf { facesOf(it).isNotEmpty() }
    }

    /** Files picked and read, waiting in [staging] for where they go. */
    data class Picked(
        val faces: List<Face>,
        /** The family the files name, else a name worked out from the first file's. */
        val family: String,
        /** Files that weren't fonts Butler can read, or more faces than a family holds. */
        val refused: List<String>,
    )

    /**
     * Copies picked font files in and reads each one's face. Refuses anything that isn't a
     * readable .ttf or .otf, so a wrong file never turns the app's text into boxes. Two files
     * of the same face keep the later one; past [MAX_FACES] the rest are refused.
     */
    suspend fun pick(uris: List<Uri>): Picked = withContext(Dispatchers.IO) {
        staging.deleteRecursively()
        staging.mkdirs()
        val faces = LinkedHashMap<Pair<Int, Boolean>, Face>()
        val refused = mutableListOf<String>()
        var family: String? = null
        var firstBase: String? = null
        uris.forEachIndexed { i, uri ->
            val shown = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "font.ttf"
            val ext = shown.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf") } ?: "ttf"
            val target = File(staging, "$i.$ext")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            }.getOrNull() != null
            val info = if (copied) FontFaceReader.read(target.readBytes()) else null
            val readable = info != null && runCatching { Typeface.Builder(target).build() }.getOrNull() != null
            val slot = info?.let { it.weight to it.italic }
            if (info == null || slot == null || !readable || (slot !in faces && faces.size >= MAX_FACES)) {
                target.delete()
                refused += shown
                return@forEachIndexed
            }
            faces.remove(slot)?.file?.delete()
            faces[slot] = Face(target, info.weight, info.italic)
            if (family == null) family = info.family
            if (firstBase == null) firstBase = shown.substringBeforeLast('.')
        }
        Picked(
            faces = faces.values.sortedWith(FACE_ORDER),
            family = clean(family ?: firstBase?.let(::withoutStyle) ?: "Font"),
            refused = refused,
        )
    }

    /** What [place] did: the family's key, the faces it took, and those that didn't fit. */
    data class Placed(val key: String, val name: String, val added: List<Face>, val refused: Int)

    /**
     * Puts [picked] into a family: [into] (an added font's key), or a new one named after the
     * files when null. A face the family already has is replaced; a family holds [MAX_FACES]
     * at most, and faces past that are left out. An old single-file font becomes a family
     * first, and anything set to it follows.
     */
    suspend fun place(picked: Picked, into: String?): Placed = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val home = when {
            into == null -> File(dir, unique(picked.family))
            into.startsWith(FILE) -> adopt(into)
            else -> File(dir, into.removePrefix(FAMILY))
        }
        home.mkdirs()
        val key = FAMILY + home.name
        val added = mutableListOf<Face>()
        var refused = 0
        for (face in picked.faces) {
            val have = familyFaces(home)
            val same = have.filter { it.weight == face.weight && it.italic == face.italic }
            if (same.isEmpty() && have.size >= MAX_FACES) {
                refused++
                continue
            }
            same.forEach { it.file.delete() }
            val target = File(home, faceFile(face.weight, face.italic, face.file.extension))
            face.file.copyTo(target, overwrite = true)
            face.file.delete()
            added += face.copy(file = target)
        }
        if (familyFaces(home).isEmpty()) home.deleteRecursively()
        staging.deleteRecursively()
        _added.value = listAdded()
        Placed(key, home.name, added, refused)
    }

    /** Lets go of picked files that went nowhere. */
    fun drop(picked: Picked) {
        picked.faces.forEach { it.file.delete() }
    }

    /** Removes an added font and its files; anything set to it goes back to the default. */
    fun remove(key: String) {
        when {
            key.startsWith(FAMILY) -> File(dir, key.removePrefix(FAMILY)).deleteRecursively()
            key.startsWith(FILE) -> File(dir, key.removePrefix(FILE)).delete()
        }
        if (_chat.value == key) setChat(DEFAULT_CHAT)
        if (_app.value == key) setApp(DEFAULT_APP)
        _added.value = listAdded()
    }

    /**
     * Moves an old single-file font into a family folder of its own, its face read from the
     * file. The old key is remembered, so a look profile saved with it still finds the font.
     */
    private fun adopt(fileKey: String): File {
        val file = File(dir, fileKey.removePrefix(FILE))
        val info = runCatching { FontFaceReader.read(file.readBytes()) }.getOrNull()
        val home = File(dir, unique(clean(info?.family ?: withoutStyle(file.nameWithoutExtension))))
        home.mkdirs()
        if (file.isFile) {
            file.copyTo(File(home, faceFile(info?.weight ?: 400, info?.italic ?: false, file.extension)), overwrite = true)
            file.delete()
        }
        val key = FAMILY + home.name
        prefs.edit().putString(KEY_MOVED + fileKey, key).apply()
        if (_chat.value == fileKey) setChat(key)
        if (_app.value == fileKey) setApp(key)
        return home
    }

    private fun familyFaces(folder: File): List<Face> =
        folder.listFiles()?.mapNotNull { f ->
            val m = FACE_FILE.matchEntire(f.name) ?: return@mapNotNull null
            Face(f, m.groupValues[1].toInt().coerceIn(1, 1000), m.groupValues[2].isNotEmpty())
        }?.sortedWith(FACE_ORDER).orEmpty()

    private fun listAdded(): List<Added> = dir.listFiles().orEmpty().mapNotNull { f ->
        when {
            f.isDirectory -> familyFaces(f).takeIf { it.isNotEmpty() }?.let { Added(FAMILY + f.name, f.name, it) }
            f.isFile -> Added(FILE + f.name, f.nameWithoutExtension, listOf(Face(f, 400, false)))
            else -> null
        }
    }.sortedBy { it.name.lowercase() }

    /** A folder name not yet taken: "Inter", then "Inter 2", ... */
    private fun unique(name: String): String {
        var candidate = name
        var n = 2
        while (File(dir, candidate).exists()) candidate = "$name ${n++}"
        return candidate
    }

    companion object {
        const val FILE = "file:"
        const val FAMILY = "family:"
        const val DEFAULT_CHAT = "atkinson"
        const val DEFAULT_APP = "system"

        /** Regular, bold, italic and bold italic. */
        const val MAX_FACES = 4

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
            BUILT_IN.firstOrNull { it.first == key }?.second
                ?: key.takeIf { it.startsWith(FAMILY) }?.removePrefix(FAMILY)
                ?: key.removePrefix(FILE).substringBeforeLast('.')

        /** A face by its weight and slant, as a font menu names it: "Bold italic". */
        fun faceName(weight: Int, italic: Boolean): String {
            val w = when ((weight + 50) / 100) {
                0, 1 -> "Thin"
                2 -> "Extra light"
                3 -> "Light"
                4 -> "Regular"
                5 -> "Medium"
                6 -> "Semibold"
                7 -> "Bold"
                8 -> "Extra bold"
                else -> "Black"
            }
            return when {
                !italic -> w
                w == "Regular" -> "Italic"
                else -> "$w italic"
            }
        }

        /** Upright before italic, lighter before heavier: Regular, Bold, Italic, Bold italic. */
        private val FACE_ORDER = compareBy<Face>({ it.italic }, { it.weight })
        private val FACE_FILE = Regex("""(\d{1,4})(-italic)?\.(ttf|otf)""")
        private fun faceFile(weight: Int, italic: Boolean, ext: String) =
            "$weight${if (italic) "-italic" else ""}.${ext.lowercase().takeIf { it == "otf" } ?: "ttf"}"

        /** A family name safe as a folder name. */
        private fun clean(name: String): String =
            name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifEmpty { "Font" }

        /** "Inter-BoldItalic" as "Inter": a file name without the face words at its end. */
        private fun withoutStyle(base: String): String =
            base.replace(STYLE_SUFFIX, "").ifEmpty { base }

        private val STYLE_SUFFIX = Regex(
            "(?i)([\\s_-]*(thin|hairline|extralight|ultralight|light|book|regular|normal|medium|semibold|demibold|bold|extrabold|ultrabold|black|heavy|italic|oblique|variable|vf))+$",
        )

        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.75f
        private const val KEY_APP_SCALE = "font_app_scale"
        private const val KEY_CHAT = "font_chat"
        private const val KEY_APP = "font_app"

        /** Prefix of the remembered move of an old "file:" key into its family. */
        private const val KEY_MOVED = "font_moved:"
    }
}
