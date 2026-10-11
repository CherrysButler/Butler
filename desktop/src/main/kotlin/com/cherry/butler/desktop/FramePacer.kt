package com.cherry.butler.desktop

import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/**
 * Knows when the window last drew a frame and how long a frame lasts on its display, so
 * work done between frames (the lists' prefetch) can stop short of the next one.
 */
object FramePacer {
    @Volatile private var frameStart = 0L
    @Volatile private var intervalNanos = 1_000_000_000L / 60

    /** Leaves room for the next frame to be built and handed to the GPU. */
    private const val MARGIN_NANOS = 1_500_000L

    /** Idle with no frame running: work may take a short slice. */
    private const val IDLE_SLICE_NANOS = 3_000_000L

    fun install(window: java.awt.Window, refreshHz: Int) {
        intervalNanos = 1_000_000_000L / refreshHz.coerceAtLeast(30)
        val layer = findLayer(window) ?: return
        val inner = layer.renderDelegate ?: return
        layer.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                frameStart = System.nanoTime()
                StartupClock.firstFrame()
                inner.onRender(canvas, width, height, nanoTime)
            }
        }
    }

    /** Until when work between frames may run, from now. */
    fun idleUntil(): Long {
        val now = System.nanoTime()
        val sinceFrame = now - frameStart
        return if (sinceFrame < intervalNanos) frameStart + intervalNanos - MARGIN_NANOS else now + IDLE_SLICE_NANOS
    }

    internal fun findLayer(c: java.awt.Component): SkiaLayer? {
        if (c is SkiaLayer) return c
        if (c is java.awt.Container) for (child in c.components) findLayer(child)?.let { return it }
        return null
    }
}
