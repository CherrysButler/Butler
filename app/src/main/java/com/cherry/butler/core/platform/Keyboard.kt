package com.cherry.butler.core.platform

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * Whether the on-screen keyboard is up and staying up. It turns false the moment the keyboard
 * starts on its way down, not once it has gone: read off where its animation is headed, so
 * what closes with the keyboard closes alongside it and not after.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardStaying(): Boolean = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0

/**
 * How far up the on-screen keyboard is, 0 away to 1 fully up, frame by frame while it moves:
 * its height now against its full height, which is where its animation started or where it is
 * headed, whichever is the taller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardRise(): Float {
    val density = LocalDensity.current
    val now = WindowInsets.ime.getBottom(density)
    val full = maxOf(now, WindowInsets.imeAnimationSource.getBottom(density), WindowInsets.imeAnimationTarget.getBottom(density))
    return if (full <= 0) 0f else (now.toFloat() / full).coerceIn(0f, 1f)
}
