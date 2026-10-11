package android.os

/**
 * The desktop answers as the oldest Android Butler supports, so shared code takes its plain
 * paths (BitmapFactory, not ImageDecoder; no notification permission to ask for).
 */
object Build {
    @JvmField val MANUFACTURER: String = "PC"
    @JvmField val MODEL: String = System.getProperty("os.name") ?: "Desktop"
    @JvmField val BRAND: String = "desktop"
    @JvmField val DEVICE: String = "desktop"

    object VERSION {
        @JvmField val SDK_INT: Int = 26
        @JvmField val RELEASE: String = (System.getProperty("os.name") ?: "Desktop") + " " + (System.getProperty("os.version") ?: "")
    }

    object VERSION_CODES {
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
}
