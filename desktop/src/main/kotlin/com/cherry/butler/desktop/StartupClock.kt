package com.cherry.butler.desktop

/**
 * Development: with -Dbutler.startup.log=true, how long each step of starting took, from the
 * process starting to Butler's first frame, written to files/frames.log.
 */
object StartupClock {
    private val on = System.getProperty("butler.startup.log") == "true"
    private val started = ProcessHandle.current().info().startInstant().map { it.toEpochMilli() }.orElse(System.currentTimeMillis())
    private val marks = StringBuilder()
    @Volatile private var done = false

    fun mark(step: String) {
        if (!on || done) return
        synchronized(marks) { marks.append(step).append(' ').append(System.currentTimeMillis() - started).append("ms  ") }
    }

    /** The first frame: the line is written once. */
    fun firstFrame() {
        if (!on || done) return
        mark("frame")
        done = true
        FrameLog.line("[startup] " + synchronized(marks) { marks.toString() })
    }
}
