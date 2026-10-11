package android.graphics

import java.io.File
import java.io.InputStream

object BitmapFactory {
    class Options {
        @JvmField var inJustDecodeBounds: Boolean = false
        @JvmField var inSampleSize: Int = 1
        @JvmField var outWidth: Int = -1
        @JvmField var outHeight: Int = -1
        @JvmField var outMimeType: String? = null
        @JvmField var inPreferredConfig: Bitmap.Config? = null
    }

    @JvmStatic
    fun decodeStream(stream: InputStream?, outPadding: Rect? = null, opts: Options? = null): Bitmap? =
        stream?.readBytes()?.let { decodeByteArray(it, 0, it.size, opts) }

    @JvmStatic
    fun decodeFile(path: String, opts: Options? = null): Bitmap? =
        File(path).takeIf { it.isFile }?.readBytes()?.let { decodeByteArray(it, 0, it.size, opts) }

    @JvmStatic
    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap? {
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        if (opts?.inJustDecodeBounds == true) {
            // Android answers the size and returns null; so does this.
            val image = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes) }.getOrNull()
            opts.outWidth = image?.width ?: -1
            opts.outHeight = image?.height ?: -1
            image?.close()
            return null
        }
        val full = Bitmap.decode(bytes) ?: return null
        val sample = opts?.inSampleSize?.coerceAtLeast(1) ?: 1
        if (opts != null) { opts.outWidth = full.width / sample; opts.outHeight = full.height / sample }
        if (sample == 1) return full
        return Bitmap.createScaledBitmap(full, (full.width / sample).coerceAtLeast(1), (full.height / sample).coerceAtLeast(1), true)
            .also { full.recycle() }
    }
}

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0)
