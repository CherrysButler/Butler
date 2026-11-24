package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.LocalDepth
import com.cherry.butler.core.design.LocalDepthLook
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.SmoothCornerShape
import io.github.fletchmckee.liquid.LiquidScope
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.liquid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Glass chrome (Depth Lifted): the frame (the floating nav bar, the chat's top bar and
 * composer) is glass over what passes under it.
 *
 * The glass is Apple's, as nearly as one pass over one blurred picture can make it: the steps
 * of QuartzCore's own glass shader (macOS 26's `glass_background_lpf`, recovered and written
 * out in github.com/spacedouut/liquid-glass) with the numbers AppKit gives a regular pane. The
 * shader is in Liquid's sources (LiquidShader.kt), which says what each step does; this file
 * gives it the recipe for a pane of a given size in a light or a dark look.
 *
 * Only the frame: text you read never sits on glass. Off on battery saver, where Butler draws
 * the frame solid instead. Android 12 frosts without the lens; before it nothing can blur
 * behind a view, so the frame is its fill.
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
 * Which way the glass is turned to the light: the phone's tilt, or the pointer's place in a PC
 * window, each -1 to 1 with 0 at rest. The rim's two lights turn with it, and with them the
 * gaps between them. Null where nothing moves it.
 */
val LocalGlassLight = compositionLocalOf<State<Offset>?> { null }

/*
 * A regular pane's recipe, from macOS 26 (qcRecipe in spacedouut/liquid-glass, read off a live
 * NSGlassEffectView). Lengths are points, which are Butler's dp.
 */

/** The rim: how deep it runs in from the edge, and how far the picture is pulled at the edge itself. */
private const val RIM_DEPTH = 20f
private const val RIM_PULL = 60f

/** How much of the outward-bent picture is laid over the last point of the rim. */
private const val OUTER_MIX = 0.3f

/**
 * Colour fringing at the rim: how far apart red and blue are read at the edge. Apple leaves
 * this off on macOS; 3 is the step its playground starts from when it is turned on.
 */
private const val FRINGE = 3f

/** The rim highlight's strength: half way to white where an edge faces one of its two lights. */
private const val HIGHLIGHT = 0.5f

/**
 * Apple's blur for a pane whose short side is [side] dp, as the radius Compose's blur takes.
 * Apple's number is 4 for a pane 160 across or more and falls to 4/3 for the smallest, and its
 * blur is 1.6 times that, read off a pyramid of ever smaller copies. Worked through, that
 * pyramid softens about as a blur of 2.1 times the number does here.
 */
private fun frostFor(side: Float): Dp = (2.1f * (4f - (160f - side) / 42f).coerceIn(4f / 3f, 4f)).dp

/**
 * The face of a pane [side] dp across its short side: the band its light and dark are brought
 * into (black, white), and the fill laid over that. Dark: everything behind lands between a
 * fifth and three fifths of white, under black two fifths thick. Light: between a half and
 * white under white two fifths thick; a pane under 64 is cleaner, as Apple's small ones are.
 */
private class Face(val black: Float, val white: Float, val fill: Color)

private fun faceFor(side: Float, dark: Boolean): Face = when {
    dark -> Face(0.2f, 0.6f, Color.Black.copy(alpha = 0.4f))
    side < 64f -> Face(0.85f, 1.03f, Color.White.copy(alpha = 0.25f))
    else -> Face(0.5f, 1.03f, Color.White.copy(alpha = 0.4f))
}

/**
 * Where the rim's key light comes from, turned by [tilt]: the lower end corner at rest, with
 * its fill opposite at the upper start, as on a macOS pane. Each lights the rim that faces it
 * and dies away 80 degrees round, so the upper end and the lower start of the rim, square
 * between the two, are left dark. A unit vector, y down.
 */
private fun keyLight(tilt: Offset): Offset {
    val turn = (tilt.x * 0.6f + tilt.y * 0.35f).toDouble()
    val angle = Math.PI / 4 + turn
    return Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
}

/**
 * A finger on the glass, answered the way Apple describes theirs: it flexes at once and is
 * energized with light, the light starting under the fingertip and spreading. Where the finger
 * is, how brightly the glass is lit for it, how far that light has spread, and how far the
 * pane has swelled. Its size is kept here too, for the recipe.
 */
@Stable
private class GlassTouch {
    var at by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    val power = Animatable(0f)
    val spread = Animatable(0f)
    val flex = Animatable(0f)

    /** Watched on the way down and never taken: what is on the glass is pressed as before. */
    fun watch(scope: CoroutineScope): Modifier = Modifier
        .pointerInput(this) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                at = down.position
                scope.launch { power.animateTo(1f, tween(120)) }
                scope.launch { spread.snapTo(0f); spread.animateTo(1f, tween(520, easing = Motion.Glide)) }
                // Gel: it swells quickly and settles with a little give.
                scope.launch { flex.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val held = event.changes.firstOrNull { it.pressed } ?: break
                    at = held.position
                }
                scope.launch { power.animateTo(0f, tween(560, easing = Motion.EaseOut)) }
                scope.launch { flex.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
            }
        }
}

