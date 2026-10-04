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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cherry.butler.core.markdown.RichHtml
import com.cherry.butler.core.markdown.RichHtml.Align
import com.cherry.butler.core.markdown.RichHtml.Block
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame

/**
 * Renders a character's rich-text description: editor HTML becomes paragraphs, headings,
 * lists, rules, links and banner images; anything that is not HTML goes through
 * [RpText] as roleplay prose. Creator colours are drawn as written on the dark looks,
 * as Janitor does (text hidden by colour stays hidden); only on Daylight do colours that
 * would vanish fall back to the page's ink.
 */
@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    @Suppress("NAME_SHADOWING") val style = style.copy(fontFamily = ProseFamily)
    if (!RichHtml.looksLikeHtml(text)) {
        RpText(text = text, modifier = modifier, style = style, color = color)
        return
    }
    val blocks = remember(text) { RichHtml.parse(text) }
    val colors = ButlerTheme.colors
    val linkStyles = remember(colors) {
        TextLinkStyles(style = SpanStyle(color = colors.speech, textDecoration = TextDecoration.Underline))
    }
    val isDark = color.luminance() > 0.5f
    val personaColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is Block.Paragraph -> {
                    val annotated = remember(block, color, linkStyles, personaColor) { block.runs.toAnnotated(block.text, color, isDark, linkStyles, personaColor) }
                    when {
                        block.role == "appModalBadge" || block.heading >= 4 || (block.heading > 0 && block.text.length <= 48) ->
                            SectionLabel(block.text.trimEnd(':').trim(), modifier = Modifier.padding(top = 8.dp))
                        block.role == "pageTitle" || block.role == "detailName" -> Text(
                            text = annotated,
                            style = MaterialTheme.typography.titleLarge,
                            color = color,
                            textAlign = block.align.toTextAlign(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        block.role == "metaCaption" -> Text(
                            text = annotated,
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.textLow,
                            textAlign = block.align.toTextAlign(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        block.role == "button" -> KeyPanel(annotated, style)
                        block.role != null -> RolePanel(
                            text = annotated,
                            role = block.role,
                            style = style,
                            color = color,
                            align = block.align.toTextAlign(),
                        )
                        else -> Text(
                            text = annotated,
                            style = headingStyle(block.heading, style),
                            color = color,
                            textAlign = block.align.toTextAlign(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is Block.ListItem -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (block.ordered) "${block.index}." else "•",
                        style = style,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = remember(block, color, linkStyles, personaColor) { block.runs.toAnnotated(block.text, color, isDark, linkStyles, personaColor) },
                        style = style,
                        color = color,
                    )
                }
                is Block.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.width(2.dp).padding(end = 0.dp)) {
                        HairlineRule(color = ButlerTheme.colors.rule, modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = remember(block, color, linkStyles, personaColor) { block.runs.toAnnotated(block.text, color, isDark, linkStyles, personaColor) },
                        style = style,
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                is Block.Image -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = when (block.align) {
                        Align.Center -> Alignment.Center
                        Align.End -> Alignment.CenterEnd
                        Align.Start -> Alignment.CenterStart
                    },
                ) {
                    AsyncImage(
                        model = block.url,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth(block.widthFraction ?: 1f)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                Block.Rule -> HairlineRule(color = ButlerTheme.colors.rule, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/**
 * The official client's paragraph looks, in this world's material. A panel is a ruled
 * box; the fill, the frame colour and the italics are what tell the looks apart.
 */
@Composable
private fun RolePanel(text: AnnotatedString, role: String, style: TextStyle, color: Color, align: TextAlign) {
    val colors = ButlerTheme.colors
    val frame = when (role) {
        "card", "dropzone", "moderationNotes" -> colors.outlineFaint
        "characterInfoShinyBox", "bonus", "iosAlert", "confirm" -> MaterialTheme.colorScheme.primary
        else -> colors.rule
    }
    val filled = role != "card" && role != "dropzone"
    val ink = when (role) {
        "card", "moderationNotes" -> colors.textMed
        else -> color
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineFrame(frame)
            .background(if (filled) colors.surfaceElevated else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = style,
            color = ink,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A creator's "button": the inverted key, inert. */
@Composable
private fun KeyPanel(text: AnnotatedString, style: TextStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.background,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Align.toTextAlign(): TextAlign = when (this) {
    Align.Start -> TextAlign.Start
    Align.Center -> TextAlign.Center
    Align.End -> TextAlign.End
}

@Composable
private fun headingStyle(level: Int, body: TextStyle): TextStyle = when (level) {
    0 -> body
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

private fun List<RichHtml.Run>.toAnnotated(
    text: String,
    ink: Color,
    darkGround: Boolean,
    linkStyles: TextLinkStyles,
    personaColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    for (run in this@toAnnotated.sortedBy { if (it.style.persona) 1 else 0 }) {
        if (run.style.persona) {
            // The persona's name keeps whatever else its run says, but is always Butler red.
            addStyle(SpanStyle(color = personaColor), run.start, run.end)
            continue
        }
        val s = run.style
        val color = s.color?.let { Color(it) }?.takeIf { it.readableOn(darkGround) }
        addStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.SemiBold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                fontFamily = if (s.code) FontFamily.Monospace else null,
                color = color ?: Color.Unspecified,
                background = s.background?.let { Color(it).copy(alpha = 0.32f) } ?: Color.Unspecified,
                fontSize = s.sizeScale?.let { (14f * it).sp } ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                textDecoration = when {
                    s.underline && s.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    s.underline -> TextDecoration.Underline
                    s.strike -> TextDecoration.LineThrough
                    else -> null
                },
            ),
            run.start,
            run.end,
        )
        s.href?.let { addLink(LinkAnnotation.Url(it, linkStyles), run.start, run.end) }
    }
}

/**
 * Creator colours were picked on Janitor's dark page and are drawn as written there, even
 * when that makes them nearly invisible: creators hide search tags that way, and Janitor
 * keeps them hidden. Only on a light ground do colours that would vanish fall back to ink.
 */
private fun Color.readableOn(darkGround: Boolean): Boolean {
    if (darkGround) return true
    return luminance() in 0.05f..0.6f
}
