package com.cherry.butler.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.LocalThinkingLook
import com.cherry.butler.core.design.LocalThoughtLook
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.ThinkingLook
import com.cherry.butler.core.design.ThoughtLook
import com.cherry.butler.feature.chat.ThinkingLine

/**
 * The thinking line's two choices, each option shown as it will look: the animation while a
 * reply is thought out, and the shape a finished thought folds to. The previews are the real
 * line, so what is picked here is exactly what the chat shows.
 */
@Composable
internal fun ThinkingSection(viewModel: SettingsViewModel) {
    val thinking by viewModel.thinkingLook.collectAsStateWithLifecycle()
    val thought by viewModel.thoughtLook.collectAsStateWithLifecycle()
    SettingsSection(title = "While thinking", footnote = "What the line shows while the model thinks a reply out.") {
        ThinkingLook.entries.forEach { look ->
            LookChoice(title = look.label, subtitle = look.blurb, selected = look == thinking, onClick = { viewModel.setThinkingLook(look) }) {
                CompositionLocalProvider(LocalThinkingLook provides look) {
                    ThinkingLine(text = LIVE_THOUGHT, streaming = true, onOpen = { viewModel.setThinkingLook(look) })
                }
            }
        }
    }
    SettingsSection(title = "After thinking", footnote = "What a finished thought folds to above the reply. A tap on it opens the whole thought.") {
        ThoughtLook.entries.forEach { look ->
            LookChoice(title = look.label, subtitle = look.blurb, selected = look == thought, onClick = { viewModel.setThoughtLook(look) }) {
                CompositionLocalProvider(LocalThoughtLook provides look, LocalThinkingLook provides thinking) {
                    ThinkingLine(text = SAMPLE_THOUGHT, streaming = false, onOpen = { viewModel.setThoughtLook(look) }, durationMs = 12_300L)
                }
            }
        }
    }
}

/** A choice with its preview underneath: the radio and words, then the thing itself. */
@Composable
private fun LookChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ring by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                animationSpec = Motion.enter(Motion.SHORT),
                label = "look-ring",
            )
            val dot by animateFloatAsState(if (selected) 1f else 0f, animationSpec = Motion.enter(Motion.SHORT), label = "look-dot")
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, ring, CircleShape)
                    .padding(5.dp)
                    .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 14.dp)) { preview() }
        RowDivider()
    }
}

private const val SAMPLE_THOUGHT = "She asked about the letter; the honest answer costs more than a lie, so start with the lie's price."

/**
 * What the while-thinking previews are fed: a thought part-way written. The dither thickens as a
 * thought grows, so a one-line sample showed it barely begun, sparser than a chat ever looks.
 */
private val LIVE_THOUGHT = (SAMPLE_THOUGHT + " ").repeat(9)
