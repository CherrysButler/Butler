package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.ui.components.GlassKey
import com.cherry.butler.ui.components.GlassKeyChoice
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.frostSource
import io.github.fletchmckee.liquid.rememberLiquidState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The round key that makes something new, offscreen: the drop at rest, and flowed open into
 * the pane its choices are on, over a list of cards.
 * Run with GLASS_KEY=<out dir>; skipped otherwise.
 */
class GlassKeyShot {
    @Test
    fun shots() {
        val out = System.getenv("GLASS_KEY") ?: return
        for ((name, theme) in listOf("classic" to AppTheme.JanitorClassic, "daylight" to AppTheme.Daylight)) {
            for (open in listOf(false, true)) {
                val scene = ImageComposeScene(width = 720, height = 600, density = Density(2f)) {
                    ButlerTheme(theme = theme, depth = Depth.Lifted) {
                        val glass = rememberLiquidState()
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            Column(Modifier.fillMaxSize().frostSource(glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (who in listOf("Seraphine", "The Lantern Keeper", "Quinn", "A stranger at the door")) {
                                    Column(Modifier.fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(who, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text("She/her \u00b7 412 words", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                                    }
                                }
                            }
                            GlassKey(
                                glass = glass,
                                choices = listOf(GlassKeyChoice("New persona", Icons.Outlined.PersonAdd) {}, GlassKeyChoice("New character", Icons.Outlined.Face) {}),
                                label = "New",
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
                                startOpen = open,
                            )
                        }
                    }
                }
                var image = scene.render(0)
                for (t in 1..6) image = scene.render(t * 16_000_000L)
                File(out, "key-$name-${if (open) "open" else "rest"}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                scene.close()
            }
        }
    }
}
