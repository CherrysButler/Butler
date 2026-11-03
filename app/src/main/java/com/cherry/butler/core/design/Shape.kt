package com.cherry.butler.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, but with an edge: marks at 4, chips and fields at 8, cards and keys at 12,
 * grouped sections at 14, the composer at 18. Janitor's anatomy, drawn a notch harder.
 * Pills are [Pill], kept for what is a tag or a count, never for a key or a field.
 */
val ButlerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

val Pill = RoundedCornerShape(50)

/**
 * A sheet's top corners: 20 dp, or with Depth Lifted 28 dp continuous ones (16 on Lights out,
 * which keeps its tighter geometry), where everything is a step softer.
 */
val SheetShape: androidx.compose.ui.graphics.Shape
    @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = when {
        LocalDepth.current != Depth.Lifted -> FlatSheetShape
        androidx.compose.material3.MaterialTheme.shapes === LightsOutLiftedShapes -> LightsOutSheetShape
        else -> LiftedSheetShape
    }

private val FlatSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
private val LiftedSheetShape = SmoothCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val LightsOutSheetShape = SmoothCornerShape(topStart = 16.dp, topEnd = 16.dp)

/**
 * Depth Lifted: the same set a step softer and drawn with continuous corners (SmoothCornerShape),
 * the curve easing out of each edge rather than a quarter circle on a box: marks 6, fields 10,
 * cards and keys 16, sections 20, the composer and floating frames 28.
 */
val LiftedShapes = Shapes(
    extraSmall = SmoothCornerShape(6.dp),
    small = SmoothCornerShape(10.dp),
    medium = SmoothCornerShape(16.dp),
    large = SmoothCornerShape(20.dp),
    extraLarge = SmoothCornerShape(28.dp),
)

/** Lights out: near-square, as the true-black world was drawn. Sheets and pills keep their own shapes. */
val LightsOutShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp),
)

/** Lights out with Depth Lifted: still the tightest set, but continuous, machined rather than cut. */
val LightsOutLiftedShapes = Shapes(
    extraSmall = SmoothCornerShape(4.dp),
    small = SmoothCornerShape(6.dp),
    medium = SmoothCornerShape(10.dp),
    large = SmoothCornerShape(12.dp),
    extraLarge = SmoothCornerShape(16.dp),
)

/**
 * Butler's corners scaled by [factor]: 0 square, 1 as [ButlerShapes], 2 twice as round.
 * The Custom look's "Corners" setting. [smooth] draws them continuous (Depth Lifted).
 */
fun scaledShapes(factor: Float, smooth: Boolean = false): Shapes {
    val f = factor.coerceIn(0f, 2.6f)
    fun c(r: Int): androidx.compose.foundation.shape.CornerBasedShape =
        if (smooth) SmoothCornerShape((r * f).dp) else RoundedCornerShape((r * f).dp)
    return Shapes(extraSmall = c(4), small = c(8), medium = c(12), large = c(14), extraLarge = c(18))
}

/** How much softer Lifted rounds the Custom look's own corners. */
internal const val LIFTED_CORNERS = 1.3f
