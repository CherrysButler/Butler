package android.content.res

/** The window's size in dp, as the phone's Configuration gives the screen's. */
class Configuration(@JvmField val screenWidthDp: Int, @JvmField val screenHeightDp: Int) {
    @JvmField val orientation: Int = if (screenWidthDp > screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    @JvmField val fontScale: Float = 1f

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}
