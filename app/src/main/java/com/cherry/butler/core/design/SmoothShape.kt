package com.cherry.butler.core.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A rectangle with continuous corners: each corner eases out of its straight edge into the arc
 * instead of meeting it at a hard joint, the curvature of premium hardware and app icons rather
 * than a quarter circle stuck on a box. [smoothing] is how far the easing reaches (0 is a plain
 * rounded corner, 1 the fullest); 0.6 is the iOS-like default. The geometry is Figma's corner
 * smoothing (as figma-squircle, MIT, computes it): for a corner of radius R, the curve begins
 * (1 + smoothing) × R from the corner, runs a cubic into a shorter circular arc, and a mirrored
 * cubic out. Where two corners would overlap, the smoothing gives way first, then the radius.
 *
 * A [CornerBasedShape], so it slots into Material's Shapes and every component that reads them.
 */
class SmoothCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = DEFAULT_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(radius: Dp, smoothing: Float = DEFAULT_SMOOTHING) :
        this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

    constructor(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
        smoothing: Float = DEFAULT_SMOOTHING,
    ) : this(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize): CornerBasedShape =
        SmoothCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd
        val w = size.width
        val h = size.height
        val budget = min(w, h) / 2f
        val path = Path()
        // Clockwise from the top edge: each corner given its corner point, the direction the
        // path arrives along (u) and the one it leaves along (v).
        val corners = listOf(
            Corner(tr, Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f)),
            Corner(br, Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f)),
            Corner(bl, Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f)),
            Corner(tl, Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f)),
        )
        corners.forEachIndexed { i, c -> c.draw(path, budget, smoothing, first = i == 0) }
        path.close()
        return Outline.Generic(path)
    }

    /**
     * The plain rounded rectangle at the same radii: what a shadow is cast from. Under a blur
     * the two can't be told apart, and Android before API 29 draws an elevation shadow only
     * under an outline its convexity test passes, which the eased curves can fail by a
     * rounding hair. A rounded rectangle is also the platform's fast path.
     */
    fun rounded(): androidx.compose.foundation.shape.RoundedCornerShape =
        androidx.compose.foundation.shape.RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun equals(other: Any?): Boolean =
        other is SmoothCornerShape && topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart && smoothing == other.smoothing

    override fun hashCode(): Int =
        ((((topStart.hashCode() * 31 + topEnd.hashCode()) * 31 + bottomEnd.hashCode()) * 31 + bottomStart.hashCode()) * 31) + smoothing.hashCode()

    override fun toString(): String = "SmoothCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart, smoothing=$smoothing)"

    private class Corner(val radius: Float, val k: Offset, val u: Offset, val v: Offset) {
        /** The point [x] along u and [y] along v from the corner point. */
        private fun at(x: Float, y: Float) = Offset(k.x + u.x * x + v.x * y, k.y + u.y * x + v.y * y)

        fun draw(path: Path, budget: Float, smoothingWanted: Float, first: Boolean) {
            val r = min(radius, budget)
            if (r <= 0f) {
                if (first) path.moveTo(k.x, k.y) else path.lineTo(k.x, k.y)
                return
            }
            // The smoothing gives way where the corner would reach past half the shorter side.
            val smoothing = min(smoothingWanted, (budget / r - 1f).coerceAtLeast(0f))
            val p = min((1f + smoothing) * r, budget)
            val arcMeasure = 90f * (1f - smoothing)
            val arcSection = sin(rad(arcMeasure / 2f)) * r * sqrt(2f)
            val alpha = (90f - arcMeasure) / 2f
            val p3ToP4 = r * tan(rad(alpha / 2f))
            val beta = 45f * smoothing
            val c = p3ToP4 * cos(rad(beta))
            val d = c * tan(rad(beta))
            val b = (p - arcSection - c - d) / 3f
            val a = 2f * b

            val start = at(-p, 0f)
            if (first) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
            // Out of the straight edge into the arc.
            val c1 = at(-p + a, 0f)
            val c2 = at(-p + a + b, 0f)
            val e1 = at(-p + a + b + c, d)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            // The arc, around the corner's circle, as one cubic that meets both curves exactly
            // (an arcTo would join them with a hair of a line).
            val sx = -p + a + b + c
            val sy = d
            val x2 = sx + arcSection
            val y2 = sy + arcSection
            if (arcMeasure > 0.01f) {
                val k = 4f / 3f * tan(rad(arcMeasure / 4f))
                // The tangent where a clockwise path crosses a point on the circle: its radius turned 90°.
                val h1 = at(sx - (sy - r) * k, sy + (sx + r) * k)
                val h2 = at(x2 + (y2 - r) * k, y2 - (x2 + r) * k)
                val e = at(x2, y2)
                path.cubicTo(h1.x, h1.y, h2.x, h2.y, e.x, e.y)
            }
            // And out of the arc into the next straight edge.
            val c3 = at(x2 + d, y2 + c)
            val c4 = at(x2 + d, y2 + b + c)
            val e2 = at(0f, p)
            path.cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }

        private fun rad(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)
    }

    companion object {
        /** iOS-like continuous corners. */
        const val DEFAULT_SMOOTHING = 0.6f
    }
}
