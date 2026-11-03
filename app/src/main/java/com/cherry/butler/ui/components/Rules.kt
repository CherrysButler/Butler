package com.cherry.butler.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.DepthLook
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.LocalDepth
import com.cherry.butler.core.design.LocalDepthLook
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One device pixel, whatever the density: the width of every rule in the app. */
@Composable
fun hairline(): Dp = with(LocalDensity.current) { 1.toDp() }

/** A rule across the full width. The structure of every list is made of these. */
@Composable
fun HairlineRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    val px = hairline()
    Canvas(modifier = modifier.fillMaxWidth().height(px)) {
        drawRect(color)
    }
}

/**
 * A thin rounded outline that also clips what follows it, so a background or ripple set
 * after it keeps the same corners.
 */
fun Modifier.hairlineFrame(color: Color, shape: Shape? = null): Modifier = composed {
    val s = shape ?: MaterialTheme.shapes.small
    clip(s).border(1.dp, color, s)
}

/**
 * A card: the lifted surface with a hairline edge. With Depth Flat the tone step and hairline do
 * all the work. With Depth Lifted ([LocalDepth]) light comes from above, drawn in what the look
 * can show ([LocalDepthLook]): the hairline brightens along the card's top edge (its rim), the
 * fill is a touch lighter at its top, and a soft contact shadow sits under it, as deep as its
 * [lift]. The shadow is drawn once by the platform, not every frame.
 */
fun Modifier.card(shape: Shape? = null, color: Color? = null, clip: Boolean = true, lift: Lift = Lift.Rest): Modifier = composed {
    val s = shape ?: MaterialTheme.shapes.medium
    val outline = ButlerTheme.colors.cardOutline
    val fill = color ?: MaterialTheme.colorScheme.surfaceContainer
    val lifted = LocalDepth.current == Depth.Lifted
    val look = LocalDepthLook.current
    val raise = if (lifted) liftShadow(s, look, lift) else Modifier
    // A clip costs a rounded mask on every frame; content that never reaches the corners
    // (the Browse card's picture sits between its name and footer) can skip it.
    val paint = if (lifted) litFill(fill, look, lift) else null
    val base = raise.then(
        when {
            paint != null && clip -> Modifier.clip(s).background(paint)
            paint != null -> Modifier.background(paint, s)
            clip -> Modifier.clip(s).background(fill)
            else -> Modifier.background(fill, s)
        },
    )
    // 1 dp, not one device pixel: a single pixel anti-aliases to nothing on a rounded corner.
    when {
        !outline.isSpecified -> base
        lifted -> base.border(1.dp, rimBrush(outline, look, lift), s)
        else -> base.border(1.dp, outline, s)
    }
}

/**
 * A soft, wide contact shadow under a raised surface (Depth Lifted), as deep as its [lift]:
 * deep under the slate looks, faint and cool on Daylight.
 */
fun Modifier.liftShadow(shape: Shape, look: DepthLook, lift: Lift = Lift.Rest): Modifier = shadow(
    elevation = lift.elevation,
    // A continuous corner casts its shadow from the rounded rectangle under it (see SmoothCornerShape.rounded).
    shape = (shape as? com.cherry.butler.core.design.SmoothCornerShape)?.rounded() ?: shape,
    clip = false,
    ambientColor = look.shadow.copy(alpha = look.ambient),
    spotColor = look.shadow.copy(alpha = look.spot),
)

/** The surface lit from above: a touch brighter at its top, its own tone by the middle. */
fun litFill(fill: Color, look: DepthLook, lift: Lift = Lift.Rest): Brush {
    val top = androidx.compose.ui.graphics.lerp(fill, Color.White, (look.lit * lift.rimBoost).coerceAtMost(0.9f))
    return Brush.verticalGradient(0f to top, 0.6f to fill, 1f to fill)
}

/** The hairline with light along its top edge: brighter at the rim, the card's own edge below. */
fun rimBrush(outline: Color, look: DepthLook, lift: Lift = Lift.Rest): Brush {
    val rim = androidx.compose.ui.graphics.lerp(outline, Color.White, (look.rim * lift.rimBoost).coerceAtMost(0.95f))
    return Brush.verticalGradient(0f to rim, 0.35f to outline, 1f to outline)
}

/**
 * Hides the last device pixel of what it wraps. A grouped card of rows uses it so the
 * divider under its last row doesn't show at the card's rounded bottom edge.
 */
fun Modifier.dropLastPixel(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - 1).coerceAtLeast(0)) { p.place(0, 0) }
}

/**
 * A smooth fade into [color] over the lower [coverage] of whatever is beneath, so text set
 * on the bottom of a picture stays readable. (The dot-dither fade is gone; [DitherTiles]
 * stays for the thinking animation.)
 */
fun Modifier.ditherFade(
    color: Color = Color.Black,
    coverage: Float = 0.6f,
): Modifier = drawWithCache {
    val start = size.height * (1f - coverage)
    val brush = Brush.verticalGradient(
        0f to color.copy(alpha = 0f),
        0.55f to color.copy(alpha = 0.55f),
        1f to color.copy(alpha = 0.92f),
        startY = start,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush, topLeft = Offset(0f, start), size = Size(size.width, size.height - start))
    }
}

/** The 4×4 Bayer matrix at seven densities, each as a repeating image shader. */
class DitherTiles(color: Color, dotPx: Int) {
    private val brushes: List<ShaderBrush>

    companion object {
        /** Seven dithered bands and one solid. */
        const val BANDS = 8
    }

    init {
        val bayer = intArrayOf(
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5,
        )
        // Thresholds that light 2, 4 … 14 of the 16 cells.
        brushes = (1 until BANDS).map { it * 2 }.map { lit ->
            val tile = ImageBitmap(4 * dotPx, 4 * dotPx)
            val canvas = androidx.compose.ui.graphics.Canvas(tile)
            val paint = Paint().apply { this.color = color }
            for (y in 0 until 4) for (x in 0 until 4) {
                if (bayer[y * 4 + x] < lit) {
                    canvas.drawRect(
                        left = (x * dotPx).toFloat(), top = (y * dotPx).toFloat(),
                        right = ((x + 1) * dotPx).toFloat(), bottom = ((y + 1) * dotPx).toFloat(),
                        paint = paint,
                    )
                }
            }
            ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        }
    }

    /** Levels 0..[levels]-1, sparse to dense. */
    fun brush(band: Int): ShaderBrush = brushes[band.coerceIn(0, brushes.lastIndex)]

    val levels: Int get() = brushes.size
}

@Composable
fun rememberDitherTiles(color: Color, dotPx: Int = 3): DitherTiles = remember(color, dotPx) { DitherTiles(color, dotPx) }

