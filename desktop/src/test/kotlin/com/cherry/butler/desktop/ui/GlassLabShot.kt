package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.ui.components.LocalGlassLight
import com.cherry.butler.ui.components.frostSource
import com.cherry.butler.ui.components.glassSurface
import io.github.fletchmckee.liquid.rememberLiquidState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The glass over hard ground, offscreen: one bar over bright, dark, coloured and written
 * ground, to see how its tint gives way to each; turned three ways, to see the rim's light
 * travel; and pressed, to see it light from inside. Run with GLASS_LAB=<out dir>; skipped
 * otherwise.
 */
class GlassLabShot {
    @Test
    fun shots() {
        val out = System.getenv("GLASS_LAB") ?: return
        val tilts = listOf("left" to Offset(-0.7f, 0f), "rest" to Offset.Zero, "right" to Offset(0.7f, 0.5f))
        for (theme in listOf(AppTheme.JanitorClassic, AppTheme.Daylight)) {
            for ((name, tilt) in tilts) shot(File(out, "lab-${theme.name}-$name.png"), theme, tilt, press = false)
            shot(File(out, "lab-${theme.name}-pressed.png"), theme, Offset.Zero, press = true)
        }
    }

    private fun shot(file: File, theme: AppTheme, tilt: Offset, press: Boolean) {
        val light = mutableStateOf(tilt)
        val scene = ImageComposeScene(width = 824, height = 1340, density = Density(2f)) {
            ButlerTheme(theme = theme, depth = Depth.Lifted) {
                CompositionLocalProvider(LocalGlassLight provides light) {
                    val glass = rememberLiquidState()
                    Box(Modifier.fillMaxSize()) {
                        // Ground of four kinds, top to bottom.
                        Column(Modifier.fillMaxSize().frostSource(glass)) {
                            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.horizontalGradient(listOf(Color(0xFF5FA8A8), Color(0xFF8FC7C4), Color(0xFF4E8F92)))))
                            Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.horizontalGradient(listOf(Color(0xFFFFF4D6), Color(0xFFFFFFFF), Color(0xFFBFE3FF)))))
                            Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.horizontalGradient(listOf(Color(0xFF050608), Color(0xFF16181D), Color(0xFF000000)))))
                            Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.horizontalGradient(listOf(Color(0xFFE5484D), Color(0xFF7B61FF), Color(0xFF2BB673), Color(0xFFF5A524)))))
                            Column(Modifier.fillMaxWidth().height(130.dp).background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
                                repeat(4) {
                                    Text(
                                        "She leads the way with a sudden, energetic skip in her step.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Column(Modifier.fillMaxSize()) {
                            // A round key, as a call button is: the rim's two gaps are most of what shows on one.
                            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(104.dp).glassSurface(glass, androidx.compose.foundation.shape.CircleShape))
                            }
                            repeat(4) {
                                Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                                    val shape = MaterialTheme.shapes.extraLarge
                                    Row(
                                        Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(60.dp).glassSurface(glass, shape).padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        listOf("Home", "Chats", "Alerts", "Settings").forEach {
                                            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        var image = scene.render(0)
        var t = 1
        while (t <= 10) { image = scene.render(t * 16_000_000L); t++ }
        if (press) {
            // A finger down on each bar's "Chats", held while the light comes up.
            scene.sendPointerEvent(PointerEventType.Press, Offset(300f, 390f))
            while (t <= 40) { image = scene.render(t * 16_000_000L); t++ }
        }
        file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
}
