package android.graphics

/** The 3x3 matrix Butler builds from a photo's EXIF: rotations, flips, scales. */
class Matrix {
    // Row-major affine: [a b c; d e f; 0 0 1]
    private var m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun post(o: FloatArray) {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = (0..2).sumOf { k -> (o[i * 3 + k] * m[k * 3 + j]).toDouble() }.toFloat()
        m = r
    }

    fun postRotate(degrees: Float): Boolean {
        val rad = Math.toRadians(degrees.toDouble())
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()
        post(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        return true
    }

    fun setRotate(degrees: Float) { reset(); postRotate(degrees) }

    fun postScale(sx: Float, sy: Float): Boolean { post(floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)); return true }

    fun preScale(sx: Float, sy: Float): Boolean {
        val r = FloatArray(9)
        val o = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = (0..2).sumOf { k -> (m[i * 3 + k] * o[k * 3 + j]).toDouble() }.toFloat()
        m = r
        return true
    }

    fun setScale(sx: Float, sy: Float) { reset(); postScale(sx, sy) }

    val isIdentity: Boolean get() = m.contentEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

    fun reset() { m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) }

    internal fun toSkia(): org.jetbrains.skia.Matrix33 = org.jetbrains.skia.Matrix33(*m)

    internal fun mapBounds(w: Float, h: Float): org.jetbrains.skia.Rect {
        val xs = FloatArray(4)
        val ys = FloatArray(4)
        listOf(0f to 0f, w to 0f, 0f to h, w to h).forEachIndexed { i, (x, y) ->
            xs[i] = m[0] * x + m[1] * y + m[2]
            ys[i] = m[3] * x + m[4] * y + m[5]
        }
        return org.jetbrains.skia.Rect(xs.min(), ys.min(), xs.max(), ys.max())
    }
}
