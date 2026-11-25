package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.fletchmckee.liquid.internal.StillGlass
import io.github.fletchmckee.liquid.internal.normalizedCornerRadii
import io.github.fletchmckee.liquid.internal.stillGlassSupported
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

/**
 * The glass's numbers: the one place they are written. Every pane of glass in Butler is made
 * from these, whether it floats over a moving picture ([glassSurface]) or stands on plain
 * ground ([glassPane]); the tuner (GlassTuner.kt) starts from them and nothing else repeats
 * them. Where they come from, and why the pull is not Apple's, is in docs/LIQUID_GLASS.md.
 * Lengths are points, which are Butler's dp.
 */
object Glass {
    /** How deep the rim runs in from the edge. Apple's. */
    const val RIM_DEPTH = 20f

    /**
     * How far the picture is pulled at the rim's edge. Apple's is 60, which on a bar as slim as
     * Butler's reaches the far edge and shows it, flipped, in the near one; 35 is where the bend
     * still reads and that stops (found by eye on a phone).
     */
    const val RIM_PULL = 35f

    /** How much of the outward-bent picture is laid over the last point of the rim. Apple's. */
    const val OUTER_MIX = 0.3f

    /** Colour fringing at the rim, in points at the edge. Off, as Apple leaves it. */
    const val FRINGE = 0f

    /** The rim highlight: half way to white where an edge faces one of its two lights. Apple's. */
    const val HIGHLIGHT = 0.5f

    /** The blur's radius. Under 0 it would be Apple's own, by the pane's size ([frostFor]). */
    const val BLUR = 3f

    /**
     * The blur behind a pane that has opened out to be written in (the message box with the
     * keyboard up): words sit on it then, over whatever the chat is showing. It rises from
     * [BLUR] to this as the pane opens.
     */
    const val BLUR_OPEN = 12f

    /**
     * How far short of the two unlit corners a box's rim light falls. 0 is Apple's: lit by the
     * way the edge faces, which on a box leaves only the corner arcs dark. At 1 the light dies
     * along the sides as on a round pane; past 1 sooner still. For panes on plain ground
     * (cards, fields, chips); the bars keep Apple's.
     */
    const val RIM_GAPS = 1.5f

    /**
     * A bot's picture under its card's glass: the soft copy's width in pixels, and how far it
     * is blurred, in those pixels. Small and soft enough that it reads as colour and light,
     * not as a second picture.
     */
    const val LEAK_WIDTH = 32
    const val LEAK_SOFT = 2.2f
}

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
fun Modifier.glassSurface(
    state: LiquidState,
    shape: Shape,
    lift: Lift = Lift.Float,
    still: Boolean = false,
    /** How far the pane has opened out to be written in, 0 to 1: its blur rises with it ([Glass.BLUR_OPEN]). */
    opened: (() -> Float)? = null,
    /** Pull added to the pane's own for a moment, in points: a lens that swells and settles (GlassKey). */
    swell: (() -> Float)? = null,
): Modifier {
    val touch = remember { GlassTouch() }
    val look = LocalDepthLook.current
    val cut = (shape as? SmoothCornerShape)?.rounded() ?: shape
    val gain = with(LocalDensity.current) { FLEX_GAIN.toPx() }
    val flexFull = with(LocalDensity.current) { FLEX_FULL.toPx() }
    val flexNone = with(LocalDensity.current) { FLEX_NONE.toPx() }
    val flexing = if (still) Modifier else Modifier.graphicsLayer {
        val slim = ((flexNone - size.minDimension) / (flexNone - flexFull)).coerceIn(0f, 1f)
        val scale = 1f + (gain / size.maxDimension.coerceAtLeast(1f)).coerceAtMost(FLEX_MOST) * slim * touch.flex.value
        scaleX = scale
        scaleY = scale
    }
    return this
        // Outermost, so the shadow swells with the glass.
        .then(flexing)
        .liftShadow(cut, look, lift)
        .clip(cut)
        .frosted(state, cut, touch, watched = !still, opened = opened, swell = swell)
}

/**
 * Whether surfaces are glass now: Depth Lifted, not on battery saver, and a device that can
 * run the shader as a paint. One answer for every card, chip and field (and nothing else
 * asks the question its own way).
 */
@Composable
fun glassAvailable(): Boolean = glassOn() && stillGlassSupported

