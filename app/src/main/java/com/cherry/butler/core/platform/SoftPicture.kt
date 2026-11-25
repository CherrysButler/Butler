package com.cherry.butler.core.platform

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImagePainter
import com.cherry.butler.ui.components.Glass
import com.cherry.butler.ui.components.softenPixels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Soft copies already made, by picture: a card scrolled back to has its own at once. */
private val copies = LruCache<String, ImageBitmap>(400)
private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * A picture's soft copy: a few dozen pixels of it, blurred, for a pane of glass to look
 * through (GlassPicture). Made from the picture the card has just loaded, off the main thread,
 * so nothing is fetched or decoded twice. [image] is null until it is made.
 */
@Stable
class SoftPicture internal constructor(private val key: String) {
    var image: ImageBitmap? by mutableStateOf(copies.get(key))
        private set
    private var asked = false

    /** The card's picture has loaded: make the copy from it, once. */
    fun take(state: AsyncImagePainter.State.Success) {
        if (image != null || asked) return
        val source = (state.result.drawable as? BitmapDrawable)?.bitmap ?: return
        asked = true
        work.launch {
            val made = runCatching { soften(source) }.getOrNull() ?: return@launch
            copies.put(key, made)
            image = made
        }
    }
}

/** The soft copy of the picture known by [key]; null for no picture. */
@Composable
fun rememberSoftPicture(key: String?): SoftPicture? = if (key == null) null else remember(key) { SoftPicture(key) }

private fun soften(source: Bitmap): ImageBitmap? {
    if (source.isRecycled || source.width < 1 || source.height < 1) return null
    val w = Glass.LEAK_WIDTH
    val h = (w * source.height / source.width.toFloat()).roundToInt().coerceIn(1, w * 3)
    // A picture kept on the GPU is read back once; the rest is done on that copy.
    var at = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) ?: return null else source
    // By halves, so every pixel of the picture is in the copy, not one in a hundred of them.
    while (at.width / 2 >= w && at.height / 2 >= h) {
        val next = Bitmap.createScaledBitmap(at, at.width / 2, at.height / 2, true)
        if (at !== source && at !== next) at.recycle()
        at = next
    }
    val small = Bitmap.createScaledBitmap(at, w, h, true)
    if (at !== source && at !== small) at.recycle()
    val pixels = IntArray(w * h)
    small.getPixels(pixels, 0, w, 0, 0, w, h)
    if (small !== source) small.recycle()
    softenPixels(pixels, w, h, Glass.LEAK_SOFT)
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
