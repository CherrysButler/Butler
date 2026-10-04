package com.cherry.butler.feature.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.cherry.butler.core.data.TextLookPrefs
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.RpLook
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.design.proseStyle
import com.cherry.butler.ui.components.card
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CustomizeViewModel @Inject constructor(private val prefs: TextLookPrefs) : ViewModel() {
    val look: StateFlow<RpLook> = prefs.look
    fun update(change: (RpLook) -> RpLook) = prefs.update(change)
    fun reset() = prefs.reset()
}

/**
 * How roleplay text is drawn, the reader's way, with a preview that is the real renderer.
 * Changes apply everywhere at once and are kept on the phone.
 */
@Composable
fun CustomizeScreen(onBack: () -> Unit, viewModel: CustomizeViewModel = hiltViewModel()) {
    val look by viewModel.look.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Customize text", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::reset, enabled = look != RpLook(), shape = Pill) {
                Text("Reset", color = if (look != RpLook()) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow)
            }
        }

        // The preview stays put while the controls scroll under it.
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .card(shape = MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ButlerTheme.colors.textLow)
            RpText(
                text = SAMPLE,
                style = proseStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
                paragraphSpacing = 8.dp,
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSection(title = "Text") {
                SliderRow(
                    title = "Size",
                    value = look.textSize.toFloat(),
                    range = 13f..22f,
                    step = 1f,
                    format = { "${it.toInt()}" },
                    onCommit = { v -> viewModel.update { it.copy(textSize = v.toInt()) } },
                )
                SwitchRow("Italic actions", null, look.italicActions, { on -> viewModel.update { it.copy(italicActions = on) } })
                SwitchRow("Quote marks", null, look.showQuotes, { on -> viewModel.update { it.copy(showQuotes = on) } })
            }
            SettingsSection(title = "Colours") { Colours(look, viewModel::update) }
        }
    }
}

/** The five kinds of text the colours apply to, each with how it reads and writes [RpLook]. */
private enum class Ink(val label: String, val get: (RpLook) -> Long?, val set: (RpLook, Long?) -> RpLook) {
    Dialogue("Dialogue", { it.speech }, { l, c -> l.copy(speech = c) }),
    Actions("Actions", { it.action }, { l, c -> l.copy(action = c) }),
    Narration("Narration", { it.narration }, { l, c -> l.copy(narration = c) }),
    Bold("Bold", { it.strong }, { l, c -> l.copy(strong = c) }),
    Thoughts("Thoughts", { it.thought }, { l, c -> l.copy(thought = c) }),
}

/**
 * One kind of text at a time: its chips across the top (each wearing its colour), then one
 * grid of swatches for the chosen kind. "Theme" is the look's own colour, light or dark.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Colours(look: RpLook, update: ((RpLook) -> RpLook) -> Unit) {
    var target by rememberSaveable { mutableStateOf(Ink.Dialogue) }
    val colors = ButlerTheme.colors
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun shown(ink: Ink): Color = ink.get(look)?.let { Color(it) } ?: when (ink) {
        Ink.Dialogue -> colors.speech
        Ink.Actions -> colors.textMed
        Ink.Thoughts -> colors.thought
        Ink.Narration, Ink.Bold -> look.narration?.let { Color(it) } ?: onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ink.entries.forEach { ink ->
            val on = ink == target
            Row(
                modifier = Modifier
                    .clip(Pill)
                    .background(if (on) ButlerTheme.colors.surfaceHigh else Color.Transparent)
                    .clickable { target = ink }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(shown(ink), CircleShape))
                Text(
                    text = ink.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
        }
    }
    val value = target.get(look)
    // An even grid, eight across: "Theme" first, then the swatches.
    val cells = listOf<Long?>(null) + SWATCHES
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cells.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { argb ->
                    Swatch(color = argb?.let { Color(it) }, chosen = value == argb, modifier = Modifier.weight(1f)) { update { target.set(it, argb) } }
                }
            }
        }
    }
}

/** A colour to tap; [color] null is "Theme", drawn as the ground with a slash of the look's ink. */
@Composable
private fun Swatch(color: Color?, chosen: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ring = if (chosen) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.rule
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.background)
            .border(if (chosen) 2.dp else 1.dp, ring, CircleShape)
            .clickable(onClickLabel = if (color == null) "Theme colour" else null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            chosen -> Icon(
                Icons.Rounded.Check,
                contentDescription = "Chosen",
                tint = if ((color ?: MaterialTheme.colorScheme.background).luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(16.dp),
            )
            color == null -> Text("A", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ButlerTheme.colors.textMed)
        }
    }
}

private const val SAMPLE =
    "*She glances up from her book, unimpressed.* \"You're late,\" she says. **Again.**\n\n" +
        "`He always is.` *A small smile slips through anyway.*"

/** Readable on the dark looks; the "Theme" choice adapts for Daylight. */
private val SWATCHES = listOf(
    0xFFF3F3F1, 0xFFC3C6CB, 0xFF90949B, 0xFFE8D5B0, 0xFFF2B5A0, 0xFFF28FAD, 0xFFEA5A4F, 0xFFF2A65E,
    0xFFE8C55A, 0xFF8FD6B0, 0xFF6CC4C4, 0xFF9CC3F0, 0xFF7FA6F5, 0xFFC3A8EE, 0xFFA98BF2,
)
