package android.graphics

import java.io.File

/** Typeface.Builder(file).build() is how Butler checks an added font reads; Skia checks here. */
class Typeface private constructor() {
    class Builder(private val file: File) {
        fun build(): Typeface {
            val data = org.jetbrains.skia.Data.makeFromBytes(file.readBytes())
            requireNotNull(org.jetbrains.skia.FontMgr.default.makeFromData(data)) { "not a font" }
            return Typeface()
        }
    }

    companion object {
        @JvmField val DEFAULT = Typeface()
    }
}
