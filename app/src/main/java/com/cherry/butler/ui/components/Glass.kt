package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.LocalDepthLook
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.cherry.butler.core.design.Motion
import kotlinx.coroutines.launch
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
fun chromeGlass(shape: Shape, clear: Boolean = false): LiquidScope.() -> Unit {
    // A tone lighter than the ground, so the pane shows as a pane even with nothing under it.
    val chrome = ButlerTheme.colors.surfaceHigh
    // Without a blur what is under the frame would read through it: nearly solid then.
    val tint = chrome.copy(alpha = if (!canFrost) 0.94f else if (clear) CLEAR_TINT else GLASS_TINT)
    // The lens belongs to a floating shape. A strip across the screen (the chat's top bar) has no
    // edge to bend or light: with them its rim shows as a box drawn round the bar. It only frosts.
    val floating = shape != RectangleShape
    return remember(tint, shape, clear) {
        {
            this.shape = shape
            frost = if (clear) CLEAR_FROST else GLASS_FROST
            refraction = if (floating) GLASS_REFRACTION else 0f
            curve = if (floating) { if (clear) CLEAR_CURVE else GLASS_CURVE } else 0f
            edge = if (floating) GLASS_EDGE else 0f
            this.tint = tint
            saturation = GLASS_SATURATION
            dispersion = if (floating) GLASS_DISPERSION else 0f
        }
    }
}

private const val GLASS_TINT = 0.5f
/**
 * A page's panes (the settings): clear glass, not the frame's frosted kind. The frame frosts
 * hard because a story's words pass under it; under a pane there is only the page's quiet
 * ground, and frosted that hard the pane went milky and stopped reading as glass at all. A
 * thin tint, the ground behind only softened, and more bend at the rim.
 */
private const val CLEAR_TINT = 0.18f
private val CLEAR_FROST = 1.5.dp
private const val CLEAR_CURVE = 0.45f
/**
 * How deep a clear pane's rim bends what is behind it. A fixed depth, not the frame's share of
 * the pane's size: on a card as tall as a page, a share reached halfway in and dragged the
 * ground into streaks.
 */
private val CLEAR_RIM = 14.dp
private val GLASS_FROST = 12.dp
private const val GLASS_REFRACTION = 0.45f
private const val GLASS_CURVE = 0.38f
private const val GLASS_EDGE = 0.02f
private const val GLASS_SATURATION = 1.6f
private const val GLASS_DISPERSION = 0.22f
/** How much the tint gives way to what is behind it (LiquidScope.adapt). */
private const val GLASS_ADAPT = 0.8f
/** The rim light's strength. */
private const val GLASS_SHINE = 0.3f

/**
 * Which way the glass is turned to the light: the phone's tilt, or the pointer's place in a PC
 * window, each -1 to 1 with 0 at rest. The rim's highlight travels round the glass with it.
 * Null where nothing moves it: the light stays above.
 */
val LocalGlassLight = compositionLocalOf<State<Offset>?> { null }

/** Light from above and a little to the start side, turned by [tilt]. */
private fun lightFrom(tilt: Offset): Offset {
    val x = -0.55f + 1.5f * tilt.x
    val y = -0.85f + 1.1f * tilt.y
    val length = kotlin.math.sqrt(x * x + y * y).coerceAtLeast(0.001f)
    return Offset(x / length * GLASS_SHINE, y / length * GLASS_SHINE)
}

/**
 * A finger on the glass, answered the way Apple describes theirs: it flexes at once and is
 * energized with light, the light starting under the fingertip and spreading through the
 * element. Where the finger is, how brightly the glass is lit for it, how far that light has
 * spread (0 under the finger, 1 the whole pane), and how far the pane has swelled.
 */
@Stable
private class GlassTouch {
    var at by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    val power = Animatable(0f)
    val spread = Animatable(0f)
    val flex = Animatable(0f)

    /** Watched on the way down and never taken: what is on the glass is pressed as before. */
    fun watch(scope: CoroutineScope): Modifier = Modifier
        .onSizeChanged { size = it }
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
 * A floating pane of glass over [state]'s content, whole: lifted by its shadow, lit along its
 * rim, and answering a touch by swelling a little and lighting from inside (see [GlassTouch]).
 */
@Composable
fun Modifier.glassSurface(state: LiquidState, shape: Shape, lift: Lift = Lift.Float, clear: Boolean = false): Modifier {
    val touch = remember { GlassTouch() }
    val look = LocalDepthLook.current
    val outline = ButlerTheme.colors.cardOutline
    val gain = with(LocalDensity.current) { FLEX_GAIN.toPx() }
    val flexFull = with(LocalDensity.current) { FLEX_FULL.toPx() }
    val flexNone = with(LocalDensity.current) { FLEX_NONE.toPx() }
    val pane = this
        // Outermost, so the shadow and the rim swell with the glass.
        .graphicsLayer {
            val slim = ((flexNone - size.minDimension) / (flexNone - flexFull)).coerceIn(0f, 1f)
            val scale = 1f + (gain / size.maxDimension.coerceAtLeast(1f)).coerceAtMost(FLEX_MOST) * slim * touch.flex.value
            scaleX = scale
            scaleY = scale
        }
        .liftShadow(shape, look, lift)
        .clip(shape)
        .frosted(state, shape, touch, clear)
    return if (outline.isSpecified) pane.rim(1.dp, rimBrush(outline, look, lift), shape) else pane
}

/** What the frame's glass looks through: the content under it. */
fun Modifier.frostSource(state: LiquidState?): Modifier = if (state == null) this else liquefiable(state)

/**
 * The frame itself, glass over [state]'s content; plain when there is nothing under it. The
 * glass answers what happens to it: touched, it lights from inside under the finger and the
 * light fades once the finger is gone; turned ([LocalGlassLight]), the highlight on its rim
 * travels round it.
 */
@Composable
fun Modifier.frosted(state: LiquidState?, shape: Shape = RectangleShape): Modifier =
    if (state == null) this else frosted(state, shape, remember { GlassTouch() }, clear = false)

@Composable
private fun Modifier.frosted(state: LiquidState, shape: Shape, touch: GlassTouch, clear: Boolean): Modifier {
    val recipe = chromeGlass(shape, clear)
    val scope = rememberCoroutineScope()
    val tilt = LocalGlassLight.current
    val start = with(LocalDensity.current) { GLOW_START.toPx() }
    val most = with(LocalDensity.current) { GLOW_MOST.toPx() }
    // Light added to a pale pane shows less than on a dark one.
    val glowPower = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.11f else 0.14f
    val floating = shape != RectangleShape
    val rim = with(LocalDensity.current) { CLEAR_RIM.toPx() }
    val block: LiquidScope.() -> Unit = remember(recipe, touch, tilt, start, most, glowPower, floating, clear, rim) {
        {
            recipe()
            if (clear && floating) {
                val least = minOf(touch.size.width, touch.size.height).toFloat()
                refraction = if (least > 0f) (rim / least).coerceAtMost(GLASS_REFRACTION) else 0f
            }
            adapt = GLASS_ADAPT
            // Read here, so the glass alone is redrawn when any of them changes.
            if (floating) light = lightFrom(tilt?.value ?: Offset.Zero)
            glowAt = touch.at
            // From a spot under the finger outward, a little way: over a whole large pane it
            // was a flash, and spread thin over a bar it could not be seen at all.
            val reach = start + (most - start) * touch.spread.value
            glow = Offset(reach, touch.power.value * glowPower)
        }
    }
    return this.then(touch.watch(scope)).liquid(state, block)
}

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
    return glassSurface(glass, s, Lift.Rest, clear = true)
}

