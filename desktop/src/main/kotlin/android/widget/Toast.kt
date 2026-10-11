package android.widget

import android.content.Context

/** A toast shows in the Butler window's toast line (DesktopToasts). */
class Toast private constructor(private val text: CharSequence) {
    fun show() = com.cherry.butler.desktop.DesktopToasts.show(text.toString())

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Context?, text: CharSequence, duration: Int): Toast = Toast(text)
    }
}
