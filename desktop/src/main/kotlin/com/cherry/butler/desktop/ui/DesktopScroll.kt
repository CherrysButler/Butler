package com.cherry.butler.desktop.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The wheel anywhere on a screen scrolls its list: over the dither beside a column, or a
 * toolbar, as well as over the list itself (which scrolls itself). Ticks add up and glide out,
 * as the list's own wheel does, so a quick spin goes as far as it would over the list.
 * [reverse] is for a list laid out from the bottom (the chat), where down means newer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.wheelScrolls(state: ScrollableState?, reverse: Boolean = false): Modifier {
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    val glide = remember { WheelGlide() }
    val target = rememberUpdatedState(state)
    return onPointerEvent(PointerEventType.Scroll, PointerEventPass.Main) { event ->
        // Whatever is under the pointer had first say; only a turn nothing used comes here.
        val list = target.value ?: return@onPointerEvent
        if (event.changes.any { it.isConsumed }) return@onPointerEvent
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy == 0f) return@onPointerEvent
        event.changes.forEach { it.consume() }
        glide.pending += (if (reverse) -dy else dy) * step
        if (glide.running) return@onPointerEvent
        glide.running = true
        scope.launch {
            try {
                list.scroll {
                    var last = withFrameNanos { it }
                    while (kotlin.math.abs(glide.pending) > 0.5f) {
                        val now = withFrameNanos { it }
                        // An exponential ease-out over ~60 ms, the same at any refresh rate.
                        val take = glide.pending * (1f - kotlin.math.exp(-(now - last) / 60_000_000f))
                        last = now
                        val used = scrollBy(take)
                        glide.pending = if (used == 0f && take != 0f) 0f else glide.pending - take
                    }
                }
            } finally {
                glide.pending = 0f
                glide.running = false
            }
        }
    }
}

private class WheelGlide {
    var pending = 0f
    var running = false
}
