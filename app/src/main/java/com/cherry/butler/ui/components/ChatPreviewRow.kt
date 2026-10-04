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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * One save slot in the chats sheet, as a card: "Chat 3" and its size on the first line,
 * when it last moved at the margin, then the last two lines of the story. Each chat is
 * its own block, so seven of them read as seven things and not as one page of prose.
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
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .card()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Chat $number",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$messageCount " + if (messageCount == 1) "message" else "messages",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.weight(1f),
            )
            updatedAt?.let { MarginFigure(RelativeTime.short(it)) }
        }
        Spacer(Modifier.height(6.dp))
        val text = preview?.takeIf { it.isNotBlank() }
        if (text != null) {
            PreviewText(text = text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
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
