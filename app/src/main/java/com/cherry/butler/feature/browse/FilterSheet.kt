package com.cherry.butler.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.model.BrowseQuery
import com.cherry.butler.core.model.NsfwMode
import com.cherry.butler.feature.settings.NumberRow
import com.cherry.butler.feature.settings.SettingsSection
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.SegmentedChoice

/**
 * The filter sheet, laid out as Janitor's: View, Source, Tags, and the content minimums.
 * Every change applies at once; Reset puts them all back. The Proxy switch and the
 * minimums filter the fetched rows on the phone, as the official app does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    query: BrowseQuery,
    onMode: (NsfwMode) -> Unit,
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

            SettingsSection(title = "Content") {
                SwitchRow("Proxy allowed", null, query.proxyOnly, onProxyOnly)
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
