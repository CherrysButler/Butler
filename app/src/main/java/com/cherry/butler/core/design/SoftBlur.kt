package com.cherry.butler.core.design

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A soft blur baked into a bitmap, the same on every Android version (Modifier.blur needs 12+).
 * The picture is shrunk, box-blurred three times (close to a Gaussian), and grown back with
 * filtering, which is both fast and smoother than blurring at full size.
 *
 * [strength] runs 0 (untouched) to 1 (about 3% of the width as radius: shapes and colour
 * only). The same strength looks the same on a preview and on the full picture, because the
 * radius follows the width.
 */
fun Bitmap.softBlur(strength: Float): Bitmap {
    val s = strength.coerceIn(0f, 1f)
    if (s <= 0.001f) return this
    val radius = s * 0.03f * width
    val down = 4
    val sw = max(1, width / down)
    val sh = max(1, height / down)
    val small = Bitmap.createScaledBitmap(this, sw, sh, true)
    val r = max(1, (radius / down).roundToInt())
    val pixels = IntArray(sw * sh)
    small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    val scratch = IntArray(pixels.size)
    repeat(3) {
        boxPass(pixels, scratch, sw, sh, r, horizontal = true)
        boxPass(scratch, pixels, sw, sh, r, horizontal = false)
    }
    small.setPixels(pixels, 0, sw, 0, 0, sw, sh)
    val out = Bitmap.createScaledBitmap(small, width, height, true)
    if (small !== out) small.recycle()
    return out
}

/** One running-sum box blur along rows or columns, edges clamped. */
private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val window = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        var a = 0; var red = 0; var g = 0; var b = 0
        for (i in -r..r) {
            val p = at(i)
            a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until len) {
            val idx = if (horizontal) line * w + i else i * w + line
            dst[idx] = ((a / window) shl 24) or ((red / window) shl 16) or ((g / window) shl 8) or (b / window)
            val out = at(i - r)
            val inn = at(i + r + 1)
            a += (inn ushr 24) - (out ushr 24)
            red += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
            g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
            b += (inn and 0xFF) - (out and 0xFF)
        }
    }
}
