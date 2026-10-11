package android.graphics

import android.content.ContentResolver
import android.net.Uri

/** Only reached on Android 9+; the desktop answers as Android 8, so this never decodes. */
class ImageDecoder private constructor() {
    var allocator: Int = 0
    fun setTargetSize(width: Int, height: Int) = Unit
    fun setTargetSampleSize(size: Int) = Unit

    class Source internal constructor(val uri: Uri)
    class ImageInfo internal constructor(val size: android.util.Size)

    fun interface OnHeaderDecodedListener {
        fun onHeaderDecoded(decoder: ImageDecoder, info: ImageInfo, source: Source)
    }

    companion object {
        const val ALLOCATOR_SOFTWARE = 1
        @JvmStatic fun createSource(resolver: ContentResolver, uri: Uri): Source = Source(uri)
        @JvmStatic fun decodeBitmap(source: Source, listener: OnHeaderDecodedListener): Bitmap =
            error("ImageDecoder isn't used on the desktop")
        @JvmStatic fun decodeBitmap(source: Source): Bitmap = error("ImageDecoder isn't used on the desktop")
    }
}
