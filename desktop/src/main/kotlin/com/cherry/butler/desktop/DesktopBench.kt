package com.cherry.butler.desktop

import com.cherry.butler.ui.navigation.TopLevelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Development only (desktop-dev; removed before release): `janitor://butler-bench` scrolls
 * Browse by itself at a steady speed for 20 seconds, loading pages as it goes, while the
 * frame meter and a Flight Recorder trace run. Results: files/frames.log and files/bench.jfr.
 */
object DesktopBench {
    /** Set by Browse while it is on screen: scrolls its grid by px over ms, from its own scope. */
    @Volatile var scroller: (suspend (px: Float, ms: Int) -> Unit)? = null
    @Volatile var toTop: (suspend () -> Unit)? = null
    @Volatile private var running = false
    private var restore: (() -> Unit)? = null

    fun isBench(link: String) = link.startsWith("janitor://butler-bench")

    suspend fun run() {
        if (running) return
        running = true
        try {
            val win = DesktopTour.window ?: return
            val c = DesktopTour.controls ?: return DesktopToasts.show("Sign in first")
            val frame = win as? java.awt.Frame
            val wasState = frame?.extendedState ?: java.awt.Frame.NORMAL
            withContext(Dispatchers.Main) {
                c.character(null); c.section(TopLevelDestination.Browse)
                // Full screen is the hard case: the most cards on screen at once.
                frame?.extendedState = java.awt.Frame.MAXIMIZED_BOTH
                win.toFront()
            }
            restore = { frame?.extendedState = wasState }
            delay(3_000)
            val refresh = win.graphicsConfiguration.device.displayMode.refreshRate
            withContext(Dispatchers.Main) { FrameLog.attach(win, refresh, force = true) }
            val trace = File(DesktopContext.filesDir, "bench.jfr").toPath()
            val recording = jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))
            recording.enable("jdk.JavaExceptionThrow").withStackTrace()
            var scroll = scroller
            repeat(50) { if (scroll == null) { delay(100); scroll = scroller } }
            val scrollGrid = scroll ?: run { FrameLog.line("--- bench: no Browse grid on screen"); return }
            toTop?.invoke()
            delay(1_500)
            FrameLog.line("--- bench ${java.time.LocalDateTime.now()} start, ${refresh} Hz")
            // Development: -Dbutler.bench.trace=off measures without the trace weighing on it.
            val traced = System.getProperty("butler.bench.trace") != "off"
            if (traced) recording.start()
            val began = System.nanoTime()
            runCatching { scrollGrid(18_000f, 20_000) }.onFailure { FrameLog.line("--- bench scroll stopped: $it") }
            FrameLog.line("--- bench scrolled for ${(System.nanoTime() - began) / 1_000_000} ms")
            delay(1_000)
            if (traced) { recording.stop(); recording.dump(trace) }
            recording.close()
            FrameLog.line("--- bench end")
            org.jetbrains.skia.Graphics.let { g ->
                FrameLog.line("[skia] font cache ${g.fontCacheUsed / 1024} of ${g.fontCacheLimit / 1024} KB (${g.fontCacheCountUsed} glyph sets), resource cache ${g.resourceCacheTotalUsed / 1024} of ${g.resourceCacheTotalLimit / 1024} KB")
            }
            toTop?.invoke()
            DesktopToasts.show("Benchmark saved")
        } finally {
            restore?.let { withContext(Dispatchers.Main) { it() } }
            restore = null
            running = false
        }
    }
}
