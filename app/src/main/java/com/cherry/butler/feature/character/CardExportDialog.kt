package com.cherry.butler.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.CharacterCard
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.HairlineRule

/** Where an export stands: reading the definition, ready to save, or why it can't be. */
sealed interface CardExportState {
    /** Reading; [viaProxy] when it comes through Janitor's proxy prompt (a few seconds). */
    data class Reading(val viaProxy: Boolean) : CardExportState
    data class Ready(val card: CharacterCard, val viaProxy: Boolean) : CardExportState
    data class Failed(val message: String) : CardExportState
    /** Hidden, and proxies not allowed: there's no definition to put in a card. */
    data object Hidden : CardExportState
    /** Hidden, proxies allowed, but no chat with it yet to read the definition from. */
    data object NeedsChat : CardExportState
}

/**
 * Export card: the character as it'll go out (its picture and name), what's in the card once
 * it's read, and Save. A hidden definition is read through Janitor's proxy prompt first.
 */
@Composable
fun CardExportDialog(
    state: CardExportState,
    name: String,
    creator: String?,
    avatarUrl: String?,
    saving: Boolean,
    onSave: (CharacterCard) -> Unit,
    onStartChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.cherry.butler.ui.components.ButlerAlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Export card", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(url = avatarUrl, name = name, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!creator.isNullOrBlank()) Text("@$creator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Character card PNG (V2)", style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow)
                    }
                }
                HairlineRule()
                when (state) {
                    is CardExportState.Reading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = ButlerTheme.colors.surfaceHigh,
                        )
                        Text(
                            if (state.viaProxy) "The creator hides the definition but allows proxies, so Butler is reading it from the prompt Janitor puts together for a message in your chat. Nothing is sent to your proxy or added to the chat."
                            else "Putting the card together…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ButlerTheme.colors.textMed,
                        )
                    }
                    is CardExportState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val c = state.card
                        Contents("Personality", words(c.description))
                        Contents("Scenario", words(c.scenario))
                        Contents("Example dialogue", words(c.exampleMessages))
                        val intros = 1 + c.alternateGreetings.size
                        Contents("Intros", if (c.firstMessage.isBlank()) null else if (intros == 1) "1" else "$intros")
                        Contents("Tags", c.tags.size.takeIf { it > 0 }?.toString())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (state.viaProxy) "Read through Janitor's proxy prompt. " else "") + "Opens in SillyTavern, Chub, RisuAI and the like.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ButlerTheme.colors.textLow,
                        )
                    }
                    is CardExportState.Failed -> Text(state.message, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.danger)
                    CardExportState.Hidden -> Text(
                        "Its creator hides the definition and doesn't allow proxies, so there's nothing to put in a card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ButlerTheme.colors.textMed,
                    )
                    CardExportState.NeedsChat -> Text(
                        "Its creator hides the definition but allows proxies. Butler reads it from your chat with $name, so start a chat first, then export again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ButlerTheme.colors.textMed,
                    )
                }
            }
        },
        confirmButton = {
            if (state is CardExportState.Ready) {
                KeyButton(if (saving) "Saving…" else "Save card", onClick = { onSave(state.card) }, enabled = !saving, primary = true)
            }
            if (state is CardExportState.NeedsChat) KeyButton("Start a chat", onClick = onStartChat, primary = true)
        },
        dismissButton = {
            TextButton(onClick = com.cherry.butler.ui.components.leaving(onDismiss), shape = Pill) {
                Text(if (state is CardExportState.Ready || state is CardExportState.Reading) "Cancel" else "Close", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

/** One line of what the card carries; a part it hasn't got reads as "none". */
@Composable
private fun Contents(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value ?: "none", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = if (value == null) ButlerTheme.colors.textLow else ButlerTheme.colors.textMed)
    }
}

private fun words(text: String): String? {
    val n = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    return when (n) { 0 -> null; 1 -> "1 word"; else -> "%,d words".format(n) }
}
