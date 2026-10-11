package android.media

import java.io.InputStream

/**
 * Reads the EXIF orientation of a JPEG, the one tag Butler asks for: a tiny APP1 parser,
 * so a phone photo dropped on the PC is still turned upright.
 */
class ExifInterface(stream: InputStream) {
    private val orientation: Int = runCatching { readOrientation(stream.readBytes()) }.getOrDefault(ORIENTATION_NORMAL)

    fun getAttributeInt(tag: String, defaultValue: Int): Int =
        if (tag == TAG_ORIENTATION) orientation else defaultValue

    private fun readOrientation(b: ByteArray): Int {
        if (b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) return ORIENTATION_NORMAL
        var i = 2
        while (i + 4 < b.size) {
            if (b[i] != 0xFF.toByte()) return ORIENTATION_NORMAL
            val marker = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (marker == 0xE1 && i + 10 < b.size && String(b, i + 4, 4, Charsets.ISO_8859_1) == "Exif") {
                val t = i + 10
                val little = b[t] == 'I'.code.toByte()
                fun u16(p: Int) = if (little) (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)
                else ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)
                fun u32(p: Int) = if (little) u16(p) or (u16(p + 2) shl 16) else (u16(p) shl 16) or u16(p + 2)
                val ifd = t + u32(t + 4)
                val count = u16(ifd)
                for (e in 0 until count) {
                    val entry = ifd + 2 + e * 12
                    if (u16(entry) == 0x0112) return u16(entry + 8)
                }
                return ORIENTATION_NORMAL
            }
            if (marker == 0xDA) return ORIENTATION_NORMAL
            i += 2 + len
        }
        return ORIENTATION_NORMAL
    }

    companion object {
        const val TAG_ORIENTATION = "Orientation"
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_FLIP_HORIZONTAL = 2
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_FLIP_VERTICAL = 4
        const val ORIENTATION_TRANSPOSE = 5
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_TRANSVERSE = 7
        const val ORIENTATION_ROTATE_270 = 8
    }
}
