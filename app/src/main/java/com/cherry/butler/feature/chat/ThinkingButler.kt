package com.cherry.butler.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * A little pixel butler walking the thinking line: tailcoat, white front, a Butler-red bow
 * tie, and a tray held out ahead of him with a speech bubble on it, its three dots lighting
 * in turn as a reply being typed does.
 *
 * Behind him a neural network wakes as he goes: a faint web of layers always there, swelling
 * into the accent's colour as it falls behind him, signals running along it away from him, dimming slowly as
 * he walks on and never quite out; each time he walks back through, it lights again in the
 * next of the accent's neighbouring colours, as a thought gone over once more.
 *
 * The frames are generated from a sprite source kept outside the repository: four steps of
 * walk at 150 ms. A grid pixel is as many screen pixels as the row allows, whole ones only,
 * so he stays crisp at any scale; he turns at each end of the row.
 */
@Composable
fun ThinkingButler(modifier: Modifier = Modifier, still: Boolean = false) {
    val ink = MaterialTheme.colorScheme.onSurface
    val light = MaterialTheme.colorScheme.background.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } > 0.5f
    val tones = Tones(
        coat = ButlerTheme.colors.textMed,
        shade = ButlerTheme.colors.textLow,
        face = if (light) ButlerTheme.colors.textLow else ink,
        front = if (light) ButlerTheme.colors.surfaceElevated else ink,
        hair = if (light) ink else ButlerTheme.colors.textLow,
        shoe = if (light) ink else ButlerTheme.colors.textLow,
        tray = if (light) ButlerTheme.colors.textLow else ink,
        tie = MaterialTheme.colorScheme.primary,
        bubble = ButlerTheme.colors.speech,
        dot = MaterialTheme.colorScheme.background,
    )
    // The network's colours come from the look's accent: its hue, and a softened version of its
    // saturation, so the web belongs to the theme (Butler red, or a Custom look's own colour).
    val accent = MaterialTheme.colorScheme.primary
    val accentHsv = remember(accent) { hsv(accent) }
    val sat = accentHsv[1] * 0.7f
    val value = if (light) (accentHsv[2] * 0.8f).coerceAtMost(0.7f) else 0.95f
    val accentHue = accentHsv[0]
    // Pixel lines carry more ink than hairlines did: the resting web is a touch fainter for it.
    val skeleton = ButlerTheme.colors.rule.copy(alpha = 0.1f)
    val net = remember { Network() }
    // Ticks only while some of him is on screen.
    val shown = rememberOnScreen()
    val clock = rememberThinkingClock(running = shown.value)
    Canvas(modifier.onScreen(shown)) {
        val nanos = clock.value
        val seconds = nanos / 1e9f
        val tick = nanos / (FRAME_MS * 1_000_000L)
        // Still: the passing frame, feet together, tray up.
        val frame = if (still) 1 else (tick % BUTLER_FRAMES.size).toInt()
        // A grid pixel: the row's height over the grid's, with a pixel's margin above and below.
        val px = (size.height / (BUTLER_ROWS + 1)).toInt().coerceAtLeast(1).toFloat()
        // Less than a body-width to spare (the small box) is no room to walk: he walks on the spot.
        val range = ((size.width - BUTLER_COLS * px) / px).toInt().let { if (it < BUTLER_COLS) 0 else it }
        val top = ((size.height - BUTLER_ROWS * px) / 2f).toInt().toFloat()
        // Across the row at his own pace, whole grid pixels at a time, there and back.
        val travel = if (range > 0) ((seconds * WALK_PX_PER_S).toLong() % (2L * range)).toInt() else 0
        val right = travel <= range
        val gridX = if (range > 0) (if (right) travel else 2 * range - travel) else 0
        // With no room to walk (a small box) he stands in the middle, on a whole grid pixel.
        val cols = (size.width / px).toInt()
        val gx = if (range > 0) gridX else (cols - BUTLER_COLS) / 2
        val x = gx * px
        // The web is on his grid: the same pixel, the same origin.
        val gap = (15.dp.toPx() / px).toInt().coerceAtLeast(4)
        if (still) {
            // A finished thought: he stands, and the web behind him keeps on, lit and swaying
            // either side of the accent.
            net.layout(cols, BUTLER_ROWS, gap = (11.dp.toPx() / px).toInt().coerceAtLeast(3))
            net.ambient(seconds, accentHue)
            net.draw(this, px, 0f, top, at = cols / 2, now = seconds, skeleton = skeleton, sat = sat, value = value)
        } else if (range > 0) {
            net.layout(cols, BUTLER_ROWS, gap = gap)
            val center = gx + BUTLER_COLS / 2
            // The stretch behind him, from his back to his middle: never what he walks toward. The
            // row's ends are behind him once he has turned there.
            if (right) net.step(gx, center, seconds, accentHue) else net.step(center, gx + BUTLER_COLS - 1, seconds, accentHue)
            net.draw(this, px, 0f, top, at = center, now = seconds, skeleton = skeleton, sat = sat, value = value)
        }
        val lit = if (still) -1 else (tick / 2 % 3).toInt()
        val rows = BUTLER_FRAMES[frame]
        for (r in 0 until BUTLER_ROWS) {
            val row = rows[r]
            for (c in 0 until BUTLER_COLS) {
                val color = when (val ch = row[c]) {
                    '.' -> continue
                    'c' -> tones.coat
                    'C' -> tones.shade
                    'f' -> tones.face
                    'w' -> tones.front
                    'h', 'e' -> tones.hair
                    's' -> tones.shoe
                    'S' -> tones.shoe.copy(alpha = 0.6f)
                    'T' -> tones.tray
                    't' -> tones.tie
                    'b' -> tones.bubble
                    '1', '2', '3' -> tones.dot.copy(alpha = if (lit < 0 || ch - '1' == lit) 1f else 0.4f)
                    else -> continue
                }
                val col = if (right) c else BUTLER_COLS - 1 - c
                drawRect(color, topLeft = Offset(x + col * px, top + r * px), size = Size(px, px))
            }
        }
    }
}

