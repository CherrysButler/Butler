package android.os

object SystemClock {
    private val start = System.nanoTime()
    @JvmStatic fun elapsedRealtime(): Long = (System.nanoTime() - start) / 1_000_000
    @JvmStatic fun uptimeMillis(): Long = elapsedRealtime()
}

class PowerManager {
    fun isIgnoringBatteryOptimizations(packageName: String): Boolean = true
    /** A PC has no battery saver Butler follows: the frosted frame stays on. */
    val isPowerSaveMode: Boolean get() = false
}

class Bundle

interface IBinder
