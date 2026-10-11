package com.cherry.butler.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.Motion

/**
 * The door on the desktop: the phone's sign-in form standing straight on the thinking line's
 * dither, grown into a field that drifts and gathers where the pointer goes. The form's own
 * ground is made clear here, so the field runs behind its keys.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopDoor(form: @Composable () -> Unit) {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, tween(Motion.LONG + 120, easing = Motion.EaseOut)) }
    // The whole door listens, first and without taking anything, so the pool follows the
    // pointer across the form too instead of dying behind it.
    val pointer = remember { PointerTrack() }
    val overlay = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(LocalDoorOverlay provides overlay) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .trackPointer(pointer),
    ) {
        val formWidth = minOf(maxWidth - 32.dp, 440.dp)
        // The form gets the window's height, so the e-mail and code steps fit as they open.
        val formHeight = maxHeight - 32.dp
        DitherField(pointer, formWidth, Modifier.fillMaxSize().graphicsLayer { alpha = arrival.value })
        Box(
            Modifier
                .align(Alignment.Center)
                .width(formWidth)
                .height(formHeight)
                .graphicsLayer {
                    alpha = arrival.value
                    translationY = (1f - arrival.value) * 18.dp.toPx()
                },
        ) {
            // Every line of text carries a soft halo of the ground, so the dots give way right
            // around the letters and the field runs on everywhere else.
            val ground = MaterialTheme.colorScheme.background
            val halo = androidx.compose.ui.graphics.Shadow(color = ground, offset = Offset.Zero, blurRadius = with(LocalDensity.current) { 10.dp.toPx() })
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(background = Color.Transparent),
                typography = MaterialTheme.typography.withHalo(halo),
                shapes = MaterialTheme.shapes,
            ) { form() }
        }
        overlay.value?.invoke()
    }
    }
}

/**
 * The halo on the text that stands on the field. Not on titleMedium (the keys' labels) or
 * bodyLarge (what is typed in a field): those sit on solid fills and need nothing.
 */
private fun androidx.compose.material3.Typography.withHalo(halo: androidx.compose.ui.graphics.Shadow) = copy(
    displayLarge = displayLarge.copy(shadow = halo), displayMedium = displayMedium.copy(shadow = halo), displaySmall = displaySmall.copy(shadow = halo),
    headlineLarge = headlineLarge.copy(shadow = halo), headlineMedium = headlineMedium.copy(shadow = halo), headlineSmall = headlineSmall.copy(shadow = halo),
    titleLarge = titleLarge.copy(shadow = halo), titleSmall = titleSmall.copy(shadow = halo),
    bodyMedium = bodyMedium.copy(shadow = halo), bodySmall = bodySmall.copy(shadow = halo),
    labelLarge = labelLarge.copy(shadow = halo), labelMedium = labelMedium.copy(shadow = halo), labelSmall = labelSmall.copy(shadow = halo),
)

private val LocalDoorOverlay = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.MutableState<(@Composable () -> Unit)?>?> { null }

/**
 * Shows [content] over the whole door (a panel the form asks for, like the human check),
 * rather than inside the form's column. Outside the door it draws in place.
 */
@Composable
fun DoorOverlay(content: @Composable () -> Unit) {
    val host = LocalDoorOverlay.current
    if (host == null) {
        content()
        return
    }
    val latest = androidx.compose.runtime.rememberUpdatedState(content)
    androidx.compose.runtime.DisposableEffect(host) {
        val slot: @Composable () -> Unit = { latest.value() }
        host.value = slot
        onDispose { if (host.value === slot) host.value = null }
    }
}
