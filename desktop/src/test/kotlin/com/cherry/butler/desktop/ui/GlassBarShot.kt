package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.ui.components.frostSource
import com.cherry.butler.ui.components.glassSurface
import io.github.fletchmckee.liquid.rememberLiquidState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * A capsule bar of Butler's glass over the kind of thing an iPhone's tab bar is seen over in
 * a chat list: a black ground, a row's round picture under the bar's left end, and lines of
 * white text passing its edges. Three places in the scroll, to lay beside frames of a
 * recording of the real one. Run with GLASS_BAR=<out dir>; skipped otherwise.
 */
class GlassBarShot {
    @Test
    fun shots() {
        val out = System.getenv("GLASS_BAR") ?: return
        for ((name, shift) in listOf("a" to 0, "b" to 14, "c" to 30)) {
            val scene = ImageComposeScene(width = 384 * 3, height = 120 * 3, density = Density(3f)) {
                ButlerTheme(theme = AppTheme.LightsOut, depth = Depth.Lifted) {
                    val glass = rememberLiquidState()
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // The list behind: rows 60 apart, each a round picture and two lines.
                        Box(Modifier.fillMaxSize().frostSource(glass).background(Color.Black)) {
                            for (row in -1..2) {
                                val y = (row * 60 + shift).dp
                                Box(Modifier.offset(x = 6.dp, y = y + 8.dp).size(52.dp).background(Color(0xFF3F9BD8), CircleShape))
                                Column(Modifier.offset(x = 72.dp, y = y + 10.dp)) {
                                    Text("TheHolyRudy", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                    Text("Saved Messages and 2 files", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AA0))
                                }
                            }
                        }
                        Row(
                            Modifier.align(Alignment.Center).padding(horizontal = 10.dp).fillMaxWidth().height(62.dp)
                                .glassSurface(glass, RoundedCornerShape(percent = 50)).padding(horizontal = 30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf("Contacts", "Calls", "Chats", "Settings").forEach {
                                Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                    }
                }
            }
            var image = scene.render(0)
            for (t in 1..10) image = scene.render(t * 16_000_000L)
            File(out, "bar-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
