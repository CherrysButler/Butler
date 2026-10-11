package com.cherry.butler.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import kotlinx.coroutines.delay
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.roundToInt

/** Where the pointer is over a dither ground, and where it was last seen once it has left. */
internal class PointerTrack {
    var inside by mutableStateOf(false)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    fun at(p: Offset) { position = p; inside = true }
    fun gone() { inside = false }
}

/** Feeds [track] from everything under this node, first and without taking anything. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.trackPointer(track: PointerTrack): Modifier = this
    .onPointerEvent(PointerEventType.Move, PointerEventPass.Initial) { track.at(it.changes.first().position) }
    .onPointerEvent(PointerEventType.Enter, PointerEventPass.Initial) { track.at(it.changes.first().position) }
    .onPointerEvent(PointerEventType.Exit, PointerEventPass.Initial) { track.gone() }

/** How the field clears room for what stands on it. */
internal enum class DitherShape {
    /** Thins toward the middle all round, for a form standing in the centre (the door). */
    Centre,
    /** Clear in a middle column [quietWidth] wide, thickening toward the side edges (Browse). */
    Column,
}

/**
 * The thinking line's 4x4 Bayer dither as a whole field: two slow waves cross it, it gives way
 * where content stands, and it thickens in a soft pool around the pointer. The graphics card
 * works out every dot in one pass (an SkSL shader).
 *
 * [driftFps] 0 moves the waves every display frame. A number paces the drift instead: dots
 * only step when a wave crosses a threshold, so 30 steps a second look the same, and a screen
 * that is otherwise still doesn't redraw the window 180 times a second for it.
 */
@Composable
internal fun DitherField(
    pointer: PointerTrack,
    quietWidth: Dp,
    modifier: Modifier,
    shape: DitherShape = DitherShape.Centre,
    driftFps: Int = 0,
) {
    val dot = with(LocalDensity.current) { 2.dp.toPx() }.roundToInt().coerceAtLeast(2).toFloat()
    val faint = ButlerTheme.colors.rule
    val near = ButlerTheme.colors.textLow
    val phase: State<Float> = if (driftFps <= 0) {
        rememberInfiniteTransition(label = "dither-field").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "dither-drift",
        )
    } else {
        val paced = remember { mutableFloatStateOf(0f) }
        // Still while Butler isn't the window in use: nothing is drawn for a field nobody is
        // looking at. It picks up where it stopped.
        val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(driftFps, focused) {
            if (!focused) return@LaunchedEffect
            val start = System.nanoTime() - (paced.floatValue * DRIFT_MS * 1_000_000L).toLong()
            val step = 1000L / driftFps
            while (true) {
                paced.floatValue = ((System.nanoTime() - start) / 1_000_000L % DRIFT_MS) / DRIFT_MS.toFloat()
                delay(step)
            }
        }
        paced
    }
    // The pool sits under the pointer the frame it moves; only its strength eases, so it
    // blooms in when the pointer arrives and dissolves where it was when the pointer leaves.
    val poolStrength = remember { Animatable(0f) }
    LaunchedEffect(pointer.inside) {
        if (pointer.inside) poolStrength.animateTo(1f, tween(Motion.MEDIUM, easing = Motion.EaseOut))
        else poolStrength.animateTo(0f, tween(Motion.LONG + 120, easing = Motion.EaseOut))
    }

    // One builder and one paint for the field's whole life; each frame only sets the uniforms
    // and swaps in the new shader, freeing the last one there and then (a fresh builder,
    // shader and brush every frame left native objects for the collector to find).
    val field = remember { FieldPaint() }
    androidx.compose.runtime.DisposableEffect(field) { onDispose { field.close() } }
    // Its own layer: a step of the field redraws the field alone. Without one, each step
    // invalidated the parent's recording, so every step re-recorded the whole screen around
    // it (Browse's toolbar, sort, tags: text shaped again 12 to 30 times a second).
    Canvas(modifier.graphicsLayer()) {
        val at = pointer.position
        val half = quietWidth.toPx() / 2f
        val b = field.builder
        b.uniform("size", size.width, size.height)
        b.uniform("dotPx", dot)
        b.uniform("phase", phase.value)
        b.uniform("pointer", at.x, at.y)
        b.uniform("strength", poolStrength.value)
        b.uniform("reach", 170.dp.toPx())
        b.uniform("formHalf", half)
        b.uniform("formSoft", 64.dp.toPx())
        b.uniform("column", if (shape == DitherShape.Column) 1f else 0f)
        b.uniform("faint", faint.red * faint.alpha, faint.green * faint.alpha, faint.blue * faint.alpha, faint.alpha)
        b.uniform("near", near.red * near.alpha, near.green * near.alpha, near.blue * near.alpha, near.alpha)
        field.swap(b.makeShader())
        val canvas = drawContext.canvas.nativeCanvas
        if (shape == DitherShape.Column) {
            // Only the two sides: the column has no dots, so the graphics card needn't visit it.
            val cx = size.width / 2f
            canvas.drawRect(org.jetbrains.skia.Rect.makeLTRB(0f, 0f, cx - half, size.height), field.paint)
            canvas.drawRect(org.jetbrains.skia.Rect.makeLTRB(cx + half, 0f, size.width, size.height), field.paint)
        } else {
            canvas.drawRect(org.jetbrains.skia.Rect.makeWH(size.width, size.height), field.paint)
        }
    }
}

