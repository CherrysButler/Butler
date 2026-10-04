package com.cherry.butler.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The swap pop-up's own glyphs, drawn rather than borrowed: one 24-unit grid, one 1.8 dp
 * round-capped stroke, so the five read as a set. [behind] is the surface they sit on, used
 * to let the front frame cover the back one.
 */
enum class SwapGlyph { Pictures, Names, Crown, Tick, Cross }

@Composable
fun Glyph(glyph: SwapGlyph, color: Color, behind: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        // Whole-pixel stroke, and every point snapped to the pixel grid (to pixel centres for an
        // odd stroke), so straight edges land on one row of pixels instead of smearing over two.
        val width = 1.8.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
        val half = if (width.toInt() % 2 == 1) 0.5f else 0f
        fun snap(v: Float) = (v * u - half).roundToInt() + half
        val stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(snap(x), snap(y))
        fun path(vararg points: Pair<Float, Float>, close: Boolean = false) = Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(snap(x), snap(y)) else lineTo(snap(x), snap(y)) }
            if (close) close()
        }
        fun box(x: Float, y: Float, w: Float, h: Float) = p(x, y) to Size(snap(x + w) - snap(x), snap(y + h) - snap(y))
        when (glyph) {
            SwapGlyph.Pictures -> {
                // Two frames, one over the other: the back one, then the front one with a hill and a sun.
                box(8f, 4f, 12f, 11f).let { (at, size) -> frame(at, size, u, color, behind, stroke, fill = false) }
                box(4f, 9f, 12f, 11f).let { (at, size) -> frame(at, size, u, color, behind, stroke, fill = true) }
                drawPath(path(6.5f to 17.5f, 9.5f to 14f, 13.5f to 17.5f), color, style = stroke)
                drawCircle(color, radius = 1.1f * u, center = p(12.5f, 12.5f))
            }
            SwapGlyph.Names -> {
                // A name tag: the label, its eyelet, and two lines of writing.
                drawPath(path(3f to 7f, 16f to 7f, 21f to 12f, 16f to 17f, 3f to 17f, close = true), color, style = stroke)
                drawCircle(color, radius = 1.1f * u, center = p(16f, 12f))
                drawPath(path(6.5f to 10.5f, 11.5f to 10.5f), color, style = stroke)
                drawPath(path(6.5f to 13.5f, 9.5f to 13.5f), color, style = stroke)
            }
            SwapGlyph.Crown -> {
                drawPath(path(4f to 17f, 4f to 8f, 9f to 12f, 12f to 5f, 15f to 12f, 20f to 8f, 20f to 17f, close = true), color, style = stroke)
                drawPath(path(4f to 20.5f, 20f to 20.5f), color, style = stroke)
            }
            SwapGlyph.Tick -> drawPath(path(5f to 12f, 10f to 17f, 19f to 7f), color, style = stroke)
            SwapGlyph.Cross -> {
                drawPath(path(6.5f to 6.5f, 17.5f to 17.5f), color, style = stroke)
                drawPath(path(17.5f to 6.5f, 6.5f to 17.5f), color, style = stroke)
            }
        }
    }
}

private fun DrawScope.frame(at: Offset, size: Size, u: Float, color: Color, behind: Color, stroke: Stroke, fill: Boolean) {
    val radius = CornerRadius(2.5f * u)
    if (fill) drawRoundRect(behind, topLeft = at, size = size, cornerRadius = radius)
    drawRoundRect(color, topLeft = at, size = size, cornerRadius = radius, style = stroke)
}
