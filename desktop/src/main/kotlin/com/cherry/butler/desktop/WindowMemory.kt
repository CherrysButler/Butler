package com.cherry.butler.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** The window opens where it was closed: its size, its place, maximised or not. */
object WindowMemory {
    private val prefs get() = DesktopPrefs.open("butler_desktop")

    fun placement(): WindowPlacement =
        if (prefs.getBoolean("window_maximized", false)) WindowPlacement.Maximized else WindowPlacement.Floating

    fun size(): DpSize? {
        val w = prefs.getFloat("window_w", 0f)
        val h = prefs.getFloat("window_h", 0f)
        return if (w >= 420f && h >= 560f) DpSize(w.dp, h.dp) else null
    }

    /** The last place, if it is still on a screen (a monitor may have gone since). */
    fun position(): WindowPosition? {
        if (!prefs.contains("window_x")) return null
        val x = prefs.getFloat("window_x", 0f)
        val y = prefs.getFloat("window_y", 0f)
        val screens = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
        }.getOrDefault(emptyList())
        val onScreen = screens.any { it.contains((x + 40).toInt(), (y + 20).toInt()) }
        return if (onScreen) WindowPosition(x.dp, y.dp) else null
    }

    /** The character panel's width as last dragged, or [default]. */
    fun panelWidth(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
        prefs.getFloat("panel_w", 0f).takeIf { it > 0f }?.dp ?: default

    fun keepPanelWidth(width: androidx.compose.ui.unit.Dp) {
        prefs.edit().putFloat("panel_w", width.value).apply()
    }

    @OptIn(FlowPreview::class)
    @Composable
    fun Keep(state: WindowState) {
        LaunchedEffect(state) {
            snapshotFlow { Triple(state.placement, state.size, state.position) }
                .debounce(400)
                .collect { (placement, size, position) ->
                    val edit = prefs.edit().putBoolean("window_maximized", placement == WindowPlacement.Maximized)
                    if (placement == WindowPlacement.Floating) {
                        edit.putFloat("window_w", size.width.value).putFloat("window_h", size.height.value)
                        if (position is WindowPosition.Absolute) edit.putFloat("window_x", position.x.value).putFloat("window_y", position.y.value)
                    }
                    edit.apply()
                }
        }
    }
}