private class Tones(
    val coat: Color, val shade: Color, val face: Color, val front: Color, val hair: Color,
    val shoe: Color, val tray: Color, val tie: Color, val bubble: Color, val dot: Color,
)

/** One step of his walk, as drawn. */
private const val FRAME_MS = 150L

/** How fast he crosses the row, in grid pixels a second. */
private const val WALK_PX_PER_S = 14f

/**
 * The network behind him, on his own pixel grid: layers of three nodes all along the row (the
 * same scatter every time), each node linked to two of the next layer's by a stepped line of
 * grid pixels, the whole web always there as a faint skeleton. A layer wakes as it falls behind
 * him: its light swells, a pixel signal hops along each link away from him, and once he has
 * gone it eases down over a few seconds to a floor it keeps. Its colours are the accent's: the
 * first pass in the accent's own hue, each pass after a near neighbour of it, warmer, cooler
 * and back. Every change of light and colour eases; nothing switches in a frame.
 */
private class Network {
    private var cols = -1
    private var rows = -1
    private var xs = IntArray(0)
    private var ys = Array(0) { IntArray(0) }
    /** Every link's pixels, worked out once: [link][i] packs x and y; links in layer order. */
    private var paths = Array(0) { IntArray(0) }
    private var pathLayer = IntArray(0)
    private var pathSeed = IntArray(0)
    /** Each layer's light now, 0..1, and its hue now, in degrees. */
    private var level = FloatArray(0)
    private var hue = FloatArray(0)
    private var woken = BooleanArray(0)
    private var passes = IntArray(0)
    private var behind = BooleanArray(0)
    private var last = -1f

