package com.cherry.butler.desktop

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * A frame-rate log for measuring Butler, off unless switched on with Ctrl+Shift+F. Skia's
 * own counter (frames per second, with the slowest and fastest frame of each two seconds)
 * can only start with the app, so switching it on restarts Butler, and the log goes to
 * files/frames.log.
 */
object FrameLog {
    private const val KEY = "frame_log_v2"
    private val prefs get() = DesktopPrefs.open("butler_desktop")
    val file: File get() = File(DesktopContext.filesDir, "frames.log")

    /** One press is one switch, however many key events the press makes. */
    @Volatile private var lastToggle = 0L

    fun startIfOn() {
        if (!prefs.getBoolean(KEY, false)) return
        System.setProperty("skiko.fps.enabled", "true")
        System.setProperty("skiko.fps.periodSeconds", "2")
        runCatching {
            val out = PrintStream(FileOutputStream(file, true), true)
            out.println("--- ${java.time.LocalDateTime.now()} start")
            System.setOut(out)
        }
        // A Flight Recorder trace of the same run: every collector pause, and what each
        // thread was doing, sampled every 10-20 ms. Written to files/butler.jfr on exit.
        runCatching {
            val trace = File(DesktopContext.filesDir, "butler.jfr").toPath()
            val recording = jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))
            recording.isToDisk = true
            recording.destination = trace
            recording.setDumpOnExit(true)
            // Every exception thrown, with where: the profile settings only count them.
            recording.enable("jdk.JavaExceptionThrow").withStackTrace()
            recording.start()
            // Written every 20 seconds too, so the trace is there however the app ends.
            Thread {
                while (true) {
                    Thread.sleep(20_000)
                    runCatching { recording.dump(trace) }
                }
            }.apply { isDaemon = true; name = "butler-trace" }.start()
        }
    }

    val on: Boolean get() = prefs.getBoolean(KEY, false)

    /**
     * Times each frame Compose builds (its onRender into Skia) and, every two seconds of
     * activity, logs the frame rate while frames are coming (gaps over 100 ms are idle, not
     * slow) and how long frames took to build against the display's budget.
     */
    @Volatile private var attached = false


    /** A line in files/frames.log, straight to the file (stdout is only redirected when the log is on). */
    fun line(text: String) = runCatching { file.appendText(text + "\n") }

    fun attach(window: java.awt.Window, refreshHz: Int, force: Boolean = false) {
        if (!on && !force) return
        if (attached) return
        attached = true
        val layer = findLayer(window) ?: return
        val inner = layer.renderDelegate ?: return
        val budgetNs = 1_000_000_000L / refreshHz.coerceAtLeast(30)
        val builds = LongArray(4096)
        var count = 0
        var activeNs = 0L
        var activeFrames = 0
        var last = 0L
        var windowStart = 0L
        layer.renderDelegate = object : org.jetbrains.skiko.SkikoRenderDelegate {
          override fun onRender(canvas: org.jetbrains.skia.Canvas, w: Int, h: Int, nanoTime: Long) {
            val t0 = System.nanoTime()
            inner.onRender(canvas, w, h, nanoTime)
            val t1 = System.nanoTime()
            if (count < builds.size) builds[count++] = t1 - t0
            if (last != 0L) {
                val gap = t0 - last
                if (gap < 100_000_000L) { activeNs += gap; activeFrames++ }
            }
            last = t0
            if (windowStart == 0L) windowStart = t0
            if (t0 - windowStart >= 2_000_000_000L) {
                val b = builds.copyOf(count).sorted()
                fun ms(ns: Long) = "%.1f".format(ns / 1e6)
                val fps = if (activeNs > 0) activeFrames * 1e9 / activeNs else 0.0
                val over = b.count { it > budgetNs }
                line(
                    "[meter] frames ${b.size}, active fps ${"%.0f".format(fps)}, build p50 ${ms(b[b.size / 2])} " +
                        "p95 ${ms(b[(b.size * 95) / 100])} max ${ms(b.last())} ms, over ${refreshHz}Hz budget: $over",
                )
                count = 0; activeNs = 0; activeFrames = 0; windowStart = t0
            }
          }
        }
    }

    private fun findLayer(c: java.awt.Component): org.jetbrains.skiko.SkiaLayer? {
        if (c is org.jetbrains.skiko.SkiaLayer) return c
        if (c is java.awt.Container) for (child in c.components) findLayer(child)?.let { return it }
        return null
    }

    fun toggle() {
        val now = System.currentTimeMillis()
        if (now - lastToggle < 1_000) return
        lastToggle = now
        val on = !prefs.getBoolean(KEY, false)
        prefs.edit().putBoolean(KEY, on).commit()
        if (on) restart() else DesktopToasts.show("Frame log off from the next start")
    }

    /** Starts Butler again a moment after this one has gone, so the new one isn't handed off to it. */
    private fun restart() {
        val exe = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
        if (exe == null) {
            DesktopToasts.show("Frame log on from the next start")
            return
        }
        DesktopToasts.show("Frame log on: restarting Butler")
        runCatching {
            ProcessBuilder("cmd", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" \"$exe\"")
                .redirectErrorStream(true)
                .start()
        }
        Thread {
            Thread.sleep(400)
            exitProcess(0)
        }.start()
    }
}
