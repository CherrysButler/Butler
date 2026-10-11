package com.cherry.butler.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * The light an open chat casts over the whole window: the rail, the chat and the room either
 * side. The chat says where its column is and whether a reply is coming; the shell draws the
 * light once, over everything, so it reaches the rail too.
 */
@Stable
internal class ChatLight {
    /** The column's edges in the window, in pixels; the light is drawn while [on]. */
    var on by mutableStateOf(false)
    var left by mutableFloatStateOf(0f)
    var right by mutableFloatStateOf(0f)
    var warm by mutableStateOf(false)
}

internal val LocalChatLight = staticCompositionLocalOf<ChatLight?> { null }

/** Reports this node's place in the window as the chat column's edges. */
internal fun Modifier.castsChatLight(light: ChatLight?): Modifier =
    if (light == null) this else onGloballyPositioned { c ->
        val x = c.positionInRoot().x
        light.left = x
        light.right = x + c.size.width
    }

/**
 * The light itself, over the window. Two broad pools, one in each side's room, drift up and
 * down over a slow minute, out of step, over a faint even glow that reaches across the rail
 * and the column alike. While the character is writing the light warms toward the look's accent
 * (Butler red, or a Custom look's own colour) and breathes, and cools again when the reply is
 * in. Stepped ten times a second (nothing finer shows in a minute-long drift) and still while
 * Butler isn't the window in use.
 */
@Composable
internal fun ChatLightOverlay(light: ChatLight, modifier: Modifier) {
    // It comes up as a chat opens and goes out as it closes, never in one frame.
    val presence = remember { Animatable(0f) }
    LaunchedEffect(light.on) {
        presence.animateTo(if (light.on) 1f else 0f, tween(if (light.on) 700 else 1_100, easing = FastOutSlowInEasing))
    }
    val shown by remember { derivedStateOf { light.on || presence.value > 0f } }
    if (!shown) return
    val focused = LocalWindowInfo.current.isWindowFocused
    val phase = remember { mutableFloatStateOf(0f) }
    val breath = remember { mutableFloatStateOf(0f) }
    val warmth = remember { Animatable(0f) }
    val warm = light.warm
    LaunchedEffect(warm) {
        // Warming and cooling both ease in and out over seconds: an ease-out did most of the
        // cooling in its first instant, which read as a snap back to white.
        warmth.animateTo(if (warm) 1f else 0f, tween(if (warm) 1_800 else 3_000, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val start = System.nanoTime() - (phase.floatValue * DRIFT_MS * 1_000_000L).toLong()
        while (true) {
            val ms = (System.nanoTime() - start) / 1_000_000L
            phase.floatValue = (ms % DRIFT_MS) / DRIFT_MS.toFloat()
            // The breath runs on always; it shows only as far as the light is warm, so it fades
            // out with the warmth instead of stopping in a frame.
            breath.floatValue = (ms % BREATH_MS) / BREATH_MS.toFloat()
            delay(100)
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    // Lighter looks get less: the same lift reads far stronger on a pale ground.
    val strength = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.035f else 0.06f
    val glow = remember { GlowPaint() }
    DisposableEffect(glow) { onDispose { glow.close() } }
    var origin by remember { mutableFloatStateOf(0f) }
    Canvas(modifier.graphicsLayer().onGloballyPositioned { origin = it.positionInRoot().x }) {
        val b = glow.builder
        b.uniform("size", size.width, size.height)
        b.uniform("column", light.left - origin, light.right - origin)
        b.uniform("phase", phase.floatValue)
        b.uniform("breath", breath.floatValue)
        b.uniform("warmth", warmth.value)
        b.uniform("strength", strength * presence.value)
        b.uniform("ink", ink.red, ink.green, ink.blue, 1f)
        b.uniform("warm", accent.red, accent.green, accent.blue, 1f)
        glow.swap(b.makeShader())
        drawContext.canvas.nativeCanvas.drawRect(Rect.makeWH(size.width, size.height), glow.paint)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** One pass of the drift, and one breath while a reply is written. */
private const val DRIFT_MS = 60_000L
private const val BREATH_MS = 3_200L

/** The light's reusable Skia objects: one builder and paint, each frame's shader freed when replaced. */
private class GlowPaint {
    val builder = RuntimeShaderBuilder(GlowEffect)
    val paint = Paint()
    private var shader: Shader? = null

    fun swap(next: Shader) {
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

/**
 * Per pixel: an even floor of light everywhere (a little less toward the column's middle, so
 * the story stays the calmest place), plus two broad pools centred in the room either side of
 * the column, drifting a third of the height out of step. The light is the ink colour at a few
 * percent, mixed toward red by warmth; a trace of noise keeps the faint gradient from banding
 * on an 8-bit screen.
 */
private val GlowEffect: RuntimeEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform float2 size;
        uniform float2 column;
        uniform float phase;
        uniform float breath;
        uniform float warmth;
        uniform float strength;
        uniform half4 ink;
        uniform half4 warm;

        float pool(float2 p, float cx, float reach, float offset) {
            const float TAU = 6.2831853;
            float cy = size.y * (0.5 + 0.3 * sin((phase + offset) * TAU));
            float2 d = (p - float2(cx, cy)) / float2(max(reach, 1.0), size.y * 0.55);
            return exp(-dot(d, d) * 1.4);
        }

        half4 main(float2 p) {
            const float TAU = 6.2831853;
            float leftRoom = max(column.x, 1.0);
            float rightRoom = max(size.x - column.y, 1.0);
            float pools = max(pool(p, leftRoom * 0.5, leftRoom, 0.37), pool(p, size.x - rightRoom * 0.5, rightRoom, 0.0));
            // Inside the column the floor eases to two thirds by its middle.
            float halfWidth = max((column.y - column.x) * 0.5, 1.0);
            float depth = clamp(min(p.x - column.x, column.y - p.x) / halfWidth, 0.0, 1.0);
            float floorLight = mix(1.0, 0.66, smoothstep(0.0, 1.0, depth));
            float lift = strength * (0.45 * floorLight + 0.55 * pools) * (1.0 + warmth * (0.6 + 0.4 * sin(breath * TAU)));
            float grain = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
            float a = clamp(lift + grain / 255.0, 0.0, 1.0);
            half3 c = mix(ink.rgb, warm.rgb, warmth);
            return half4(c * a, a);
        }
        """.trimIndent(),
    )
}