    /** [cols] x [rows] grid pixels; [gap] grid pixels between layers. */
    fun layout(cols: Int, rows: Int, gap: Int) {
        if (cols == this.cols && rows == this.rows) return
        this.cols = cols
        this.rows = rows
        val n = (cols / gap).coerceAtLeast(2)
        val step = cols.toFloat() / n
        xs = IntArray(n) { i -> (step * (i + 0.5f) + (unit(i * 3 + 1) - 0.5f) * step * 0.5f).toInt().coerceIn(0, cols - 1) }
        val span = rows - 2
        ys = Array(n) { i ->
            // Three nodes a layer, each in its own third of the height, so links criss-cross.
            IntArray(NODES) { k -> (1 + span * (k + 0.2f + 0.6f * unit(i * 11 + k * 5 + 2)) / NODES).toInt().coerceIn(0, rows - 1) }
        }
        val links = ArrayList<IntArray>()
        val layers = ArrayList<Int>()
        val seeds = ArrayList<Int>()
        for (i in 0 until n - 1) for (a in 0 until NODES) for (b in 0 until NODES) {
            if (!linked(i, a, b)) continue
            links += line(xs[i], ys[i][a], xs[i + 1], ys[i + 1][b])
            layers += i
            seeds += i * 31 + a * 7 + b
        }
        paths = links.toTypedArray()
        pathLayer = layers.toIntArray()
        pathSeed = seeds.toIntArray()
        level = FloatArray(n)
        hue = FloatArray(n) { Float.NaN }
        woken = BooleanArray(n)
        passes = IntArray(n)
        behind = BooleanArray(n)
    }

    /** One frame: grid columns [from]..[to] are the stretch just behind him. */
    fun step(from: Int, to: Int, now: Float, base: Float) {
        val dt = tick(now)
        for (i in xs.indices) {
            val inside = xs[i] in from..to
            if (inside && !behind[i]) passes[i]++
            behind[i] = inside
            if (inside) woken[i] = true
            val target = when {
                inside -> 1f
                woken[i] -> FLOOR
                else -> 0f
            }
            val tau = if (target > level[i]) RISE_S else FADE_S
            level[i] += (target - level[i]) * (1f - exp(-dt / tau))
            // Each pass steps to the next of the accent's neighbours; it turns there the short way.
            val goal = base + PASS_HUES[(passes[i] - 1).coerceAtLeast(0) % PASS_HUES.size]
            if (hue[i].isNaN()) hue[i] = goal
            hue[i] += turn(hue[i], goal) * (1f - exp(-dt / TURN_S))
        }
    }

    /** One frame with no one walking: every layer lit to a steady glow, swaying either side of the accent. */
    fun ambient(now: Float, base: Float) {
        val dt = tick(now)
        for (i in xs.indices) {
            level[i] += (AMBIENT - level[i]) * (1f - exp(-dt / RISE_S))
            hue[i] = base + SWAY_DEG * sin((now / SWAY_S + i * 0.09f) * 2f * Math.PI.toFloat())
        }
    }

    private fun tick(now: Float): Float {
        val dt = if (last < 0f) 0f else (now - last).coerceIn(0f, 0.1f)
        last = now
        return dt
    }

