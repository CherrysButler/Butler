package com.cherry.butler.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.markdown.DescriptionLayout
import com.cherry.butler.core.markdown.DescriptionLayout.Doc
import com.cherry.butler.core.markdown.RichHtml
import com.cherry.butler.core.markdown.RpMarkdown
import com.cherry.butler.core.markdown.RpMarkdown.Span
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame

/**
 * A character description, laid out the way its creator meant it: section heads as
 * small red plates in a frame, one fact per ruled panel beneath, asides as italic
 * notes, prose where there is prose. Editor HTML takes the [RichText] path instead.
 */
@Composable
fun DescriptionText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    @Suppress("NAME_SHADOWING") val style = style.copy(fontFamily = ProseFamily)
    if (RichHtml.looksLikeHtml(text)) {
        RichText(text = text, modifier = modifier, style = style, color = color)
        return
    }
    val styles = rememberRpStyles(color)
    val keepQuotes = LocalRpLook.current.showQuotes
    val doc = remember(text, keepQuotes) { DescriptionLayout.layout(RpMarkdown.parse(text, keepQuotes)) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (item in doc) {
            when (item) {
                is Doc.Prose -> Text(
                    text = remember(item, styles) { annotate(item.text, item.spans, styles) },
                    style = style,
                    color = color,
                )
                is Doc.Label -> SectionLabel(item.text, modifier = Modifier.padding(top = 8.dp))
                is Doc.Panel -> Panel(
                    text = remember(item, styles) { annotate(item.text, item.spans, styles) },
                    note = item.note,
                    style = style,
                    color = color,
                )
                is Doc.ListItem -> Row {
                    Text(
                        text = if (item.ordered) "${item.index}." else "•",
                        style = style,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = remember(item, styles) { annotate(item.text, item.spans, styles) },
                        style = style,
                        color = color,
                    )
                }
                Doc.Rule -> HairlineRule(color = ButlerTheme.colors.rule, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/** A section head: the plate face, red, in its own hairline box. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer, Pill)) {
        PlateText(
            text = text,
            level = PlateLevel.Small,
            color = ButlerTheme.colors.onAccentSoft,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Panel(text: AnnotatedString, note: Boolean, style: TextStyle, color: Color) {
    val frame = if (note) ButlerTheme.colors.outlineFaint else ButlerTheme.colors.rule
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineFrame(frame)
            .background(if (note) Color.Transparent else ButlerTheme.colors.surfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = if (note) style.copy(fontStyle = FontStyle.Italic) else style,
            color = if (note) ButlerTheme.colors.textMed else color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun annotate(text: String, spans: List<Span>, styles: RpStyles): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end)
}
