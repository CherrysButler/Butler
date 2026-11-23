package com.cherry.butler.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.generation.ContextPreview
import com.cherry.butler.ui.components.card

/**
 * The chat panel's Context page: what Butler sends with the next reply, part by part with its
 * size (the memory, who is played, the lore the scene brings in, the standing instructions and
 * the story's lines), under how full the model's context was on the last reply. It is rebuilt
 * as the chat changes. Only live values: every number is this chat's, now.
 */
@Composable
internal fun ContextPage(viewModel: ChatViewModel) {
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val persona by viewModel.activePersona.collectAsStateWithLifecycle()
    val note by viewModel.pendingNote.collectAsStateWithLifecycle()
    val busyPercent by viewModel.contextPercent.collectAsStateWithLifecycle()
    val preview by produceState<ContextPreview?>(null, transcript, chat?.summary, persona?.id, busyPercent) {
        value = viewModel.contextPreview()
    }
    val p = preview ?: return
    val characterName = chat?.shownName?.ifBlank { null } ?: "Character"
    val instructions = listOfNotNull(p.instructions, note?.takeIf { it.isNotBlank() }).joinToString("\n\n").takeIf { it.isNotEmpty() }

    val colors = ButlerTheme.colors
    val parts = buildList {
        p.memory?.let { add(Part("Memory", it.length, colors.thought)) }
        if (p.personaAppearance.isNotBlank()) add(Part(p.personaName, p.personaAppearance.length, colors.romance))
        if (p.lore.isNotEmpty()) add(Part("Lore", p.loreChars, colors.success))
        instructions?.let { add(Part("Instructions", it.length, colors.warn)) }
        add(Part("Story", p.historyChars, colors.speech))
    }
    val total = parts.sumOf { it.chars }.coerceAtLeast(1)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp)) {
        p.percent?.let { percent ->
            item {
                Heading("Context", "$percent%")
                Bar { Box(Modifier.fillMaxHeight().fillMaxWidth(percent.coerceIn(0, 100) / 100f).background(MaterialTheme.colorScheme.primary)) }
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Heading("Next reply", tokens(total))
            // The parts, to scale: how much of what Butler sends each one is.
            Bar {
                Row(Modifier.fillMaxSize()) {
                    parts.forEach { part ->
                        Box(Modifier.fillMaxHeight().weight(part.chars.coerceAtLeast(1).toFloat()).background(part.color))
                    }
                }
            }
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().card(shape = MaterialTheme.shapes.medium).padding(vertical = 6.dp)) {
                parts.forEach { part ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(part.color))
                        Spacer(Modifier.width(10.dp))
                        Text(part.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(tokens(part.chars), style = MaterialTheme.typography.labelMedium, color = colors.textMed)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (p.lore.isNotEmpty()) {
            item { Heading("Lore", p.lore.size.toString()) }
            itemsIndexed(p.lore) { _, entry ->
                Expandable(
                    head = {
                        if (entry.constant) {
                            Icon(Icons.Rounded.PushPin, contentDescription = "Always on", tint = colors.textLow, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(entry.name.ifBlank { "Entry" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (entry.chance < 100) {
                            Text("${entry.chance}%", style = MaterialTheme.typography.labelMedium, color = colors.warn)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(tokens(entry.content.length), style = MaterialTheme.typography.labelMedium, color = colors.textMed)
                    },
                    body = entry.content,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        p.memory?.let { memory ->
            item {
                Heading("Memory", tokens(memory.length))
                Expandable(head = null, body = memory, collapsedLines = 4)
                Spacer(Modifier.height(8.dp))
            }
        }
        instructions?.let { text ->
            item {
                Heading("Instructions", tokens(text.length))
                Expandable(head = null, body = text, collapsedLines = 4)
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Heading("Story", p.lines.size.toString()) }
        // Newest first: the lines nearest the reply are the ones that shape it.
        itemsIndexed(p.lines.asReversed()) { _, line ->
            Expandable(
                head = {
                    Text(
                        if (line.isBot) characterName else p.personaName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (line.isBot) MaterialTheme.colorScheme.primary else colors.speech,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(tokens(line.text.length), style = MaterialTheme.typography.labelMedium, color = colors.textMed)
                },
                body = line.text,
                collapsedLines = 2,
                open = false,
            )
        }
    }
}

private class Part(val name: String, val chars: Int, val color: Color)

/** A count of tokens the way the page says it: 840, 1.2k, 12k. */
private fun tokens(chars: Int): String {
    val n = ContextPreview.tokens(chars)
    return when {
        n < 1000 -> "$n"
        n < 10_000 -> "${n / 1000}.${(n % 1000) / 100}k"
        else -> "${n / 1000}k"
    }
}

@Composable
private fun Heading(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
    }
}

@Composable
private fun Bar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ButlerTheme.colors.outlineFaint)) { content() }
}

/** A card of text that shows a few lines and opens to all of it on a click. */
@Composable
private fun Expandable(
    head: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)?,
    body: String,
    collapsedLines: Int = 0,
    open: Boolean = false,
) {
    var opened by remember(body) { mutableStateOf(open) }
    Column(
        Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .card(shape = MaterialTheme.shapes.medium)
            .clickable { opened = !opened }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (head != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = head)
        if (opened || collapsedLines > 0) {
            if (head != null) Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
                maxLines = if (opened) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
