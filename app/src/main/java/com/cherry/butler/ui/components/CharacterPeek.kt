package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.feature.chats.KeyButton

/**
 * A card held down: the whole of what its face only starts to show. Every tag, the creator's
 * own (#) as well as Janitor's, the token count, chats and messages, and whether it takes
 * proxies, with Open to go to its page.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterPeekSheet(character: Character, onOpen: () -> Unit, onDismiss: () -> Unit) {
    com.cherry.butler.ui.components.ButlerSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = character.avatarUrl, name = character.name, size = 56.dp, obscured = character.isImageNsfw)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("@${character.creatorName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // The numbers, in one row of small boxes: what to know before opening it.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Stat(if (character.totalTokens > 0) "%,d".format(character.totalTokens) else "–", "tokens", Modifier.weight(1f))
                Stat(character.chatCount.compactCount(), "chats", Modifier.weight(1f))
                Stat(character.messageCount.compactCount(), "messages", Modifier.weight(1f))
                Stat(if (character.isProxyEnabled) "Yes" else "No", "proxy", Modifier.weight(1f), warn = !character.isProxyEnabled)
            }
            if (character.tagNames.isNotEmpty()) {
                Section("Tags")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    character.tagNames.forEach { TagPill(it) }
                    if (character.isNsfw) TagPill("NSFW")
                }
            }
            if (character.customTags.isNotEmpty()) {
                Section("Creator's tags")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    character.customTags.forEach { TagPill("#${it.removePrefix("#")}") }
                }
            }
            if (character.blurb.isNotBlank()) {
                Text(character.blurb, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            val leave = LocalLeave.current
            KeyButton("Open", onClick = { leave(onOpen) }, primary = true, modifier = Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Column(
        modifier = modifier.card(color = ButlerTheme.colors.surfaceHigh).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = if (warn) ButlerTheme.colors.warn else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1)
    }
}

/** The card a long press is showing, held where the cards are drawn. */
class PeekState {
    var shown: Character? by mutableStateOf(null)
}

@Composable
fun rememberPeekState(): PeekState = remember { PeekState() }

/** Where a held card shows itself; null outside a [PeekHost]. */
val LocalPeek = androidx.compose.runtime.staticCompositionLocalOf<PeekState?> { null }

/** Every card under it can be held to show everything about it; Open goes to [onOpen]. */
@Composable
fun PeekHost(onOpen: (String) -> Unit, content: @Composable () -> Unit) {
    val state = rememberPeekState()
    androidx.compose.runtime.CompositionLocalProvider(LocalPeek provides state, content = content)
    state.shown?.let { c ->
        CharacterPeekSheet(character = c, onOpen = { state.shown = null; onOpen(c.id) }, onDismiss = { state.shown = null })
    }
}
