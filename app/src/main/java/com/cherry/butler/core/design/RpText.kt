package com.cherry.butler.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.markdown.RpMarkdown
import com.cherry.butler.core.markdown.RpMarkdown.Block
import com.cherry.butler.core.markdown.RpMarkdown.InlineKind

/** The theme's answer to each RP construct. Read once per composition, passed by value. */
@Immutable
data class RpStyles(
    val strong: SpanStyle,
    val action: SpanStyle,
    val speech: SpanStyle,
    val thought: SpanStyle,
    /** The user's persona name where it filled `{{user}}`: Butler red. */
    val persona: SpanStyle,
    /**
     * Butter and the Highlights moods: not a span style but a highlighter stroke drawn behind
     * the letters ([MarkedText]), in each kind's colour. A background span filled the whole
     * (tall) line and stacked into blocks.
     */
    val marks: Map<InlineKind, Color> = emptyMap(),
) {
    fun forKind(kind: InlineKind): SpanStyle = when (kind) {
        InlineKind.Strong -> strong
        InlineKind.Action -> action
        InlineKind.Speech -> speech
        InlineKind.Thought -> thought
        InlineKind.Persona -> persona
        else -> SpanStyle()
    }
}

/** Whether Butter mode's beats are tinted in a full reply (a Butter mode sub-option). */
val LocalButterTint = androidx.compose.runtime.staticCompositionLocalOf { true }

/**
 * Speech in blue, thought in violet, action as a quieter italic: the tints that make a
 * roleplay turn scannable without a single box or bubble. Emphasis is weight, never colour.
 */
@Composable
fun rememberRpStyles(baseColor: Color): RpStyles {
    val colors = ButlerTheme.colors
    val red = MaterialTheme.colorScheme.primary
    val look = LocalRpLook.current
    val tint = LocalButterTint.current
    return remember(baseColor, colors, red, look, tint) {
        RpStyles(
            strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = look.strong?.let { Color(it) } ?: look.narration?.let { Color(it) } ?: baseColor),
            action = SpanStyle(
                fontStyle = if (look.italicActions) FontStyle.Italic else FontStyle.Normal,
                color = look.action?.let { Color(it) } ?: colors.textMed,
            ),
            speech = SpanStyle(color = look.speech?.let { Color(it) } ?: colors.speech),
            thought = SpanStyle(fontStyle = FontStyle.Italic, color = look.thought?.let { Color(it) } ?: colors.thought),
            persona = SpanStyle(color = red),
            marks = buildMap {
                if (tint) put(InlineKind.Butter, colors.warn.copy(alpha = MARK_ALPHA))
                put(InlineKind.Romantic, colors.romance.copy(alpha = MARK_ALPHA))
                put(InlineKind.Erotic, colors.desire.copy(alpha = MARK_ALPHA))
                put(InlineKind.Dangerous, colors.danger.copy(alpha = MARK_ALPHA))
                put(InlineKind.Sad, colors.speech.copy(alpha = MARK_ALPHA))
                put(InlineKind.Funny, colors.success.copy(alpha = MARK_ALPHA))
            },
        )
    }
}

/** How strong a highlighter stroke is: "ever slightly", but a clear shape. */
private const val MARK_ALPHA = 0.22f

/** The plain-narration ink: the reader's choice, else [base]. */
@Composable
fun narrationInk(base: Color): Color = LocalRpLook.current.narration?.let { Color(it) } ?: base

/**
 * Renders roleplay markdown as prose.
 *
 * In the default block layout each paragraph is its own `Text`, rules become dividers and
 * list items get a real bullet gutter, so long turns keep a reading rhythm. With [maxLines]
 * set it collapses to one clipped `AnnotatedString` — the shape a preview needs.
 */
