package com.cherry.butler.desktop

// TEMPORARY: an offscreen render of the depth system on every look, to inspect by eye. Remove before release.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.CustomColors
import com.cherry.butler.core.design.Depth
import com.cherry.butler.core.design.Lift
import com.cherry.butler.core.design.SmoothCornerShape
import com.cherry.butler.core.design.liftedShape
import com.cherry.butler.ui.components.card
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "depth-snaps").apply { mkdirs() }
    for (theme in AppTheme.entries) for (depth in Depth.entries) {
        val scene = ImageComposeScene(420 * 2, 980 * 2, Density(2f)) {
            ButlerTheme(theme = theme, custom = CustomColors.Default, depth = depth) { Board("${theme.label} · ${depth.label}") }
        }
        val img = scene.render()
        File(out, "${theme.key}-${depth.key}.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
    // The corner itself, large: continuous (red) over a plain quarter circle (grey).
    val geo = ImageComposeScene(320 * 2, 320 * 2, Density(2f)) {
        Canvas(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
            val s = Size(size.width, size.height)
            val r = 90.dp
            drawOutline(RoundedCornerShape(r).createOutline(s, LayoutDirection.Ltr, this), Color(0xFF999999), style = Stroke(2f))
            drawOutline(SmoothCornerShape(r).createOutline(s, LayoutDirection.Ltr, this), Color(0xFFE0453A), style = Stroke(2f))
        }
    }
    File(out, "corner.png").writeBytes(geo.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    // Android draws an elevation shadow only under a convex outline (before API 30): check ours are.
    val d = Density(2.625f)
    for ((w, h, r) in listOf(Triple(300f, 120f, 16f), Triple(56f, 56f, 11f), Triple(1000f, 150f, 28f), Triple(100f, 40f, 28f), Triple(40f, 40f, 20f))) {
        val o = SmoothCornerShape(r.dp).createOutline(Size(w * d.density, h * d.density), LayoutDirection.Ltr, d)
        val convex = (o as? Outline.Generic)?.path?.asSkiaPath()?.isConvex
        println("outline ${w}x$h r$r convex=$convex")
    }
    val top = SmoothCornerShape(topStart = 28.dp, topEnd = 28.dp).createOutline(Size(1080f, 1500f), LayoutDirection.Ltr, d)
    println("sheet convex=${(top as? Outline.Generic)?.path?.asSkiaPath()?.isConvex}")
    println("wrote ${out.absolutePath}")
}

@Composable
private fun Board(title: String) {
    val c = ButlerTheme.colors
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        // Two Browse cards side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) { i ->
                Column(Modifier.weight(1f).card()) {
                    Box(Modifier.fillMaxWidth().height(130.dp).background(if (i == 0) Color(0xFF6B4E71) else Color(0xFF3E6A7A)))
                    Column(Modifier.padding(10.dp)) {
                        Text(if (i == 0) "Ash, the night clerk" else "Sera Vale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("12.4k chats", style = MaterialTheme.typography.bodySmall, color = c.textMed)
                    }
                }
            }
        }
        // A chat row with an avatar.
        Row(Modifier.fillMaxWidth().card().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(liftedShape(44.dp * 0.2f)).background(Color(0xFF8A5A44)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Ash, the night clerk", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("The lobby light flickers as you walk in…", style = MaterialTheme.typography.bodySmall, color = c.textMed)
            }
        }
        // A grouped settings section.
        Column(Modifier.fillMaxWidth().card(MaterialTheme.shapes.large)) {
            listOf("Look", "Depth", "Text size").forEach {
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // Bubbles.
        Box(Modifier.fillMaxWidth().card(liftedShape(topStart = 6.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)).padding(14.dp)) {
            Text("\"You're late,\" he says, not looking up from the ledger.", color = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(260.dp).card(liftedShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp), color = c.surfaceHigh).padding(14.dp)) {
                Text("I had to take the long way round.", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        // The composer, floating.
        Row(
            Modifier.fillMaxWidth().height(52.dp).card(MaterialTheme.shapes.large, c.surfaceHigh, lift = Lift.Float).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Write a message", Modifier.weight(1f).padding(start = 8.dp), color = c.textLow)
            Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.weight(1f))
        // The nav island.
        Row(
            Modifier.fillMaxWidth().height(56.dp).card(MaterialTheme.shapes.extraLarge, c.chrome, lift = Lift.Float),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { i -> Box(Modifier.size(22.dp).clip(MaterialTheme.shapes.extraSmall).background(if (i == 0) MaterialTheme.colorScheme.primary else c.textLow)) }
        }
        // A sheet's top edge, over everything.
        Box(Modifier.fillMaxWidth().height(70.dp).card(com.cherry.butler.core.design.SheetShape, c.surfaceHigh, lift = Lift.Overlay))
    }
}
