package com.cherry.butler.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.LocalDepth
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * Frosted chrome (Depth Lifted): the frame (the floating nav bar, the chat's top bar and
 * composer) frosts what passes under it. Only the frame: text you read never sits on glass.
 * Off on battery saver, where Butler draws the frame solid instead; on phones before
 * Android 12, which can't blur behind a view, the frame is its tinted colour (Haze's fallback).
 */

/** Room a screen leaves at its foot for frame floating over it (the nav island), so the last row clears it. */
val LocalBottomChrome = compositionLocalOf { 0.dp }

/** Where a chat's background picture is frosted from: the chat's own, or the PC window's when it spreads across. */
val LocalBackdropHaze = compositionLocalOf<HazeState?> { null }

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

/** The frame's frost: a strong blur under the chrome tone, so what passes beneath reads as light, never as text. */
@Composable
fun chromeFrost(): HazeStyle {
    val chrome = ButlerTheme.colors.chrome
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.background,
        tints = listOf(HazeTint(chrome.copy(alpha = 0.62f))),
        blurRadius = 26.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(chrome.copy(alpha = 0.94f)),
    )
}

/** What the frame frosts: the content under it. */
fun Modifier.frostSource(state: HazeState?): Modifier = if (state == null) this else hazeSource(state)

/** The frame itself, frosting [state]'s content; plain when there is nothing to frost. */
@Composable
fun Modifier.frosted(state: HazeState?): Modifier = if (state == null) this else hazeEffect(state, chromeFrost())

/** A gap the size of the frame at a list's foot. */
@Composable
fun bottomChrome(): Dp = LocalBottomChrome.current
