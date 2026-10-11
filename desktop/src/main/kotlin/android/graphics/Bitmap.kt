package android.graphics

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.OutputStream

/**
 * Android's Bitmap over Skia, the engine Compose Desktop already draws with, so WebP and
 * every format the phone decodes work here too. Pixels are ARGB ints like Android's.
 */
class Bitmap internal constructor(internal var skia: org.jetbrains.skia.Bitmap) {
    val width: Int get() = skia.width
    val height: Int get() = skia.height
    val config: Config get() = Config.ARGB_8888
    val isRecycled: Boolean get() = skia.isClosed

    fun recycle() { if (!skia.isClosed) skia.close() }

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val bytes = skia.readPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), width * 4, x, y)
            ?: return
        for (row in 0 until height) for (col in 0 until width) {
            val i = (row * width + col) * 4
            val b = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val r = bytes[i + 2].toInt() and 0xFF
            val a = bytes[i + 3].toInt() and 0xFF
            pixels[offset + row * stride + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val bytes = ByteArray(width * height * 4)
        for (row in 0 until height) for (col in 0 until width) {
            val p = pixels[offset + row * stride + col]
            val i = (row * width + col) * 4
            bytes[i] = (p and 0xFF).toByte()
            bytes[i + 1] = ((p shr 8) and 0xFF).toByte()
            bytes[i + 2] = ((p shr 16) and 0xFF).toByte()
            bytes[i + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        val patch = org.jetbrains.skia.Bitmap().apply {
            allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
            installPixels(bytes)
        }
        val surface = Surface.makeRaster(skia.imageInfo)
        surface.canvas.drawImage(Image.makeFromBitmap(skia), 0f, 0f)
        surface.canvas.drawImage(Image.makeFromBitmap(patch), x.toFloat(), y.toFloat())
        skia = org.jetbrains.skia.Bitmap.makeFromImage(surface.makeImageSnapshot())
    }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        val skiaFormat = when (format) {
            CompressFormat.JPEG -> EncodedImageFormat.JPEG
            CompressFormat.PNG -> EncodedImageFormat.PNG
            else -> EncodedImageFormat.WEBP
        }
        val data = Image.makeFromBitmap(skia).encodeToData(skiaFormat, quality) ?: return false
        stream.write(data.bytes)
        return true
    }

    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
    enum class Config { ARGB_8888, RGB_565, HARDWARE }

    companion object {
        @JvmStatic
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val surface = Surface.makeRasterN32Premul(dstWidth.coerceAtLeast(1), dstHeight.coerceAtLeast(1))
            surface.canvas.drawImageRect(
                Image.makeFromBitmap(src.skia),
                org.jetbrains.skia.Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                org.jetbrains.skia.Rect.makeWH(dstWidth.toFloat(), dstHeight.toFloat()),
                if (filter) SamplingMode.LINEAR else SamplingMode.DEFAULT,
                null,
                true,
            )
            return Bitmap(org.jetbrains.skia.Bitmap.makeFromImage(surface.makeImageSnapshot()))
        }

        /** The part of [src] at x, y, w, h, drawn through [m] (rotations and flips from EXIF). */
        @JvmStatic
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, filter: Boolean): Bitmap {
            val matrix = m ?: Matrix()
            val bounds = matrix.mapBounds(w.toFloat(), h.toFloat())
            val surface = Surface.makeRasterN32Premul(bounds.width.toInt().coerceAtLeast(1), bounds.height.toInt().coerceAtLeast(1))
            surface.canvas.translate(-bounds.left, -bounds.top)
            surface.canvas.concat(matrix.toSkia())
            surface.canvas.drawImageRect(
                Image.makeFromBitmap(src.skia),
                org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()),
                org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()),
                if (filter) SamplingMode.LINEAR else SamplingMode.DEFAULT,
                null,
                true,
            )
            return Bitmap(org.jetbrains.skia.Bitmap.makeFromImage(surface.makeImageSnapshot()))
        }

        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val b = org.jetbrains.skia.Bitmap()
            b.allocN32Pixels(width, height)
            return Bitmap(b)
        }

        internal fun decode(bytes: ByteArray): Bitmap? = runCatching {
            Bitmap(org.jetbrains.skia.Bitmap.makeFromImage(Image.makeFromEncoded(bytes)))
        }.getOrNull()
    }
}
