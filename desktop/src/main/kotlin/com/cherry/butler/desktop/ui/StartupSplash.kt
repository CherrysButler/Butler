package com.cherry.butler.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When Butler has something to show: the sign-in door, or the first page of Browse. Until
 * then the window shows the splash; a screen that never says (a chat reopened straight from
 * last time) is let through after a few seconds all the same.
 */
internal object DesktopStartup {
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()

    fun contentReady() { _ready.value = true }
}

/**
 * The first thing in the window while Butler gets ready: Browse's dot field drifting, clear
 * around the bow tie in the middle, which breathes. When [ready] comes it hands over: the
 * field fades, the bow tie grows a little as it goes, and the app is underneath.
 */
@Composable
internal fun StartupSplash(ready: Boolean, modifier: Modifier = Modifier) {
    var gone by remember { mutableStateOf(false) }
    if (gone) return
    val fade = remember { Animatable(1f) }
    // Never a flash: up for a moment at least, so the hand-over reads as one.
    var shownLongEnough by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_SHOWN_MS)
        shownLongEnough = true
    }
    LaunchedEffect(ready, shownLongEnough) {
        if (!ready || !shownLongEnough) return@LaunchedEffect
        fade.animateTo(0f, tween(HANDOVER_MS, easing = Motion.EaseOut))
        gone = true
    }
    val breath by rememberInfiniteTransition(label = "splash").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = Motion.EaseOut), RepeatMode.Reverse),
        label = "splash-breath",
    )
    val pointer = remember { PointerTrack() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(MaterialTheme.colorScheme.background)
            // Nothing underneath takes a click while it covers the app.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        DitherField(pointer, quietWidth = 260.dp, modifier = Modifier.fillMaxSize(), shape = DitherShape.Centre)
        Image(
            painter = rememberVectorPainter(ButlerMark),
            contentDescription = "Butler",
            modifier = Modifier
                .size(width = 96.dp, height = 50.dp)
                .graphicsLayer {
                    val grow = 1f + (1f - fade.value) * 0.18f
                    scaleX = grow
                    scaleY = grow
                    alpha = breath
                },
        )
    }
}

private const val MIN_SHOWN_MS = 700L
private const val HANDOVER_MS = 520
