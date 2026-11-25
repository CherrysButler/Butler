package com.cherry.butler.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.platform.softCopy
import com.cherry.butler.ui.components.GlassPicture
import com.cherry.butler.ui.components.TagPill
import com.cherry.butler.ui.components.card
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * A bot card whose glass looks through the bot's own picture, offscreen: the card as Browse
 * lays it out (name, picture, footer), with a plain card beside it to tell the two apart.
 * Run with GLASS_LEAK=<picture>;<picture>,<out dir>; skipped otherwise.
 */
class GlassLeakShot {
    @Test
    fun shots() {
        val (pictures, out) = (System.getenv("GLASS_LEAK") ?: return).split(",")
        val shown = pictures.split(";").map { org.jetbrains.skia.Image.makeFromEncoded(File(it).readBytes()) }
        for ((name, theme) in listOf("classic" to AppTheme.JanitorClassic, "black" to AppTheme.LightsOut, "daylight" to AppTheme.Daylight)) {
            val scene = ImageComposeScene(width = 1160, height = 900, density = Density(2f)) {
                ButlerTheme(theme = theme, depth = Depth.Lifted) {
                    Row(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for ((i, image) in (shown + shown.first()).withIndex()) {
                            // The tile's own crop: the middle of the picture, two wide by three tall.
                            val side = minOf(image.width, image.height * 2 / 3)
                            val crop = Bitmap().apply { allocN32Pixels(side, side * 3 / 2) }
                            image.readPixels(crop, (image.width - side) / 2, (image.height - side * 3 / 2) / 2)
                            val soft = remember { softCopy(crop)!! }
                            val sharp = remember { org.jetbrains.skia.Image.makeFromBitmap(crop).toComposeImageBitmap() }
                            var slot by remember { mutableStateOf(Rect.Zero) }
                            val leaks = i < shown.size
                            Column(Modifier.width(172.dp).card(clip = false, picture = if (leaks) ({ GlassPicture(soft, slot) }) else null)) {
                                Text("Seraphine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                Image(
                                    sharp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(172.dp).height(258.dp).onPlaced {
                                        val at = it.positionInParent()
                                        slot = Rect(at.x, at.y, at.x + it.size.width, at.y + it.size.height)
                                    },
                                )
                                Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("@lanternwright", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("She keeps the last light on the harbour wall and asks every sailor one question.", style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, maxLines = 3)
                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Female", "Fantasy", "OC").forEach { TagPill(it) } }
                                }
                            }
                        }
                    }
                }
            }
            var image = scene.render(0)
            for (t in 1..6) image = scene.render(t * 16_000_000L)
            File(out, "leak-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
