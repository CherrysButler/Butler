package android.graphics

object Color {
    const val TRANSPARENT = 0
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    @JvmStatic fun parseColor(s: String): Int = java.lang.Long.parseLong(s.removePrefix("#").let { if (it.length == 6) "FF$it" else it }, 16).toInt()

    @JvmStatic fun alpha(color: Int): Int = color ushr 24
    @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF
    @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF
    @JvmStatic fun blue(color: Int): Int = color and 0xFF
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)

    /** Android's HSV: hue 0..360, saturation and value 0..1. */
    @JvmStatic fun colorToHSV(color: Int, hsv: FloatArray) {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        hsv[0] = if (h < 0f) h + 360f else h
        hsv[1] = if (max == 0f) 0f else d / max
        hsv[2] = max
    }

    @JvmStatic fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)

    @JvmStatic fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).let { kotlin.math.round(it).toInt() }.coerceIn(0, 255)
        return argb(alpha, ch(r), ch(g), ch(b))
    }
}