/** How much a pressed pane gains along its longer side; small keys swell more for their size than wide bars. */
private val FLEX_GAIN = 6.dp
private const val FLEX_MOST = 0.06f
/** How wide the light is under the finger before it spreads, and once it has. */
private val GLOW_START = 36.dp
private val GLOW_MOST = 104.dp
/** Panes this slim swell in full under a touch; from [FLEX_NONE] on they keep still, as a wide sheet of glass would. */
private val FLEX_FULL = 60.dp
private val FLEX_NONE = 160.dp

/**
 * A floating pane of glass over [state]'s content, whole: lifted by its shadow, its rim lit,
 * and answering a touch by swelling a little and lighting from inside (see [GlassTouch]).
 * [still] glass does neither: what is written in (the message box) must not move or redraw
 * under the caret, which flickered while the glass answered every touch on it.
 *
 * The pane is cut with round corners even where [shape] has Butler's continuous ones: the
 * shader measures a round corner, and a rim lit along one curve and cut along another lost its
 * light at every corner.
 */
@Composable
fun Modifier.glassSurface(state: LiquidState, shape: Shape, lift: Lift = Lift.Float, still: Boolean = false): Modifier {
    val touch = remember { GlassTouch() }
    val look = LocalDepthLook.current
    val cut = (shape as? SmoothCornerShape)?.rounded() ?: shape
    val gain = with(LocalDensity.current) { FLEX_GAIN.toPx() }
    val flexFull = with(LocalDensity.current) { FLEX_FULL.toPx() }
    val flexNone = with(LocalDensity.current) { FLEX_NONE.toPx() }
    val swell = if (still) Modifier else Modifier.graphicsLayer {
        val slim = ((flexNone - size.minDimension) / (flexNone - flexFull)).coerceIn(0f, 1f)
        val scale = 1f + (gain / size.maxDimension.coerceAtLeast(1f)).coerceAtMost(FLEX_MOST) * slim * touch.flex.value
        scaleX = scale
        scaleY = scale
    }
    return this
        // Outermost, so the shadow swells with the glass.
        .then(swell)
        .liftShadow(cut, look, lift)
        .clip(cut)
        .frosted(state, cut, touch, watched = !still)
}

/** What the frame's glass looks through: the content under it. */
fun Modifier.frostSource(state: LiquidState?): Modifier = if (state == null) this else liquefiable(state)

/** A strip of glass across the screen over [state]'s content; plain when there is nothing under it. */
@Composable
fun Modifier.frosted(state: LiquidState?, shape: Shape = RectangleShape): Modifier =
    if (state == null) this else frosted(state, shape, remember { GlassTouch() }, watched = true)

@Composable
private fun Modifier.frosted(state: LiquidState, shape: Shape, touch: GlassTouch, watched: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val tilt = LocalGlassLight.current
    val density = LocalDensity.current.density
    val start = with(LocalDensity.current) { GLOW_START.toPx() }
    val most = with(LocalDensity.current) { GLOW_MOST.toPx() }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Light added to a pale pane shows less than on a dark one.
    val glowPower = if (dark) 0.11f else 0.14f
    // The rim belongs to a floating shape. A strip across the screen has no edge to bend or
    // light: with them its rim shows as a box drawn round the bar. It is only the face.
    val floating = shape != RectangleShape
    val block: LiquidScope.() -> Unit = remember(shape, touch, tilt, density, start, most, dark, glowPower, floating) {
        {
            this.shape = shape
            // The pane's short side in dp: Apple sizes the blur, the face and the outer bend by it.
            val least = minOf(touch.size.width, touch.size.height) / density
            val side = if (least > 0f) least else 56f
            val face = faceFor(side, dark)
            frost = frostFor(side)
            // Without a blur what is behind would read through the pane: nearly solid then.
            tint = if (canFrost) face.fill else face.fill.copy(alpha = 0.94f)
            range = Offset(face.black, face.white)
            saturation = 1f
            refraction = if (floating) RIM_DEPTH else 0f
            curve = if (floating) RIM_PULL else 0f
            outer = if (floating) OUTER_MIX else 0f
            dispersion = if (floating) FRINGE else 0f
            edge = if (floating) HIGHLIGHT else 0f
            // Read here, so the glass alone is redrawn when any of them changes.
            light = keyLight(tilt?.value ?: Offset.Zero)
            glowAt = touch.at
            // From a spot under the finger outward, a little way: over a whole large pane it
            // was a flash, and spread thin over a bar it could not be seen at all.
            val reach = start + (most - start) * touch.spread.value
            glow = Offset(reach, touch.power.value * glowPower)
        }
    }
    return this
        .onSizeChanged { touch.size = it }
        .then(if (watched) touch.watch(scope) else Modifier)
        .liquid(state, block)
}

/** A gap the size of the frame at a list's foot. */
@Composable
fun bottomChrome(): Dp = LocalBottomChrome.current
