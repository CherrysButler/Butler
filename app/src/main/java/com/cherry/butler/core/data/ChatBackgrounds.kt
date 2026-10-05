package com.cherry.butler.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.cherry.butler.core.design.softBlur
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A saved picture and how it sits behind a chat. Blur is baked into its shown file. */
data class SavedBackground(
    val id: String,
    /** 0 (sharp) to 1. */
    val blur: Float,
    /** How much of the theme's ground lies over it, 0 to [ChatBackgrounds.MAX_DIM]. */
    val dim: Float,
    /** Whether it drifts a little as the phone tilts. */
    val parallax: Boolean,
    val createdAt: Long,
) {
    /** Changes whenever the shown file does, so image caches let go of the old one. */
    val shownKey: String get() = "bg-$id-$blur"
}

/** What a chat draws behind its log. */
data class ChatBackground(val file: File, val key: String, val dim: Float, val parallax: Boolean)

/** One chat's choice: the background for every chat, none at all, or one of its own. */
sealed interface ChatBackgroundChoice {
    data object Default : ChatBackgroundChoice
    data object None : ChatBackgroundChoice
    data class Own(val id: String) : ChatBackgroundChoice
}

/**
 * The backgrounds library: every picture the user has set is kept, with its own blur, dim
 * and tilt, to be used again or deleted. One may be the background for every chat; any chat
 * can pick another or none. Kept on the phone only; Janitor has no such thing.
 *
 * Each picture is stored twice, both scaled to about the screen: the original (so the blur
 * can be changed later) and the shown copy with the blur baked in (so a chat pays nothing
 * for it, on any Android version).
 */
