package com.cherry.butler.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.BitmapImage
import coil3.compose.AsyncImagePainter
import com.cherry.butler.ui.components.Glass
import com.cherry.butler.ui.components.softenPixels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

/** Soft copies already made, by picture: a card scrolled back to has its own at once. */
private val copies = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 400
}
private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * A picture's soft copy: a few dozen pixels of it, blurred, for a pane of glass to look
 * through (GlassPicture). Made from the picture the card has just loaded, off the main thread,
 * so nothing is fetched or decoded twice. [image] is null until it is made.
 */
@Stable
class SoftPicture internal constructor(private val key: String) {
    var image: ImageBitmap? by mutableStateOf(synchronized(copies) { copies[key] })
        private set
    private var asked = false

    /** The card's picture has loaded: make the copy from it, once. */
    fun take(state: AsyncImagePainter.State.Success) {
        if (image != null || asked) return
        val source = (state.result.image as? BitmapImage)?.bitmap ?: return
        asked = true
        work.launch {
            val made = runCatching { softCopy(source) }.getOrNull() ?: return@launch
            synchronized(copies) { copies[key] = made }
            image = made
        }
    }
}

/** The soft copy of the picture known by [key]; null for no picture. */
@Composable
fun rememberSoftPicture(key: String?): SoftPicture? = if (key == null) null else remember(key) { SoftPicture(key) }

/** The soft copy of [source]: brought down to a few dozen pixels, every pixel counted, and blurred. */
fun softCopy(source: Bitmap): ImageBitmap? {
    if (source.isClosed || source.width < 1 || source.height < 1) return null
    val w = Glass.LEAK_WIDTH
    val h = (w * source.height / source.width.toFloat()).roundToInt().coerceIn(1, w * 3)
    val surface = Surface.makeRasterN32Premul(w, h)
    val picture = Image.makeFromBitmap(source)
    surface.canvas.drawImageRect(
        picture,
        Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
        Rect.makeWH(w.toFloat(), h.toFloat()),
        FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
        null,
        true,
    )
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val small = Bitmap().apply { allocPixels(info) }
    surface.readPixels(small, 0, 0)
    picture.close()
    surface.close()
    val bytes = small.readPixels(info, w * 4, 0, 0) ?: return null
    val pixels = IntArray(w * h) { i ->
        val at = i * 4
        (0xFF shl 24) or ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or (bytes[at].toInt() and 0xFF)
    }
    softenPixels(pixels, w, h, Glass.LEAK_SOFT)
    for (i in pixels.indices) {
        val at = i * 4
        bytes[at] = pixels[i].toByte()
        bytes[at + 1] = (pixels[i] shr 8).toByte()
        bytes[at + 2] = (pixels[i] shr 16).toByte()
        bytes[at + 3] = 0xFF.toByte()
    }
    small.installPixels(info, bytes, w * 4)
    small.setImmutable()
    return small.asComposeImageBitmap()
}
