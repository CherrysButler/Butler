package com.cherry.butler.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.ModelCatalog
import com.cherry.butler.core.design.AppTheme
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Depth
import com.cherry.butler.feature.settings.ModelField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Renders the model picker offscreen, with the list OpenRouter really serves, so its look can be
 * checked without driving the app. Run with MODEL_SHOT=<models.json>,<out dir> in the environment; skipped otherwise.
 */
class ModelFieldShot {
    @Test
    fun shots() {
        val arg = System.getenv("MODEL_SHOT") ?: return
        val (source, out) = arg.split(',').let { it[0] to it[1] }
        val data = Json.parseToJsonElement(File(source).readText()).jsonObject["data"]!!.jsonArray
        val models = data.mapNotNull { ModelCatalog.parse(it.jsonObject, setOf("anthropic/claude-sonnet-5.5")) }
        val state = ModelCatalog.State(models)
        val value = models.first { it.id.startsWith("anthropic/") }.id
        fun shot(name: String, theme: AppTheme, height: Int, content: @Composable () -> Unit) {
            val scene = ImageComposeScene(width = 760, height = height, density = Density(2f)) {
                ButlerTheme(theme = theme, depth = Depth.Lifted) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.width(380.dp)) { content() }
                    }
                }
            }
            // A few frames: the picker opens, asks for focus, and its list expands.
            var image = scene.render(0)
            for (t in 1..120) image = scene.render(t * 16_000_000L)
            File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
        for (theme in listOf(AppTheme.JanitorClassic, AppTheme.LightsOut, AppTheme.Daylight)) {
            shot("closed-${theme.name}", theme, 220) {
                ModelField("Model for OpenRouter", value, {}, state, {})
            }
            shot("open-${theme.name}", theme, 1040) {
                ModelField("Model for OpenRouter", value, {}, state, {}, startOpen = true)
            }
            shot("search-${theme.name}", theme, 1040) {
                ModelField("Model for OpenRouter", value, {}, state, {}, startOpen = true, startQuery = "claude son")
            }
            shot("typed-${theme.name}", theme, 520) {
                ModelField("Model for OpenRouter", value, {}, state, {}, startOpen = true, startQuery = "my-own/finetune-v2")
            }
            shot("loading-${theme.name}", theme, 600) {
                ModelField("Model for OpenRouter", value, {}, ModelCatalog.State(loading = true), {}, startOpen = true)
            }
            shot("failed-${theme.name}", theme, 420) {
                ModelField("Model for OpenRouter", value, {}, ModelCatalog.State(failed = true), {}, startOpen = true, startQuery = "x/y")
            }
        }
    }
}
