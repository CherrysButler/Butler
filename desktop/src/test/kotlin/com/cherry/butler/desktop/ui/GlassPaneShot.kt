package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cherry.butler.core.model.CharacterSort
import com.cherry.butler.feature.browse.ModeBar
import com.cherry.butler.feature.browse.StripChip
import com.cherry.butler.ui.components.SearchBox
import com.cherry.butler.ui.components.TagPill
import com.cherry.butler.ui.components.card
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * The panes that stand on plain ground, offscreen: the search box, the sort bar, chips, and
 * cards with a bot's tags on them, in each look with Depth Lifted (glass) and once Flat (which
 * must be as it was). Run with GLASS_PANES=<out dir>; skipped otherwise.
 */
class GlassPaneShot {
    @Test
    fun shots() {
        val out = System.getenv("GLASS_PANES") ?: return
        val looks = listOf(
            Triple("classic-lifted", AppTheme.JanitorClassic, Depth.Lifted),
            Triple("black-lifted", AppTheme.LightsOut, Depth.Lifted),
            Triple("daylight-lifted", AppTheme.Daylight, Depth.Lifted),
            Triple("classic-flat", AppTheme.JanitorClassic, Depth.Flat),
        )
        for ((name, theme, depth) in looks) {
            val scene = ImageComposeScene(width = 780, height = 900, density = Density(2f)) {
                ButlerTheme(theme = theme, depth = depth) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchBox(value = "", onValueChange = {}, placeholder = "Search characters", modifier = Modifier.padding(horizontal = 12.dp))
                        ModeBar(sort = CharacterSort.Popular, special = null, onSort = {}, onSpecial = {})
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StripChip("Favorites", selected = false, onClick = {})
                            StripChip("Following", selected = true, onClick = {})
                            StripChip("Fantasy", selected = false, onClick = {}, tint = ButlerTheme.colors.thought)
                            StripChip("Romance", selected = true, onClick = {}, tint = ButlerTheme.colors.romance)
                        }
                        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().card().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seraphine, the Lantern Keeper", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("She keeps the last light on the harbour wall and asks every sailor one question.", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Female", "Fantasy", "OC", "Slow burn").forEach { TagPill(it) }
                            }
                        }
                        Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().card(shape = MaterialTheme.shapes.large, color = ButlerTheme.colors.surfaceHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Look", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("Depth", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                            Text("Corners", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
                        }
                        androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 12.dp)) {
                            com.cherry.butler.ui.components.ButlerMenu(expanded = true, onDismissRequest = {}) {
                                listOf("Popular", "Latest", "Relevance").forEach {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(it, style = MaterialTheme.typography.titleMedium) }, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
            var image = scene.render(0)
            for (t in 1..30) image = scene.render(t * 16_000_000L)
            File(out, "panes-$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
