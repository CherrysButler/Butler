package com.cherry.butler.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A picture seen through a pane of glass on plain ground ([glassPane]): a bot's own picture
 * under its card. [image] is a very small, soft copy of it (SoftPicture), and [slot] is where
 * the picture itself lies in the pane, in pixels. Past the slot the copy carries on mirrored,
 * so the picture's colours run out from its edges under the glass without a seam.
 */
class GlassPicture(val image: ImageBitmap, val slot: Rect)

/**
 * Softens [pixels] (ARGB, [w] by [h]) in place: a Gaussian blur of [sigma] pixels, both ways,
 * holding the edge. The copy is drawn many times its size, and only a copy this smooth scales
 * up without showing its pixels. The result is opaque.
 */
fun softenPixels(pixels: IntArray, w: Int, h: Int, sigma: Float) {
    val reach = kotlin.math.ceil(sigma * 2.5f).toInt().coerceAtLeast(1)
    val weights = FloatArray(reach * 2 + 1) { i -> val x = (i - reach) / sigma; kotlin.math.exp(-0.5f * x * x) }
    val total = weights.sum()
    val r = FloatArray(w * h)
    val g = FloatArray(w * h)
    val b = FloatArray(w * h)
    for (i in pixels.indices) {
        val p = pixels[i]
        r[i] = ((p shr 16) and 0xFF).toFloat()
        g[i] = ((p shr 8) and 0xFF).toFloat()
        b[i] = (p and 0xFF).toFloat()
    }
    fun pass(from: FloatArray, to: FloatArray, across: Boolean) {
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f
            for (k in -reach..reach) {
                val at = if (across) y * w + (x + k).coerceIn(0, w - 1) else (y + k).coerceIn(0, h - 1) * w + x
                sum += from[at] * weights[k + reach]
            }
            to[y * w + x] = sum / total
        }
    }
    val spare = FloatArray(w * h)
    for (channel in arrayOf(r, g, b)) {
        pass(channel, spare, across = true)
        pass(spare, channel, across = false)
    }
    for (i in pixels.indices) {
        pixels[i] = (0xFF shl 24) or (r[i].toInt().coerceIn(0, 255) shl 16) or (g[i].toInt().coerceIn(0, 255) shl 8) or b[i].toInt().coerceIn(0, 255)
    }
}
