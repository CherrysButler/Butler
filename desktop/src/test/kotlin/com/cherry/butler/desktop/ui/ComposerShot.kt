package com.cherry.butler.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.feature.chat.Composer
import com.cherry.butler.feature.chat.Mark
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the phone's message box offscreen (the words over one row of keys), at a phone's
 * width, to check its proportions. Run with COMPOSER_SHOT=<out dir>; skipped otherwise.
 */
class ComposerShot {
    @Test
    fun shots() {
        val out = System.getenv("COMPOSER_SHOT") ?: return
        val drafts = listOf(
            "empty" to "",
            "line" to "*I look up from the menu.* \"Something sweet, you said?\"",
            "long" to "*I look up from the menu, rain still running off my coat and onto the floor.* \"Something sweet, you said? I did promise.\" *I wave the waiter over and order two of whatever she was staring at.*",
            "command" to "/",
        )
        // The PC's box at rest, to see it kept its one row.
        shot(File(out, "composer-desk.png"), 900, 200, 1f, AppTheme.JanitorClassic, drafts[1].second, desk = true)
        for ((name, draft) in drafts) {
            for (theme in listOf(AppTheme.JanitorClassic, AppTheme.Daylight)) {
                shot(File(out, "composer-$name-${theme.name}.png"), 780, 520, 2f, theme, draft, desk = false)
                // Written in: a tap in the box opens it.
                shot(File(out, "composer-$name-${theme.name}-open.png"), 780, 520, 2f, theme, draft, desk = false, tap = 100)
                // Part of the way open (with no keyboard here, it opens on a timer after a pause).
                shot(File(out, "composer-$name-${theme.name}-mid.png"), 780, 520, 2f, theme, draft, desk = false, tap = 38)
            }
        }
    }

    private fun shot(file: File, width: Int, height: Int, density: Float, theme: AppTheme, draft: String, desk: Boolean, tap: Int = 0) {
                val scene = ImageComposeScene(width = width, height = height, density = Density(density)) {
                    ButlerTheme(theme = theme, depth = Depth.Lifted) {
                      androidx.compose.runtime.CompositionLocalProvider(com.cherry.butler.feature.chat.LocalEnterSends provides desk) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
                            Composer(
                                draft = draft,
                                onDraftChanged = {},
                                busy = false,
                                onSend = {},
                                onStop = {},
                                persona = PersonaOption(id = null, name = "Cherry", avatarUrl = null),
                                onPickPersona = {},
                                onWrite = {},
                                rich = true,
                                richDefault = Mark.Action,
                                onAddPicture = {},
                            )
                        }
                      }
                    }
                }
                var image = scene.render(0)
                for (t in 1..40) image = scene.render(t * 16_000_000L)
                if (tap > 0) {
                    val at = androidx.compose.ui.geometry.Offset(width * 0.4f, height - 68f)
                    scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, at)
                    scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, at)
                    for (t in 41..(40 + tap)) image = scene.render(t * 16_000_000L)
                }
                file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                scene.close()
    }
}
