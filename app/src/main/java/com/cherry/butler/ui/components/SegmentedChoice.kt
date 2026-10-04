package com.cherry.butler.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion

/** A segmented choice, as Janitor's View, Source and Oldest/Latest rows: one framed bar. */
@Composable
fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.medium)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val fill by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = Motion.enter(Motion.SHORT),
                label = "choice-fill",
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textMed,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(fill)
                    .clickable { onSelect(i) }
                    .height(36.dp)
                    .padding(top = 8.dp),
            )
        }
    }
}