/**
 * A pane of glass standing on plain ground: a card, a chip, a field. The same shader and the
 * same numbers as the glass over a moving picture, given the ground's one colour for what is
 * behind it, so the two are one glass. There is nothing to bend on plain ground, so what
 * shows is the glass's face and the light on its rim.
 *
 * [lift] null leaves off the shadow (a small chip inside a card has none). [tint] washes the
 * pane with a colour, for a pane that is a colour's own: a chosen chip. [picture] gives the
 * pane a picture of its own to look through in place of the ground (a bot's, under its card):
 * then there is something to bend, and the rim bends it as the frame's glass does. Call it
 * only where [glassAvailable]; elsewhere it draws nothing.
 */
fun Modifier.glassPane(
    shape: Shape,
    lift: Lift? = Lift.Rest,
    clip: Boolean = true,
    tint: Color = Color.Unspecified,
    picture: (() -> GlassPicture?)? = null,
): Modifier = composed {
    val cut = (shape as? SmoothCornerShape)?.rounded() ?: shape
    val look = LocalDepthLook.current
    val ground = MaterialTheme.colorScheme.background
    val dark = ground.luminance() < 0.5f
    val direction = LocalLayoutDirection.current
    val still = remember { StillGlass() }
    (if (lift != null) liftShadow(cut, look, lift) else Modifier)
        .then(if (clip) Modifier.clip(cut) else Modifier)
        .drawWithCache {
            val face = faceFor(size.minDimension / density, dark)
            // Read here, so the pane alone is redrawn when its picture arrives or moves.
            val seen = picture?.invoke()
            still.look(seen?.image, seen?.slot ?: androidx.compose.ui.geometry.Rect.Zero, GlassTuning.depth, GlassTuning.pull, GlassTuning.outer)
            still.set(
                size = size,
                cornerRadii = cut.normalizedCornerRadii(size, this, direction),
                density = density,
                ground = ground,
                fill = face.fill,
                black = face.black,
                white = face.white,
                highlight = Glass.HIGHLIGHT,
                // At rest: a card does not turn its light with every tilt of the phone.
                light = keyLight(Offset.Zero),
                place = GlassTuning.gaps,
            )
            val wash = if (tint.isSpecified) cut.createOutline(size, direction, this) else null
            onDrawBehind {
                still.shader()?.let { drawRect(ShaderBrush(it)) }
                if (wash != null) drawOutline(wash, tint)
            }
        }
        // On light ground the rim's light is white on white. A pane with a shadow is told
        // from the ground by it; one without is given a hairline.
        .then(if (!dark && lift == null) Modifier.rim(1.dp, Color.Black.copy(alpha = 0.09f), cut) else Modifier)
}

/** What the frame's glass looks through: the content under it. */
fun Modifier.frostSource(state: LiquidState?): Modifier = if (state == null) this else liquefiable(state)

/** A strip of glass across the screen over [state]'s content; plain when there is nothing under it. */
@Composable
fun Modifier.frosted(state: LiquidState?, shape: Shape = RectangleShape): Modifier =
    if (state == null) this else frosted(state, shape, remember { GlassTouch() }, watched = true)

@Composable
private fun Modifier.frosted(
    state: LiquidState,
    shape: Shape,
    touch: GlassTouch,
    watched: Boolean,
    opened: (() -> Float)? = null,
    swell: (() -> Float)? = null,
): Modifier {
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
    val block: LiquidScope.() -> Unit = remember(shape, touch, tilt, density, start, most, dark, glowPower, floating, opened, swell) {
        {
            this.shape = shape
            // The pane's short side in dp: Apple sizes the blur, the face and the outer bend by it.
            val least = minOf(touch.size.width, touch.size.height) / density
            val side = if (least > 0f) least else 56f
            val face = faceFor(side, dark)
            // Read from the tuner while it exists (GlassTuner.kt); its numbers start as these.
            val rest = if (GlassTuning.blur < 0f) frostFor(side) else GlassTuning.blur.dp
            val open = opened?.invoke()?.coerceIn(0f, 1f) ?: 0f
            frost = if (open > 0f && GlassTuning.blurOpen.dp > rest) rest + (GlassTuning.blurOpen.dp - rest) * open else rest
            // Without a blur what is behind would read through the pane: nearly solid then.
            tint = if (canFrost) face.fill else face.fill.copy(alpha = 0.94f)
            range = Offset(face.black, face.white)
            saturation = 1f
            refraction = if (floating) GlassTuning.depth else 0f
            curve = if (floating) (GlassTuning.pull + (swell?.invoke() ?: 0f)).coerceAtLeast(0f) else 0f
            outer = if (floating) GlassTuning.outer else 0f
            dispersion = if (floating) GlassTuning.fringe else 0f
            edge = if (floating) GlassTuning.highlight else 0f
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
