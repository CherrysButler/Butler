package com.cherry.butler.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How high a raised surface sits (Depth Lifted). Three heights, so what floats reads as floating:
 * - [Rest]: cards, tiles, grouped settings, fields. On the ground.
 * - [Float]: the frame that hovers over content: the nav island, the composer, banners, the PC's panel.
 * - [Overlay]: what comes over everything: sheets, menus, dialogs.
 */
enum class Lift(val elevation: Dp, val rimBoost: Float) {
    Rest(10.dp, 1f),
    Float(20.dp, 1.25f),
    Overlay(30.dp, 1.4f),
}

/**
 * How light falls on one look, so depth is drawn in what that look can show. Light comes from
 * above: a rim along a raised surface's top edge ([rim], how far its hairline moves toward white),
 * a fill a touch brighter at its top ([lit]), and a shadow under it ([ambient] and [spot] alpha,
 * in [shadow]'s colour).
 * - Night slate (Janitor Classic, a dark Custom): the shadow does most of the work.
 * - True black (Lights out): a shadow can't show on black, so the edges and fills carry it,
 *   luminous like a lit object in a dark room.
 * - Daylight: a faint, cool shadow and a bright white rim, as paper lifted toward a window.
 */
@Immutable
data class DepthLook(
    val rim: Float,
    val lit: Float,
    val ambient: Float,
    val spot: Float,
    val shadow: Color,
    /** Whether the look is light: the frost and washes read differently on white. */
    val light: Boolean,
) {
    companion object {
        /** What light does on a look whose ground is [ground]. */
        fun forGround(ground: Color): DepthLook {
            val l = ground.luminance()
            return when {
                l > 0.5f -> DepthLook(rim = 0.85f, lit = 0.65f, ambient = 0.09f, spot = 0.16f, shadow = Color(0xFF1A2233), light = true)
                l < 0.004f -> DepthLook(rim = 0.30f, lit = 0.09f, ambient = 0.55f, spot = 0.85f, shadow = Color.Black, light = false)
                else -> DepthLook(rim = 0.22f, lit = 0.055f, ambient = 0.40f, spot = 0.72f, shadow = Color.Black, light = false)
            }
        }
    }
}

val LocalDepthLook = staticCompositionLocalOf { DepthLook.forGround(Color(0xFF2B2E33)) }

/** A shape at [radius]: continuous corners with Depth Lifted, plain rounded ones with Flat. */
@Composable
@ReadOnlyComposable
fun liftedShape(radius: Dp): Shape =
    if (LocalDepth.current == Depth.Lifted) SmoothCornerShape(radius) else RoundedCornerShape(radius)

/** A shape with its own corner on each side, continuous with Depth Lifted. */
@Composable
@ReadOnlyComposable
fun liftedShape(topStart: Dp = 0.dp, topEnd: Dp = 0.dp, bottomEnd: Dp = 0.dp, bottomStart: Dp = 0.dp): Shape =
    if (LocalDepth.current == Depth.Lifted) SmoothCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    else RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
