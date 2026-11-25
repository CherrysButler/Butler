package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Lift

/*
 * A tool for settling the glass by eye, not a feature: the numbers the glass is made with,
 * held where a set of sliders can move them while the glass is on screen. Glass.kt reads
 * them; the panel below floats over whatever screen is up, so a list can be scrolled under
 * the nav bar with one hand and a number moved with the other.
 *
 * Out of reach since 0.5.3: the numbers were settled on a phone and are Glass.kt's own
 * (every value here starts as one of them), so the switch in Settings > Look and the panel's
 * place in MainActivity were taken out for the release. To tune again, show GlassTuner()
 * over the app and set GlassTuning.shown.
 */
object GlassTuning {
    /** The panel is up. */
    var shown by mutableStateOf(false)

    /** How far the picture is pulled at the rim's edge, and how deep the rim runs (dp). */
    var pull by mutableFloatStateOf(Glass.RIM_PULL)
    var depth by mutableFloatStateOf(Glass.RIM_DEPTH)

    /** The blur's radius in dp; under 0 it is Apple's own, by the pane's size. */
    var blur by mutableFloatStateOf(Glass.BLUR)

    /** The blur behind the message box once it has opened out (dp). */
    var blurOpen by mutableFloatStateOf(Glass.BLUR_OPEN)

    /** How far apart red and blue are read at the edge (dp). */
    var fringe by mutableFloatStateOf(Glass.FRINGE)

    /** How much of the outward-bent picture is laid over the last point of the rim. */
    var outer by mutableFloatStateOf(Glass.OUTER_MIX)

    /** The rim highlight's strength. */
    var highlight by mutableFloatStateOf(Glass.HIGHLIGHT)

    /** How far short of the unlit corners a box's rim light falls. */
    var gaps by mutableFloatStateOf(Glass.RIM_GAPS)


    /** Back to what the glass is made with (Glass, in Glass.kt). */
    fun reset() {
        pull = Glass.RIM_PULL
        depth = Glass.RIM_DEPTH
        blur = Glass.BLUR
        blurOpen = Glass.BLUR_OPEN
        fringe = Glass.FRINGE
        outer = Glass.OUTER_MIX
        highlight = Glass.HIGHLIGHT
        gaps = Glass.RIM_GAPS
    }

    /** Under 0 the blur is Apple's own, by the pane's size. */
    const val BLUR_BY_SIZE = -1f
}

/** The panel: one slider to a number, each with its value beside its name. */
@Composable
fun GlassTuner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .card(color = ButlerTheme.colors.surfaceHigh, lift = Lift.Float)
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Glass", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = { GlassTuning.reset() }) { Text("Reset") }
            TextButton(onClick = { GlassTuning.shown = false }) { Text("Close") }
        }
        Tune("Pull", GlassTuning.pull, 0f..80f, whole = true) { GlassTuning.pull = it }
        Tune("Rim", GlassTuning.depth, 2f..40f, whole = true) { GlassTuning.depth = it }
        Tune("Blur", GlassTuning.blur, -1f..24f, whole = true, lowest = "By size") { GlassTuning.blur = it }
        Tune("Open blur", GlassTuning.blurOpen, 0f..32f, whole = true) { GlassTuning.blurOpen = it }
        Tune("Fringe", GlassTuning.fringe, 0f..8f, whole = false) { GlassTuning.fringe = it }
        Tune("Outer", GlassTuning.outer, 0f..1f, whole = false) { GlassTuning.outer = it }
        Tune("Shine", GlassTuning.highlight, 0f..1f, whole = false) { GlassTuning.highlight = it }
        Tune("Box gaps", GlassTuning.gaps, 0f..3f, whole = false) { GlassTuning.gaps = it }
    }
}

@Composable
private fun Tune(
    name: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    whole: Boolean,
    /** What the bottom of the range is called, where it is a setting and not a number. */
    lowest: String? = null,
    onChange: (Float) -> Unit,
) {
    val shown = when {
        lowest != null && value < 0f -> lowest
        whole -> value.toInt().toString()
        else -> ((value * 100).toInt() / 100f).toString()
    }
    Row(modifier = Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed, modifier = Modifier.width(60.dp))
        Text(shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(58.dp))
        Spacer(Modifier.width(4.dp))
        Slider(
            value = value,
            onValueChange = { onChange(if (whole) kotlin.math.round(it) else it) },
            valueRange = range,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
    }
}
