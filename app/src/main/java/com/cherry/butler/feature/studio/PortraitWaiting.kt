package com.cherry.butler.feature.studio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A portrait waiting to be filled: Butler's dither (the thinking line's 4x4 Bayer dots), gathered
 * in the shape of a head and shoulders, with a slow breath rising through it from the bottom.
 * It stands in the picture slot of a character not yet given a face.
 *
 * [busy] is the picture on its way up: the whole slot breathes, quicker, in [busyInk], over the
 * picture just picked.
 */
@Composable
internal fun PortraitWaiting(ink: Color, modifier: Modifier = Modifier, busy: Boolean = false, busyInk: Color = ink) {
    val breath by rememberInfiniteTransition(label = "portrait").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (busy) BUSY_MS else BREATH_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "portrait-breath",
    )
    val cellPx = with(LocalDensity.current) { 4.dp.toPx() }
    val dotPx = with(LocalDensity.current) { 2.dp.toPx() }
    // Its own layer: a breath redraws the dots alone, never the chapter around them.
    Canvas(modifier.graphicsLayer()) {
        val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
        val rows = (size.height / cellPx).toInt().coerceAtLeast(1)
        val ox = (size.width - cols * cellPx) / 2f + (cellPx - dotPx) / 2f
        val oy = (size.height - rows * cellPx) / 2f + (cellPx - dotPx) / 2f
        val dot = Size(dotPx, dotPx)
        for (j in 0 until rows) {
            val v = (j + 0.5f) / rows
            // The breath rises: a crest travels from the shoulders up through the head.
            val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * (breath + v * 0.7f))
            for (i in 0 until cols) {
                val u = (i + 0.5f) / cols
                val density = if (busy) 0.18f + 0.5f * wave else 0.05f + silhouette(u, v) * (0.28f + 0.5f * wave)
                if (density > (BAYER[(j and 3) * 4 + (i and 3)] + 0.5f) / 16f) {
                    drawRect(if (busy) busyInk else ink, topLeft = Offset(ox + i * cellPx, oy + j * cellPx), size = dot)
                }
            }
        }
    }
}

/** How much of a head and shoulders (0 to 1) sits at (u, v) of the slot, with a soft edge. */
private fun silhouette(u: Float, v: Float): Float {
    val head = edge(radial(u, v, 0.5f, 0.39f, 0.17f, 0.19f))
    val shoulders = edge(radial(u, v, 0.5f, 1.02f, 0.38f, 0.31f))
    return maxOf(head, shoulders)
}

/** Distance from an ellipse's centre in its own radii: 1 on its edge. */
private fun radial(u: Float, v: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
    val dx = (u - cx) / rx
    val dy = (v - cy) / ry
    return sqrt(dx * dx + dy * dy)
}

/** Full inside, fading out across a band at the edge. */
private fun edge(d: Float): Float = ((1.12f - d) / 0.24f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** The thinking line's 4x4 Bayer order (DitherTiles), as thresholds 0..15. */
private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

private const val BREATH_MS = 3_600
private const val BUSY_MS = 1_100

/**
 * Lines not written yet: rows of Butler's dots in the shape of a paragraph, uneven like real
 * lines, with a slow wave passing along them as if they were being written. It stands in an
 * empty writing field (it goes the moment the field is written in or focused).
 *
 * [lineHeight] is the field's own line height, so the rows sit where the text will.
 */
@Composable
internal fun UnwrittenLines(ink: Color, lineHeight: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, seed: Int = 0) {
    val stroke by rememberInfiniteTransition(label = "unwritten").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WRITE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "unwritten-wave",
    )
    val density = LocalDensity.current
    val cellPx = with(density) { 4.dp.toPx() }
    val dotPx = with(density) { 2.dp.toPx() }
    val linePx = with(density) { lineHeight.toPx() }
    Canvas(modifier.graphicsLayer()) {
        val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
        val lines = (size.height / linePx).toInt().coerceAtLeast(1)
        val dot = Size(dotPx, dotPx)
        for (k in 0 until lines) {
            // Each line its own length, the last one short, as a paragraph ends.
            val length = if (k == lines - 1 && lines > 1) 0.35f + 0.2f * hash(seed, k) else 0.7f + 0.28f * hash(seed, k)
            // Two rows of dots at the line's middle, where the letters' bodies sit.
            val top = k * linePx + linePx / 2f - cellPx
            for (row in 0..1) {
                val y = top + row * cellPx + (cellPx - dotPx) / 2f
                for (i in 0 until (cols * length).toInt()) {
                    val u = (i + 0.5f) / cols
                    val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * (stroke - u * 0.8f - k * 0.13f))
                    val amount = 0.1f + 0.42f * wave
                    if (amount > (BAYER[((row + k * 2) and 3) * 4 + (i and 3)] + 0.5f) / 16f) {
                        drawRect(ink, topLeft = Offset(i * cellPx + (cellPx - dotPx) / 2f, y), size = dot)
                    }
                }
            }
        }
    }
}

/** A steady 0..1 for line [k] of field [seed]: the same lengths every time the field is empty. */
private fun hash(seed: Int, k: Int): Float {
    var h = seed * 374761393 + k * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private const val WRITE_MS = 4_200
