package com.cherry.butler.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.markdown.RpMarkdown
import com.cherry.butler.core.util.RelativeTime

/**
 * One save slot in the chats sheet, as a card: the chat's own name (or "Chat 3" while it
 * has none) and its size on the first line, when it last moved at the margin, who it is
 * played as, then the last two lines of the story. Each chat is its own block, so seven
 * of them read as seven things and not as one page of prose.
 *
 * [onRename] and [onDelete] are offered from the key at the row's end, and from holding the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatPreviewRow(
    number: Int,
    preview: String?,
    messageCount: Int,
    updatedAt: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The chat's own name; null while Janitor has given it none. */
    title: String? = null,
    /** Who it is played as, and their picture. */
    personaName: String? = null,
    personaAvatarUrl: String? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { androidx.compose.runtime.mutableStateOf(false) }
    val offers = onRename != null || onDelete != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .card()
            .combinedClickable(onClick = onClick, onLongClick = if (offers) ({ menu = true }) else null)
            .padding(start = 14.dp, end = if (offers) 2.dp else 14.dp, top = if (offers) 2.dp else 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: "Chat $number",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$messageCount " + if (messageCount == 1) "message" else "messages",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = ButlerTheme.colors.textLow,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            updatedAt?.let { MarginFigure(RelativeTime.short(it)) }
            if (offers) {
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.IconButton(onClick = { menu = true }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Rounded.MoreHoriz,
                            contentDescription = "Rename or delete",
                            tint = ButlerTheme.colors.textMed,
                        )
                    }
                    ButlerMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onRename != null) androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Rename", style = MaterialTheme.typography.titleSmall) },
                            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Edit, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                            onClick = { menu = false; onRename() },
                        )
                        if (onDelete != null) androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Delete", style = MaterialTheme.typography.titleSmall, color = ButlerTheme.colors.danger) },
                            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = null, tint = ButlerTheme.colors.danger) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
        // Who it is played as: the same character is often played as several people.
        if (!personaName.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = if (offers) 12.dp else 0.dp)) {
                Avatar(url = personaAvatarUrl, name = personaName, size = 20.dp, initialStyle = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(6.dp))
                Text(personaName, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        val text = preview?.takeIf { it.isNotBlank() }
        if (text != null) {
            PreviewText(text = text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(end = if (offers) 12.dp else 0.dp))
        } else {
            Text(
                text = updatedAt?.let { "Started ${RelativeTime.fullDate(it)}" } ?: "Nothing written yet",
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textLow,
            )
        }
    }
}

/**
 * A chat's last line as a list shows it: plain, the way Janitor does, with the markup
 * taken out. Only the persona's name keeps its red, where it filled `{{user}}`.
 */
@Composable
fun PreviewText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = ButlerTheme.colors.textMed,
    maxLines: Int = 1,
) {
    val red = MaterialTheme.colorScheme.primary
    val annotated = remember(text, red) {
        val (plain, spans) = RpMarkdown.parseInline(text.replace('\n', ' '))
        buildAnnotatedString {
            append(plain)
            spans.filter { it.kind == RpMarkdown.InlineKind.Persona }
                .forEach { addStyle(SpanStyle(color = red), it.start, it.end) }
        }
    }
    Text(text = annotated, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
