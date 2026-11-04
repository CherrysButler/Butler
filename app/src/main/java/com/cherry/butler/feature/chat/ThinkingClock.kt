package com.cherry.butler.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Whether a piece of the thinking line is on screen at all. A list builds rows ahead of the
 * reader and keeps some it has scrolled past; an animation in one of those asked for a frame
 * every display frame all the same, for nothing anyone could see.
 */
@Composable
internal fun rememberOnScreen(): MutableState<Boolean> = remember { mutableStateOf(false) }

/** Keeps [state] true while some of this node shows in the window (clipped by whatever scrolls it). */
internal fun Modifier.onScreen(state: MutableState<Boolean>): Modifier = onGloballyPositioned { c ->
    val b = c.boundsInWindow()
    val shown = b.width > 0f && b.height > 0f
    if (state.value != shown) state.value = shown
}

/**
 * Nanoseconds of animation so far, ticking only while [running]: frames are asked for only
 * then, and it picks up where it stopped. Every frame, as smooth as the screen goes; on battery
 * saver, thirty a second, as the phone asks of apps then.
 */
@Composable
internal fun rememberThinkingClock(running: Boolean): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val saving = (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true
        val resumeAt = clock.longValue
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { clock.longValue = resumeAt + (it - start) }
            if (saving) kotlinx.coroutines.delay(SAVER_FRAME_GAP_MS)
        }
    }
    return clock
}

/** On battery saver, the least time between the thinking animation's frames: about thirty a second. */
private const val SAVER_FRAME_GAP_MS = 30L
