package com.cherry.butler.feature.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One finger's drag, read as a sideways swipe or as nothing.
 *
 * The axis is decided once, as the finger leaves its first [decideAt] pixels: level enough
 * ([LOCK] times more across than along) and the drag is a swipe for as long as it lasts; anything
 * else and it is refused for good, so a scroll whose thumb turns sideways on the way never
 * becomes one. A swipe counts only if it ends at least [reach] across and still level.
 */
internal class SwipeLock(private val decideAt: Float, private val reach: Float) {
    enum class Axis { Undecided, Across, Refused }

    var axis = Axis.Undecided
        private set
    var across = 0f
        private set
    private var along = 0f

    fun move(dx: Float, dy: Float): Axis {
        across += dx
        along += dy
        if (axis == Axis.Undecided && hypot(across, along) >= decideAt) {
            axis = if (abs(across) >= abs(along) * LOCK) Axis.Across else Axis.Refused
        }
        return axis
    }

    /** Far enough and level enough that letting go now is a swipe. */
    val armed: Boolean get() = axis == Axis.Across && abs(across) >= reach && abs(along) <= abs(across) * DRIFT

    /**
     * What letting go now does: +1 for the next reply (the line dragged away to the left, as a
     * page is turned), -1 for the one before, 0 for nothing.
     */
    val step: Int get() = if (!armed) 0 else if (across < 0f) 1 else -1

    private companion object {
        /** Within about 22° of level to start as a swipe. */
        const val LOCK = 2.5f
        /** How far it may have wandered up or down by the end, for each pixel across. */
        const val DRIFT = 0.5f
    }
}

/** How far across a swipe has to go. */
private val SWIPE_REACH = 72.dp

/**
 * A swipe across a reply on a touch screen: dragged left for the next one, right for the one
 * before. [onPull] is how far the finger has it (0 when let go), [onArmed] fires as it passes
 * the point where letting go counts, [onSwipe] with +1 or -1 when it does.
 */
internal fun Modifier.replySwipe(
    onPull: (Float) -> Unit,
    onArmed: () -> Unit,
    onSwipe: (Int) -> Unit,
): Modifier = composed {
    val pull by rememberUpdatedState(onPull)
    val armedNow by rememberUpdatedState(onArmed)
    val swipe by rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val reach = minOf(SWIPE_REACH.toPx(), size.width / 3f)
            // Twice the touch slop: enough of the drag to tell its direction, and still before
            // the list has started to scroll under a level one.
            val lock = SwipeLock(decideAt = viewConfiguration.touchSlop * 2f, reach = reach)
            var armed = false
            var lifted = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                // A second finger, or the finger lost: not a swipe.
                if (change == null || event.changes.size > 1) break
                if (!change.pressed) { lifted = true; break }
                // Something inside took the drag before it was ours.
                if (lock.axis != SwipeLock.Axis.Across && change.isConsumed) break
                val moved = change.positionChange()
                when (lock.move(moved.x, moved.y)) {
                    SwipeLock.Axis.Across -> {
                        // Ours from here: the list does not scroll under a swipe.
                        change.consume()
                        pull(lock.across)
                        if (lock.armed != armed) {
                            armed = lock.armed
                            if (armed) armedNow()
                        }
                    }
                    SwipeLock.Axis.Refused -> break
                    SwipeLock.Axis.Undecided -> Unit
                }
            }
            val step = if (lifted) lock.step else 0
            pull(0f)
            if (step != 0) swipe(step)
        }
    }
}
