package android.util

class DisplayMetrics(@JvmField val widthPixels: Int, @JvmField val heightPixels: Int, @JvmField val density: Float) {
    companion object {
        fun current(): DisplayMetrics {
            val screen = runCatching { java.awt.Toolkit.getDefaultToolkit().screenSize }.getOrNull()
            val scale = runCatching { java.awt.Toolkit.getDefaultToolkit().screenResolution / 96f }.getOrDefault(1f)
            return DisplayMetrics(screen?.width ?: 1920, screen?.height ?: 1080, scale)
        }
    }
}
