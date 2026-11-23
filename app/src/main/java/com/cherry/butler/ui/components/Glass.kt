package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.LocalDepthLook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.LocalDepth
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import io.github.fletchmckee.liquid.LiquidScope
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid

/*
 * Glass chrome (Depth Lifted): the frame (the floating nav bar, the chat's top bar and
 * composer) is liquid glass over what passes under it: frosted, bent at its edges like a lens,
 * lit along its rim, colours a touch richer and split at the edge as through a prism (Liquid,
 * io.github.fletchmckee.liquid, kept in Butler's sources). Only the frame: text you read never
 * sits on glass. Off on battery saver, where Butler draws the frame solid instead. Android 12
 * frosts without the lens; before it nothing can blur behind a view, so the frame is its
 * tinted colour.
 */

/** Room a screen leaves at its foot for frame floating over it (the nav island), so the last row clears it. */
val LocalBottomChrome = compositionLocalOf { 0.dp }

/** Where a chat's background picture is frosted from: the chat's own, or the PC window's when it spreads across. */
val LocalBackdropHaze = compositionLocalOf<LiquidState?> { null }

/** Whether the frame frosts now: Depth Lifted and not on battery saver. */
@Composable
fun glassOn(): Boolean {
    if (LocalDepth.current != Depth.Lifted) return false
    val context = LocalContext.current
    return remember(context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        power?.isPowerSaveMode != true
    }
}

/** Whether this device can blur behind a view (Android 12 on; every PC). */
private val canFrost: Boolean get() = com.cherry.butler.core.platform.Device.canBlurBehind

/**
 * The frame's glass: a strong frost under the chrome tone, so what passes beneath reads as
 * light and never as text, with the lens at its edge. [shape] is the frame's own outline: the
 * glass bends and lights along it.
 */
@Composable
fun chromeGlass(shape: Shape): LiquidScope.() -> Unit {
    // A tone lighter than the ground, so the pane shows as a pane even with nothing under it.
    val chrome = ButlerTheme.colors.surfaceHigh
    // Without a blur what is under the frame would read through it: nearly solid then.
    val tint = chrome.copy(alpha = if (canFrost) GLASS_TINT else 0.94f)
    // The lens belongs to a floating shape. A strip across the screen (the chat's top bar) has no
    // edge to bend or light: with them its rim shows as a box drawn round the bar. It only frosts.
    val floating = shape != RectangleShape
    return remember(tint, shape) {
        {
            this.shape = shape
            frost = GLASS_FROST
            refraction = if (floating) GLASS_REFRACTION else 0f
            curve = if (floating) GLASS_CURVE else 0f
            edge = if (floating) GLASS_EDGE else 0f
            this.tint = tint
            saturation = GLASS_SATURATION
            dispersion = if (floating) GLASS_DISPERSION else 0f
        }
    }
}

private const val GLASS_TINT = 0.55f
private val GLASS_FROST = 14.dp
private const val GLASS_REFRACTION = 0.45f
private const val GLASS_CURVE = 0.35f
private const val GLASS_EDGE = 0.02f
private const val GLASS_SATURATION = 1.5f
private const val GLASS_DISPERSION = 0.2f

/** What the frame's glass looks through: the content under it. */
fun Modifier.frostSource(state: LiquidState?): Modifier = if (state == null) this else liquefiable(state)

/** The frame itself, glass over [state]'s content; plain when there is nothing under it. */
@Composable
fun Modifier.frosted(state: LiquidState?, shape: Shape = RectangleShape): Modifier =
    if (state == null) this else liquid(state, chromeGlass(shape))

/**
 * What a page's panes are glass over: its ambient ground ([AmbientGround]). Null, as nearly
 * everywhere, and a pane is a card.
 */
val LocalPaneGlass = compositionLocalOf<LiquidState?> { null }

/**
 * A pane of a page: a card, or glass over the page's ambient ground where it has one, lifted
 * and lit along its rim as a card is. Glass over flat ground shows nothing of itself, which is
 * why the ground is given light to pass under it.
 */
@Composable
fun Modifier.pane(shape: Shape? = null, color: Color? = null): Modifier {
    val glass = LocalPaneGlass.current ?: return card(shape = shape, color = color)
    val s = shape ?: MaterialTheme.shapes.medium
    val look = LocalDepthLook.current
    val outline = ButlerTheme.colors.cardOutline
    val lit = this.liftShadow(s, look, Lift.Rest).clip(s).frosted(glass, s)
    return if (outline.isSpecified) lit.rim(1.dp, rimBrush(outline, look, Lift.Rest), s) else lit
}

/**
 * Light on a page's ground for its glass to show: the accent, soft, from the upper corner, and
 * its fainter return low on the other side. It stays where it is while the page scrolls, so a
 * pane passing over it changes as glass does. [state] is what the panes look through.
 */
@Composable
fun AmbientGround(state: LiquidState, modifier: Modifier = Modifier) {
    val ground = MaterialTheme.colorScheme.background
    val light = MaterialTheme.colorScheme.primary
    // Light shows less on a pale ground, and a pale ground needs less of it.
    val strength = if (ground.luminance() < 0.5f) 0.36f else 0.22f
    Box(
        modifier.frostSource(state).drawBehind {
            drawRect(ground)
            drawRect(
                Brush.radialGradient(
                    0f to light.copy(alpha = strength),
                    0.45f to light.copy(alpha = strength * 0.4f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.95f, size.height * 0.08f),
                    radius = size.width * 1.05f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    0f to light.copy(alpha = strength * 0.55f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.02f, size.height * 0.72f),
                    radius = size.width * 0.85f,
                ),
            )
        },
    )
}

/** A gap the size of the frame at a list's foot. */
@Composable
fun bottomChrome(): Dp = LocalBottomChrome.current
