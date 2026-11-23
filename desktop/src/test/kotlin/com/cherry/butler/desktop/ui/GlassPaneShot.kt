package com.cherry.butler.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.feature.settings.SettingsPage
import com.cherry.butler.ui.components.AmbientGround
import com.cherry.butler.ui.components.LocalPaneGlass
import com.cherry.butler.ui.components.pane
import io.github.fletchmckee.liquid.rememberLiquidState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders a page of glass panes over its ambient ground (Glass.kt) offscreen, laid out as the
 * settings front page is, to check the glass shows as glass in each look. Run with
 * GLASS_SHOT=<out dir>; skipped otherwise.
 */
class GlassPaneShot {
    @Test
    fun shots() {
        val out = System.getenv("GLASS_SHOT") ?: return
        for (theme in listOf(AppTheme.JanitorClassic, AppTheme.LightsOut, AppTheme.Daylight)) {
            val scene = ImageComposeScene(width = 824, height = 1500, density = Density(2f)) {
                ButlerTheme(theme = theme, depth = Depth.Lifted) {
                    val glass = rememberLiquidState()
                    Box(Modifier.fillMaxSize()) {
                        AmbientGround(glass, Modifier.fillMaxSize())
                        CompositionLocalProvider(LocalPaneGlass provides glass) {
                            Column(Modifier.fillMaxSize().padding(top = 56.dp)) {
                                Text(
                                    "Settings",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                                )
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).pane(color = ButlerTheme.colors.surfaceHigh)) {
                                    SettingsPage.entries.filter { it.listed }.forEach { p ->
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(painterResource(p.icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(p.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ButlerTheme.colors.textLow)
                                        }
                                    }
                                }
                                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 28.dp).pane(shape = MaterialTheme.shapes.large)) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Sign out", style = MaterialTheme.typography.titleMedium, color = ButlerTheme.colors.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            var image = scene.render(0)
            for (t in 1..30) image = scene.render(t * 16_000_000L)
            File(out, "glass-${theme.name}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