    private fun turn(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

    private fun tint(h: Float, sat: Float, value: Float): Color =
        Color.hsv(((if (h.isNaN()) 0f else h) % 360f + 360f) % 360f, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    /** Draws the web; grid pixel (gx, gy) is at [left] + gx * [px], [top] + gy * [px]. */
    fun draw(scope: DrawScope, px: Float, left: Float, top: Float, at: Int, now: Float, skeleton: Color, sat: Float, value: Float) {
        val size = Size(px, px)
        fun dot(color: Color, gx: Int, gy: Int) {
            if (gx < 0 || gy < 0 || gx >= cols || gy >= rows) return
            scope.drawRect(color, topLeft = Offset(left + gx * px, top + gy * px), size = size)
        }
        // The skeleton: every link, barely there. The links' own end pixels are the nodes'.
        for (path in paths) for (k in 1 until path.size - 1) dot(skeleton, path[k] shr 16, path[k] and 0xffff)
        // The woken links, and a signal hopping along each, away from him.
        for (p in paths.indices) {
            val i = pathLayer[p]
            val glow = minOf(level[i], level[i + 1])
            if (glow <= 0.01f) continue
            val color = tint(hue[i] + turn(hue[i], hue[i + 1]) / 2f, sat, value)
            val path = paths[p]
            val lit = color.copy(alpha = color.alpha * glow * LINK_ALPHA)
            for (k in 1 until path.size - 1) dot(lit, path[k] shr 16, path[k] and 0xffff)
            if (path.size > 2) {
                val outward = xs[i] > at
                val f = (now * SIGNAL_SPEED + unit(pathSeed[p])) % 1f
                val k = 1 + ((if (outward) f else 1f - f) * (path.size - 2)).toInt().coerceIn(0, path.size - 3)
                dot(color.copy(alpha = color.alpha * glow * glow * SIGNAL_ALPHA), path[k] shr 16, path[k] and 0xffff)
            }
        }
        // Nodes: a faint pixel always; lit, a bright core with a cross of glow round it.
        for (i in xs.indices) {
            val glow = level[i]
            val color = tint(hue[i], sat, value)
            for (k in 0 until NODES) {
                val gx = xs[i]
                val gy = ys[i][k]
                if (glow <= 0.01f) {
                    dot(skeleton, gx, gy)
                    continue
                }
                val halo = color.copy(alpha = color.alpha * glow * HALO_ALPHA)
                dot(halo, gx - 1, gy); dot(halo, gx + 1, gy); dot(halo, gx, gy - 1); dot(halo, gx, gy + 1)
                dot(color.copy(alpha = color.alpha * glow * NODE_ALPHA), gx, gy)
            }
        }
    }

    /** Whether node [a] of layer [i] links to node [b] of the next: two of the three, the same two every time. */
    private fun linked(i: Int, a: Int, b: Int): Boolean = b != ((unit(i * 13 + a * 3 + 5) * NODES).toInt() % NODES)

    /** A stepped line of grid pixels, ends included, each packed as x shl 16 or y. */
    private fun line(x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val out = ArrayList<Int>()
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            out += (x shl 16) or y
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return out.toIntArray()
    }

    /** A steady pseudo-random number in 0..1 for [seed]. */
    private fun unit(seed: Int): Float {
        var h = seed * 0x45d9f3b
        h = (h xor (h ushr 16)) * 0x45d9f3b
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    private companion object {
        const val NODES = 3
        /** What a layer keeps once he has gone: never quite out. */
        const val FLOOR = 0.3f
        /** How quickly a layer's light swells as it falls behind him, and fades after. */
        const val RISE_S = 0.35f
        const val FADE_S = 5f
        /** Each pass's hue, as degrees from the accent's: the accent, then warmer and cooler neighbours. */
        val PASS_HUES = floatArrayOf(0f, 24f, -22f, 40f, -36f, 12f)
        const val TURN_S = 0.9f
        /** A finished thought's web: how lit it stays, and its sway either side of the accent. */
        const val AMBIENT = 0.75f
        const val SWAY_DEG = 28f
        const val SWAY_S = 12f
        const val LINK_ALPHA = 0.35f
        const val NODE_ALPHA = 0.9f
        const val HALO_ALPHA = 0.3f
        const val SIGNAL_ALPHA = 0.95f
        /** Trips along a link a second. */
        const val SIGNAL_SPEED = 0.6f
    }
}
/** [c] as hue (degrees), saturation and value. */
private fun hsv(c: Color): FloatArray {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == c.red -> 60f * (((c.green - c.blue) / d) % 6f)
        max == c.green -> 60f * ((c.blue - c.red) / d + 2f)
        else -> 60f * ((c.red - c.green) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, if (max == 0f) 0f else d / max, max)
}