@Singleton
class ChatBackgrounds @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(context.filesDir, "backgrounds")
    private val prefs = context.getSharedPreferences("butler_backgrounds", Context.MODE_PRIVATE)

    private val _library = MutableStateFlow(readLibrary())

    /** Newest first. */
    val library: StateFlow<List<SavedBackground>> = _library.asStateFlow()

    private val _globalId = MutableStateFlow(prefs.getString(KEY_GLOBAL, null))

    /** The background for every chat, if any. */
    val globalId: StateFlow<String?> = _globalId.asStateFlow()

    /** Bumped when a chat's choice changes. */
    private val choices = MutableStateFlow(0L)

    init {
        // Pictures from before the library (loose files in the folder) are dropped.
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }

    /** The picture as shown, blur baked in; for thumbnails as much as for chats. */
    fun shownFile(id: String) = File(dir, "$id/shown.jpg")

    private fun originalFile(id: String) = File(dir, "$id/original.jpg")

    fun forChat(chatId: Long): Flow<ChatBackground?> = choice(chatId).map { choice ->
        when (choice) {
            ChatBackgroundChoice.None -> null
            is ChatBackgroundChoice.Own -> drawable(choice.id) ?: _globalId.value?.let(::drawable)
            ChatBackgroundChoice.Default -> _globalId.value?.let(::drawable)
        }
    }

    fun choice(chatId: Long): Flow<ChatBackgroundChoice> =
        combine(choices, _globalId, _library) { _, _, _ -> choiceOf(chatId) }

    // ---- library ---------------------------------------------------------------------

    /** Adds [uri] to the library with its settings and returns its id. */
    suspend fun add(uri: Uri, blur: Float, dim: Float, parallax: Boolean): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(12)
        val folder = File(dir, id).apply { mkdirs() }
        val original = decode(uri, screenLongest())
        try {
            write(original, originalFile(id))
            val shown = original.softBlur(blur)
            write(shown, shownFile(id))
            if (shown !== original) shown.recycle()
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        } finally {
            original.recycle()
        }
        val item = SavedBackground(id, blur, dim.coerceIn(0f, MAX_DIM), parallax, System.currentTimeMillis())
        saveLibrary(listOf(item) + _library.value)
        id
    }

    /** New settings for a saved picture; the blur is baked again only when it changed. */
    suspend fun update(id: String, blur: Float, dim: Float, parallax: Boolean) = withContext(Dispatchers.IO) {
        val old = _library.value.firstOrNull { it.id == id } ?: return@withContext
        if (old.blur != blur) {
            val original = BitmapFactory.decodeFile(originalFile(id).path) ?: error("the picture is gone")
            try {
                val shown = original.softBlur(blur)
                write(shown, shownFile(id))
                if (shown !== original) shown.recycle()
            } finally {
                original.recycle()
            }
        }
        val next = old.copy(blur = blur, dim = dim.coerceIn(0f, MAX_DIM), parallax = parallax)
        saveLibrary(_library.value.map { if (it.id == id) next else it })
    }

    /** Deletes a saved picture. Where it was in use, a chat falls back to the default and the default to none. */
    fun delete(id: String) {
        File(dir, id).deleteRecursively()
        if (_globalId.value == id) setGlobal(null)
        val edit = prefs.edit()
        prefs.all.forEach { (k, v) -> if (k.startsWith(CHAT_PREFIX) && v == OWN + id) edit.remove(k) }
        edit.apply()
        saveLibrary(_library.value.filterNot { it.id == id })
        choices.value++
    }

    /** A saved picture's original, for the editor to preview another blur on. */
    suspend fun previewOf(id: String): Bitmap = withContext(Dispatchers.IO) {
        decodeFile(originalFile(id), PREVIEW_LONGEST) ?: error("the picture is gone")
    }

    /** A picked picture, before it is added. */
    suspend fun previewOf(uri: Uri): Bitmap = withContext(Dispatchers.IO) { decode(uri, PREVIEW_LONGEST) }

    // ---- choices ---------------------------------------------------------------------

    fun setGlobal(id: String?) {
        _globalId.value = id
        prefs.edit().apply { if (id == null) remove(KEY_GLOBAL) else putString(KEY_GLOBAL, id) }.apply()
    }

    fun setChat(chatId: Long, choice: ChatBackgroundChoice) {
        prefs.edit().apply {
            when (choice) {
                ChatBackgroundChoice.Default -> remove(CHAT_PREFIX + chatId)
                ChatBackgroundChoice.None -> putString(CHAT_PREFIX + chatId, NONE)
                is ChatBackgroundChoice.Own -> putString(CHAT_PREFIX + chatId, OWN + choice.id)
            }
        }.apply()
        choices.value++
    }

    private fun choiceOf(chatId: Long): ChatBackgroundChoice {
        val v = prefs.getString(CHAT_PREFIX + chatId, null) ?: return ChatBackgroundChoice.Default
        return when {
            v == NONE -> ChatBackgroundChoice.None
            v.startsWith(OWN) -> ChatBackgroundChoice.Own(v.removePrefix(OWN))
            else -> ChatBackgroundChoice.Default
        }
    }

    private fun drawable(id: String): ChatBackground? {
        val item = _library.value.firstOrNull { it.id == id } ?: return null
        val file = shownFile(id).takeIf { it.isFile && it.length() > 0 } ?: return null
        return ChatBackground(file, item.shownKey, item.dim, item.parallax)
    }

    // ---- storage ---------------------------------------------------------------------

    private fun readLibrary(): List<SavedBackground> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_LIBRARY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedBackground(
                id = o.getString("id"),
                blur = o.optDouble("blur", 0.0).toFloat(),
                dim = o.optDouble("dim", DEFAULT_DIM.toDouble()).toFloat(),
                parallax = o.optBoolean("parallax", true),
                createdAt = o.optLong("createdAt"),
            )
        }.filter { File(dir, "${it.id}/shown.jpg").isFile }
    }.getOrDefault(emptyList())

    private fun saveLibrary(items: List<SavedBackground>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("blur", it.blur.toDouble()).put("dim", it.dim.toDouble())
                    .put("parallax", it.parallax).put("createdAt", it.createdAt),
            )
        }
        prefs.edit().putString(KEY_LIBRARY, arr.toString()).apply()
        _library.value = items
    }

    private fun write(bitmap: Bitmap, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.path + ".tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun screenLongest(): Int {
        val m = context.resources.displayMetrics
        return maxOf(m.widthPixels, m.heightPixels).coerceAtLeast(1280)
    }

    private fun decodeFile(file: File, longest: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * The picture with its longest side near [longest]. ImageDecoder (Android 9+) also turns a
     * camera photo upright from its EXIF; older phones get BitmapFactory and the stored rotation.
     */
    private fun decode(uri: Uri, longest: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = longest.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("couldn't open the picture")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longest) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("couldn't read the picture")
    }

    companion object {
        const val DEFAULT_DIM = 0.55f
        const val MAX_DIM = 0.9f
        private const val PREVIEW_LONGEST = 1280
        private const val KEY_LIBRARY = "library"
        private const val KEY_GLOBAL = "global_id"
        private const val CHAT_PREFIX = "chat_"
        private const val NONE = "none"
        private const val OWN = "own:"
    }
}
