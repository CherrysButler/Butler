package com.cherry.butler.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * Butler's glass over the wallpaper the reference playground uses (spacedouut/liquid-glass,
 * Apple's recovered shader), in the same window and with the same panes, so the two can be
 * laid side by side: a bar 388 by 60, dark and light, and a pane 300 by 200, dark.
 * Run with GLASS_REF=<wallpaper.jpg>,<out dir>; skipped otherwise.
 */
class GlassRefShot {
    @Test
    fun shots() {
        val (wall, out) = (System.getenv("GLASS_REF") ?: return).split(",")
        val picture = org.jetbrains.skia.Image.makeFromEncoded(File(wall).readBytes()).toComposeImageBitmap()
        data class Pane(val name: String, val theme: AppTheme, val w: Int, val h: Int, val r: Int)
        val panes = listOf(
            Pane("bar-dark", AppTheme.JanitorClassic, 388, 60, 30),
            Pane("bar-light", AppTheme.Daylight, 388, 60, 30),
            Pane("pane-dark", AppTheme.JanitorClassic, 300, 200, 44),
        )
        for (pane in panes) for (scale in listOf(1f, 2f)) {
            val scene = ImageComposeScene(width = (560 * scale).toInt(), height = (400 * scale).toInt(), density = Density(scale)) {
                ButlerTheme(theme = pane.theme, depth = Depth.Lifted) {
                    val glass = rememberLiquidState()
                    Box(Modifier.fillMaxSize()) {
                        Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().frostSource(glass))
                        Box(Modifier.align(Alignment.Center).size(pane.w.dp, pane.h.dp).glassSurface(glass, RoundedCornerShape(pane.r.dp)))
                    }
                }
            }
            var image = scene.render(0)
            for (t in 1..10) image = scene.render(t * 16_000_000L)
            File(out, "ours-${pane.name}-${scale.toInt()}x.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
