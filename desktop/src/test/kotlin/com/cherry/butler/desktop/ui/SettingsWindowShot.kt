package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.feature.settings.SettingsPage
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the settings window's frame offscreen (the bar, the list, the page's room) over a
 * stand-in app, to check its proportions. Run with SETTINGS_SHOT=<out dir>; skipped otherwise.
 */
class SettingsWindowShot {
    @Test
    fun shots() {
        val out = System.getenv("SETTINGS_SHOT") ?: return
        for ((name, w, h) in listOf(Triple("wide", 1300, 900), Triple("narrow", 700, 620))) {
            for (theme in listOf(AppTheme.JanitorClassic, AppTheme.Daylight)) {
                val state = SettingsWindowState().apply { open = true; pick(SettingsPage.Look) }
                val scene = ImageComposeScene(width = w, height = h, density = Density(1f)) {
                    ButlerTheme(theme = theme, depth = Depth.Lifted) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            Text("The app behind", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(24.dp))
                            SettingsWindow(state = state, onClose = {}, onLorebooks = {}) { page, _, _ ->
                                Column(Modifier.fillMaxSize().padding(20.dp)) {
                                    Text(page.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                var image = scene.render(0)
                for (t in 1..60) image = scene.render(t * 16_000_000L)
                File(out, "settings-$name-${theme.name}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                scene.close()
            }
        }
    }
}
