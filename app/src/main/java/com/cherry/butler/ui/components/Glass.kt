package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.material3.MaterialTheme
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

/** A gap the size of the frame at a list's foot. */
@Composable
fun bottomChrome(): Dp = LocalBottomChrome.current
