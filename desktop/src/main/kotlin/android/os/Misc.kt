package android.os

object SystemClock {
    private val start = System.nanoTime()
    @JvmStatic fun elapsedRealtime(): Long = (System.nanoTime() - start) / 1_000_000
    @JvmStatic fun uptimeMillis(): Long = elapsedRealtime()
}

class PowerManager {
    fun isIgnoringBatteryOptimizations(packageName: String): Boolean = true
}

class Bundle

interface IBinder
