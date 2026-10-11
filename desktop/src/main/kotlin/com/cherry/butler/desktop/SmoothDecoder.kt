package com.cherry.butler.desktop

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Scale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.SurfaceProps

/**
 * Coil's desktop decoder shrinks a picture to its slot by drawing it once with Skia's default
 * sampling, which picks the nearest pixel: a 2000 px portrait in a 250 px card keeps one pixel
 * in eight and drops the rest, so edges crawl and it looks hard and over-sharpened. Android's
 * decoder averages instead. This one does too: it halves the picture with a filter while it is
 * still twice the size or more (each halving averages four pixels into one), then a linear
 * filter takes it the rest of the way. Never enlarged here; the GPU does that when drawing.
 */
class SmoothDecoder(private val result: SourceFetchResult, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = result.source.source().use { it.readByteArray() }
        val source = Image.makeFromEncoded(bytes)
        try {
            val (w, h) = targetSize(source.width, source.height)
            val bitmap = shrink(source, w, h)
            bitmap.setImmutable()
            return DecodeResult(bitmap.asImage(), isSampled = w < source.width || h < source.height)
        } finally {
            source.close()
        }
    }

    /** The size Coil would decode to, but never larger than the picture itself. */
    private fun targetSize(srcW: Int, srcH: Int): Pair<Int, Int> {
        val tw = (options.size.width as? Dimension.Pixels)?.px
        val th = (options.size.height as? Dimension.Pixels)?.px
        if (tw == null && th == null) return srcW to srcH
        val sx = tw?.let { it.toDouble() / srcW }
        val sy = th?.let { it.toDouble() / srcH }
        val scale = when {
            sx == null -> sy!!
            sy == null -> sx
            options.scale == Scale.FILL -> maxOf(sx, sy)
            else -> minOf(sx, sy)
        }.coerceAtMost(1.0)
        return (srcW * scale).toInt().coerceAtLeast(1) to (srcH * scale).toInt().coerceAtLeast(1)
    }

    private fun shrink(source: Image, w: Int, h: Int): Bitmap {
        var current = source
        var cw = source.width
        var ch = source.height
        try {
            while (cw / 2 >= w && ch / 2 >= h) {
                val half = draw(current, cw / 2, ch / 2, LINEAR)
                if (current !== source) current.close()
                current = Image.makeFromBitmap(half.setImmutable())
                cw = half.width
                ch = half.height
                half.close() // the image keeps the pixels
            }
            return draw(current, w, h, LINEAR)
        } finally {
            if (current !== source) current.close()
        }
    }

    private fun draw(image: Image, w: Int, h: Int, sampling: SamplingMode): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(w, h, false)
        val canvas = Canvas(bitmap, SurfaceProps())
        canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            sampling,
            null,
            true,
        )
        canvas.close()
        return bitmap
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            SmoothDecoder(result, options)
    }

    private companion object {
        /**
         * At exactly half size a linear filter averages each 2x2 block, and for the last step
         * (never below half) it reads as smooth as a cubic. A cubic was tried there: on the
         * CPU Skia takes 22 ms for it per card picture, against 0.13 ms for linear.
         */
        val LINEAR: SamplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
    }
}