/** The field's reusable Skia objects. */
private class FieldPaint {
    val builder = RuntimeShaderBuilder(FieldEffect)
    val paint = org.jetbrains.skia.Paint()
    private var shader: org.jetbrains.skia.Shader? = null

    fun swap(next: org.jetbrains.skia.Shader) {
        paint.shader = next
        shader?.close()
        shader = next
    }

    fun close() {
        shader?.close()
        paint.close()
        builder.close()
    }
}

/** One pass of the waves, in milliseconds. */
private const val DRIFT_MS = 26_000

/**
 * Per pixel: the dot cell it falls in, that cell's density (waves, the room left for content,
 * the pointer's pool), then the 4x4 Bayer threshold for the pixel's dot in the cell.
 * The Bayer matrix is the thinking line's (DitherTiles), built from its 2x2 seed.
 */
private val FieldEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform float2 size;
        uniform float dotPx;
        uniform float phase;
        uniform float2 pointer;
        uniform float strength;
        uniform float reach;
        uniform float formHalf;
        uniform float formSoft;
        uniform float column;
        uniform half4 faint;
        uniform half4 near;

        float seed(float x, float y) { return mod(2.0 * x + 3.0 * y, 4.0); }

        half4 main(float2 p) {
            const float TAU = 6.2831853;
            float cell = dotPx * 4.0;
            float2 cc = (floor(p / cell) + 0.5) * cell;
            float2 center = size * 0.5;
            float side = abs(cc.x - center.x);
            // A column of content stays clear: nothing is drawn under it at all.
            if (column > 0.5 && side < formHalf) { return half4(0.0); }
            float2 uv = cc / size;
            float wave = 0.55 + 0.3 * sin((uv.x * 1.3 + uv.y * 0.8 - phase) * TAU)
                + 0.15 * sin((uv.x * 2.7 - uv.y * 1.9 + phase * 2.0) * TAU);
            float edge = column > 0.5
                ? clamp((side - formHalf) / max(center.x - formHalf, 1.0), 0.0, 1.0)
                : clamp(length(cc - center) / length(center), 0.0, 1.0);
            float base = (0.04 + 0.3 * edge * edge) * wave;
            float d = length(cc - pointer) / reach;
            // Over the content the pool goes quiet (a third, in the faint dots), so what is under
            // the pointer stays readable; out at the sides it blooms to full. The waves are
            // the same everywhere.
            float roam = smoothstep(formHalf, formHalf + formSoft, abs(pointer.x - center.x));
            float gather = strength * 0.45 * mix(0.33, 1.0, roam) * exp(-d * d);
            float level = floor((base + gather) * 8.0 + 0.5) - 1.0;
            if (level < 0.0) { return half4(0.0); }
            level = min(level, 6.0);
            float2 q = floor(mod(p, cell) / dotPx);
            float bayer = 4.0 * seed(mod(q.x, 2.0), mod(q.y, 2.0)) + seed(floor(q.x / 2.0), floor(q.y / 2.0));
            if (bayer >= (level + 1.0) * 2.0) { return half4(0.0); }
            return (gather > base && roam > 0.5) ? near : faint;
        }
        """.trimIndent(),
    )
}