@Composable
fun RpText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    paragraphSpacing: androidx.compose.ui.unit.Dp = 12.dp,
) {
    val styles = rememberRpStyles(color)
    val keepQuotes = LocalRpLook.current.showQuotes
    val blocks = remember(text, keepQuotes) { RpMarkdown.parse(text, keepQuotes) }
    @Suppress("NAME_SHADOWING") val color = narrationInk(color)

    if (maxLines != Int.MAX_VALUE) {
        val compact = remember(blocks, styles) { blocks.toCompactAnnotated(styles) }
        Text(
            text = compact,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            minLines = minLines,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(paragraphSpacing)) {
        for (block in blocks) {
            when (block) {
                is Block.Paragraph -> MarkedText(
                    text = remember(block, styles) { block.toAnnotated(styles) },
                    marks = remember(block, styles) { block.spans.mapNotNull { s -> styles.marks[s.kind]?.let { Triple(s.start, s.end, it) } } },
                    style = style,
                    color = color,
                )
                is Block.ListItem -> Row {
                    Text(
                        text = if (block.ordered) "${block.index}." else "•",
                        style = style,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.width(22.dp),
                    )
                    MarkedText(
                        text = remember(block, styles) { block.toAnnotated(styles) },
                        marks = remember(block, styles) { block.spans.mapNotNull { s -> styles.marks[s.kind]?.let { Triple(s.start, s.end, it) } } },
                        style = style,
                        color = color,
                    )
                }
                Block.Rule -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = ButlerTheme.colors.outlineFaint,
                )
            }
        }
    }
}

private fun Block.Paragraph.toAnnotated(styles: RpStyles): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end)
}

private fun Block.ListItem.toAnnotated(styles: RpStyles): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end)
}

private fun List<Block>.toCompactAnnotated(styles: RpStyles): AnnotatedString = buildAnnotatedString {
    var first = true
    for (block in this@toCompactAnnotated) {
        val (text, spans) = when (block) {
            is Block.Paragraph -> block.text to block.spans
            is Block.ListItem -> (if (block.ordered) "${block.index}. " else "• ") + block.text to
                block.spans.map { it.copy(start = it.start + (if (block.ordered) "${block.index}. ".length else 2), end = it.end + (if (block.ordered) "${block.index}. ".length else 2)) }
            Block.Rule -> continue
        }
        if (!first) append(' ')
        first = false
        val base = length
        append(text.replace('\n', ' '))
        for (s in spans) addStyle(styles.forKind(s.kind), base + s.start, base + s.end)
    }
}

/**
 * Text with highlighter strokes behind some of it: for each (start, end, colour), a rounded
 * band per line, as tall as the letters rather than the line, so tagged lines in a loosely set
 * paragraph stay separate strokes instead of merging into a block.
 */
@Composable
private fun MarkedText(
    text: AnnotatedString,
    marks: List<Triple<Int, Int, Color>>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (marks.isEmpty()) {
        Text(text = text, style = style, color = color, modifier = modifier)
        return
    }
    var layout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = text,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val font = style.fontSize.toPx()
            val padX = 2.dp.toPx()
            val radius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            for ((start, end, tint) in marks) {
                val from = start.coerceIn(0, l.layoutInput.text.length)
                val to = end.coerceIn(from, l.layoutInput.text.length)
                if (to <= from) continue
                val firstLine = l.getLineForOffset(from)
                val lastLine = l.getLineForOffset((to - 1).coerceAtLeast(from))
                for (line in firstLine..lastLine) {
                    val left = if (line == firstLine) l.getHorizontalPosition(from, true) else l.getLineLeft(line)
                    val right = if (line == lastLine) l.getHorizontalPosition(to, true) else l.getLineRight(line)
                    if (right - left < 1f) continue
                    val baseline = l.getLineBaseline(line)
                    drawRoundRect(
                        color = tint,
                        topLeft = androidx.compose.ui.geometry.Offset(minOf(left, right) - padX, baseline - font * 0.8f),
                        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(right - left) + padX * 2, font * 1.06f),
                        cornerRadius = radius,
                    )
                }
            }
        },
    )
}
