package com.cherry.butler.feature.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.BrowseSource
import com.cherry.butler.core.model.NsfwMode
import com.cherry.butler.feature.settings.NumberRow
import com.cherry.butler.feature.settings.SettingsSection
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.TagPill

/**
 * The filter sheet, laid out as Janitor's: View, Source, Tags, and the content minimums.
 * Every change applies at once; Reset puts them all back. The Proxy switch and the
 * minimums filter the fetched rows on the phone, as the official app does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    query: BrowseQuery,
    tagNames: List<String>,
    onMode: (NsfwMode) -> Unit,
    onSource: (BrowseSource) -> Unit,
    onOpenTags: () -> Unit,
    onMinMessages: (Long) -> Unit,
    onMinTokens: (Int) -> Unit,
    onProxyOnly: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset, enabled = query.filterCount > 0, shape = Pill) {
                    Text("Reset", color = if (query.filterCount > 0) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow)
                }
            }

            Label("View")
            SegmentedChoice(
                options = NsfwMode.entries.map { it.label },
                selected = NsfwMode.entries.indexOf(query.mode),
                onSelect = { onMode(NsfwMode.entries[it]) },
            )

            Label("Source")
            SegmentedChoice(
                options = BrowseSource.entries.map { it.label },
                selected = BrowseSource.entries.indexOf(query.source),
                onSelect = { onSource(BrowseSource.entries[it]) },
            )

            Label("Tags")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onOpenTags)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (tagNames.isEmpty()) "Any tag" else "${tagNames.size} chosen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Choose tags", tint = ButlerTheme.colors.textLow)
                }
                if (tagNames.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) { tagNames.forEach { TagPill(it) } }
                }
            }

            SettingsSection(title = "Content", footnote = "Characters that don't match are hidden from the grid.") {
                SwitchRow("Proxy", "Only characters whose creator allows proxies", query.proxyOnly, onProxyOnly)
                NumberRow("Messages at least", null, query.minMessages.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), { onMinMessages(it.toLong()) }, 0..999_999_999)
                NumberRow("Tokens at least", null, query.minTokens, onMinTokens, 0..999_999)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = ButlerTheme.colors.textMed,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}
