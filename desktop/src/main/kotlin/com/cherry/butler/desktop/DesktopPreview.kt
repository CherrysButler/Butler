package com.cherry.butler.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.feature.chat.ThinkingLine
import com.cherry.butler.core.design.ThinkingLook
import com.cherry.butler.core.design.ThoughtLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Development only (desktop-dev; removed before release): `janitor://butler-preview` draws the
 * thinking line off screen at a few moments of its animation, in each look, to files/preview,
 * so a drawing can be checked without anyone opening a chat. Nothing on screen changes.
 */
object DesktopPreview {
    fun isPreview(link: String) = link.startsWith("janitor://butler-preview")

    suspend fun run() = withContext(Dispatchers.Main) {
        val dir = File(DesktopContext.filesDir, "preview").apply { deleteRecursively(); mkdirs() }
        // Every look of the line: the three animations while thinking, the three shapes after.
        val cases = buildList {
            for (look in ThinkingLook.entries) add(Triple("thinking-${look.name}", look, null))
            for (look in ThoughtLook.entries) add(Triple("thought-${look.name}", ThinkingLook.Butler, look))
        }
        for (theme in listOf(AppTheme.JanitorClassic, AppTheme.Daylight)) {
            for ((name, thinking, thought) in cases) {
                val scene = ImageComposeScene(width = 1800, height = 228, density = Density(6f)) {
                    ButlerTheme(theme = theme, thinkingLook = thinking, thoughtLook = thought ?: ThoughtLook.Stripe) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            ThinkingLine(
                                text = "Considering the scene before answering",
                                streaming = thought == null,
                                onOpen = {},
                                modifier = Modifier.padding(0.dp),
                                durationMs = 12_300L,
                            )
                        }
                    }
                }
                for ((i, ms) in listOf(0L, 150L, 300L, 450L).withIndex()) {
                    val image = scene.render(ms * 1_000_000L)
                    image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(dir, "${theme.name}-$name-$i.png").writeBytes(it) }
                }
                scene.close()
            }
        }
        DesktopToasts.show("Preview saved")
    }
}
