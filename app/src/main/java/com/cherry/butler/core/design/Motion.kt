package com.cherry.butler.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * One motion grammar for the whole app: state changes only, 150–280 ms, exponential
 * ease-out so things arrive quickly and settle softly. Exits are shorter than entrances.
 * Ambient loops (the thinking field, the caret, skeleton breath) keep their own pace.
 */
object Motion {
    /** Expo-out: fast start, long soft landing. */
    val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Selection, colour, small state flips. */
    const val SHORT = 150

    /** Content swaps, fades between screens. */
    const val MEDIUM = 220

    /** A screen sliding in. */
    const val LONG = 280

    fun <T> enter(durationMillis: Int = MEDIUM): FiniteAnimationSpec<T> = tween(durationMillis, easing = EaseOut)

    fun <T> exit(durationMillis: Int = SHORT): FiniteAnimationSpec<T> = tween(durationMillis, easing = EaseOut)
}