/**
 * A page's ground for its glass to show: Butler's own dither (the thinking line's 4x4 Bayer
 * dots) as a whole field, two slow waves crossing it. Sharp where the ground is bare, soft
 * shade moving under a pane, bent at a pane's rim. It stays where it is while the page
 * scrolls. [state] is what the panes look through.
 *
 * Light on the machine, the way Browse's field on a PC was made light: the waves step a few
 * times a second, not every frame (a dot only changes when a wave crosses a threshold, so more
 * steps would look the same); it stands still while Butler is not in front; and it has a layer
 * of its own, so a step redraws the field and the glass over it and nothing else on the page.
 */
@Composable
fun AmbientGround(state: LiquidState, modifier: Modifier = Modifier) {
    val ground = MaterialTheme.colorScheme.background
    val dot = with(LocalDensity.current) { 2.dp.toPx() }.roundToInt().coerceAtLeast(2)
    val tiles = rememberDitherTiles(ButlerTheme.colors.textLow.copy(alpha = AMBIENT_INK), dotPx = dot)
    val phase = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var inFront by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> inFront = true
                Lifecycle.Event.ON_PAUSE -> inFront = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inFront) {
        if (!inFront) return@LaunchedEffect
        // It picks up where it stopped.
        val started = System.nanoTime() - (phase.floatValue * AMBIENT_DRIFT_MS * 1_000_000L).toLong()
        while (true) {
            phase.floatValue = ((System.nanoTime() - started) / 1_000_000L % AMBIENT_DRIFT_MS) / AMBIENT_DRIFT_MS.toFloat()
            delay(1000L / AMBIENT_STEPS)
        }
    }
    Box(
        modifier.graphicsLayer().frostSource(state).drawBehind {
            drawRect(ground)
            val cell = dot * 4f
            val columns = kotlin.math.ceil(size.width / cell).toInt()
            val rows = kotlin.math.ceil(size.height / cell).toInt()
            val now = phase.floatValue
            for (row in 0 until rows) {
                // Measured against the width both ways, so the waves keep their shape on a tall screen.
                val v = (row + 0.5f) / columns
                // Cells of one density in a row go down as one rectangle.
                var from = 0
                var level = -1
                for (column in 0..columns) {
                    val here = if (column == columns) -2 else ambientLevel((column + 0.5f) / columns, v, now)
                    if (here != level) {
                        if (level >= 0) {
                            drawRect(tiles.brush(level), topLeft = Offset(from * cell, row * cell), size = Size((column - from) * cell, cell))
                        }
                        from = column
                        level = here
                    }
                }
            }
        },
    )
}

/** A cell's density: two waves, one slow and broad, one finer and twice as fast. */
private fun ambientLevel(u: Float, v: Float, phase: Float): Int {
    // Broad and slow: a field that keeps changing all over keeps the eye off the page.
    val wave = 0.55f + 0.33f * kotlin.math.sin((u * 0.9f + v * 0.55f - phase) * TAU) +
        0.12f * kotlin.math.sin((u * 1.7f - v * 1.2f + phase * 2f) * TAU)
    // Never bare: a hole in the field is the first thing the eye goes to. Thin everywhere, a
    // little thicker along a crest.
    return (kotlin.math.floor(AMBIENT_DENSITY * wave * 8f + 0.5f).toInt() - 1).coerceAtLeast(0)
}

private const val TAU = 6.2831855f
/** One pass of the waves. */
private const val AMBIENT_DRIFT_MS = 120_000
/** How many times a second the field steps. */
private const val AMBIENT_STEPS = 4
/** How thick the field gets at a wave's crest, of 1. */
private const val AMBIENT_DENSITY = 0.27f
/** How strong its dots are. */
private const val AMBIENT_INK = 0.38f

/** A gap the size of the frame at a list's foot. */
@Composable
fun bottomChrome(): Dp = LocalBottomChrome.current
