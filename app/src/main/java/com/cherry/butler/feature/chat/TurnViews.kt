package com.cherry.butler.feature.chat

import com.cherry.butler.core.design.ChatImage
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.RectangleShape
import com.cherry.butler.core.design.RpText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cherry.butler.core.design.narrationInk
import com.cherry.butler.core.design.LocalRpLook
import androidx.compose.ui.text.SpanStyle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.RpStyles
import com.cherry.butler.core.design.rememberRpStyles
import com.cherry.butler.core.markdown.RpMarkdown
import com.cherry.butler.core.markdown.RpMarkdown.Block
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.rememberDitherTiles
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Prose that is still arriving, revealed softly. The visible length glides toward what has
 * arrived (faster the further behind it is, so it never lags more than a moment), and the
 * newest characters fade up over a short trail instead of popping in. Only revealed text
 * is laid out, so the reply grows a line at a time. A block caret blinks at its end.
 */
@Composable
fun StreamingProse(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    /** The stream has ended; call [onRevealed] once the reveal has caught up. */
    finished: Boolean = false,
    onRevealed: () -> Unit = {},
) {
    val done by rememberUpdatedState(finished)
    val reportRevealed by rememberUpdatedState(onRevealed)
    val styles = rememberRpStyles(color)
    val keepQuotes = LocalRpLook.current.showQuotes
    val ink = narrationInk(color)
    val blocks = remember(text, keepQuotes) { RpMarkdown.parse(text, keepQuotes, images = true) }
    val total = remember(blocks) { blocks.sumOf { it.length() } }
    val target by rememberUpdatedState(total)
    // Starts at what is already there: a view rebuilt mid-stream (scrolled back into sight) does not replay.
    var revealed by remember { mutableFloatStateOf(total.toFloat()) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val goal = target.toFloat()
                if (revealed > goal) revealed = goal
                val behind = goal - revealed
                if (behind > 0f) revealed = (revealed + maxOf(REVEAL_MIN_CPS, behind * REVEAL_CATCH_UP) * dt).coerceAtMost(goal)
                if (done && revealed >= goal) reportRevealed()
            }
        }
    }

    var lastLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var caretAt by remember { mutableIntStateOf(0) }
    val blink = rememberCaretBlink()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val shown = revealed.toInt()
        if (blocks.isEmpty() || shown == 0) {
            Text(
                text = "",
                style = style,
                onTextLayout = { lastLayout = it; caretAt = 0 },
                modifier = Modifier.caret(ink, { lastLayout }, { caretAt }, blink),
            )
            return@Column
        }
        var start = 0
        for (block in blocks) {
            val len = block.length()
            if (start >= shown) break
            val cut = (shown - start).coerceAtMost(len)
            val partial = cut < len || start + len >= shown
            val base = remember(block, styles) { block.toAnnotated(styles) }
            val annotated = if (partial) base.revealedTo(cut, block, styles, ink) else base
            when (block) {
                is Block.Paragraph -> Text(
                    text = annotated,
                    style = style,
                    color = ink,
                    onTextLayout = { if (partial) { lastLayout = it; caretAt = cut } },
                    modifier = if (partial) Modifier.caret(ink, { lastLayout }, { caretAt }, blink) else Modifier,
                )
                is Block.ListItem -> Row {
                    Text(
                        text = if (block.ordered) "${block.index}." else "•",
                        style = style,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        text = annotated,
                        style = style,
                        color = ink,
                        onTextLayout = { if (partial) { lastLayout = it; caretAt = cut } },
                        modifier = if (partial) Modifier.caret(ink, { lastLayout }, { caretAt }, blink) else Modifier,
                    )
                }
                Block.Rule -> HairlineRule(color = ButlerTheme.colors.outlineFaint)
                is Block.Image -> ChatImage(url = block.url, alt = block.alt)
            }
            start += len
        }
    }
}

private const val REVEAL_MIN_CPS = 45f
private const val REVEAL_CATCH_UP = 5f
private const val REVEAL_TRAIL = 18

private fun Block.length(): Int = when (this) {
    is Block.Paragraph -> text.length
    is Block.ListItem -> text.length
    Block.Rule -> 1
    is Block.Image -> 1
}

/** [this] cut at [cut], with the last few shown characters fading up. */
private fun AnnotatedString.revealedTo(cut: Int, block: Block, styles: RpStyles, ink: Color): AnnotatedString {
    if (block is Block.Rule || block is Block.Image) return this
    val spans = when (block) {
        is Block.Paragraph -> block.spans
        is Block.ListItem -> block.spans
        else -> emptyList()
    }
    fun colorAt(i: Int): Color {
        val kind = spans.lastOrNull { i >= it.start && i < it.end }?.kind ?: return ink
        val c = styles.forKind(kind).color
        return if (c == Color.Unspecified) ink else c
    }
    val trailFrom = (cut - REVEAL_TRAIL).coerceAtLeast(0)
    return buildAnnotatedString {
        append(this@revealedTo)
        for (i in trailFrom until cut) {
            val a = (cut - i).toFloat() / (cut - trailFrom + 1)
            addStyle(SpanStyle(color = colorAt(i).copy(alpha = (1f - a).coerceIn(0.08f, 1f) * colorAt(i).alpha)), i, i + 1)
        }
    }.subSequence(0, cut)
}

/** A hard on/off blink, the way a game's text cursor blinks: no fade. */
@Composable
private fun rememberCaretBlink(): () -> Boolean {
    val transition = rememberInfiniteTransition(label = "caret")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "caret-phase",
    )
    return { phase < 0.55f }
}

private fun Modifier.caret(color: Color, layout: () -> TextLayoutResult?, at: () -> Int, visible: () -> Boolean): Modifier =
    drawWithContent {
        drawContent()
        if (!visible()) return@drawWithContent
        val l = layout() ?: return@drawWithContent
        val offset = at().coerceIn(0, l.layoutInput.text.length)
        val line = l.getLineForOffset(offset)
        val top = l.getLineTop(line)
        val bottom = l.getLineBottom(line)
        val x = if (l.layoutInput.text.isEmpty()) 0f else l.getHorizontalPosition(offset, usePrimaryDirection = true)
        val h = (bottom - top) * 0.72f
        drawRect(
            color = color,
            topLeft = Offset(x + 3.dp.toPx(), top + (bottom - top - h) / 2f),
            size = Size(7.dp.toPx(), h),
        )
    }

private fun Block.toAnnotated(styles: RpStyles): AnnotatedString = when (this) {
    is Block.Paragraph -> buildAnnotatedString { append(text); for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end) }
    is Block.ListItem -> buildAnnotatedString { append(text); for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end) }
    Block.Rule, is Block.Image -> AnnotatedString("")
}

/**
 * The model's reasoning, folded to one line.
 *
 * While the thought is being written the line's ground is a dither field that drifts:
 * dots light and die in a slow wave along the row, and the field thickens as the thought
 * grows, so the work is visible without a word of it moving. The plate breathes red on
 * top. When the reply begins the field snaps to black in one frame and the line folds to
 * its first words. A tap opens the whole thought in a sheet.
 */
@Composable
fun ThinkingLine(text: String, streaming: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, word: String = "Thinking") {
    val red = MaterialTheme.colorScheme.primary
    val tiles = rememberDitherTiles(ButlerTheme.colors.rule, dotPx = 3)
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "thinking-drift",
    )
    val breath by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinking-breath",
    )
    // Density rises with the thought: a few dots at the first token, a dense field by ~1500 chars.
    val fill = 0.15f + 0.55f * (text.length / 1_500f).coerceIn(0f, 1f)

    // The word and the field are laid out side by side with a fixed gap between them, so the
    // field never reaches the word, whatever its length. (It used to be placed by measuring,
    // which missed the row's padding and let the field run under the word's last dot.)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .hairlineFrame(if (streaming) ButlerTheme.colors.rule else ButlerTheme.colors.outlineFaint)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlateText(
            text = if (streaming) "$word…" else "Thought",
            level = PlateLevel.Small,
            color = if (streaming) red else ButlerTheme.colors.textLow,
            modifier = if (streaming) Modifier.graphicsLayer { alpha = breath } else Modifier,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    if (!streaming) return@drawBehind
                    // One band per dither tile (4 dots of 3 px), so density changes in fine
                    // steps aligned to the dot grid instead of in wide blocks.
                    val bandW = 12f
                    val bands = (size.width / bandW).toInt().coerceAtLeast(1)
                    for (i in 0 until bands) {
                        val x = (i + 0.5f) / bands
                        // Two waves travelling the row at different speeds read as drift, not a scan.
                        val wave = 0.5f + 0.35f * sin((x - phase) * TWO_PI) + 0.15f * sin((x * 2.3f + phase * 1.7f) * TWO_PI)
                        // The field rises out of the word: already present right after it, full a little in.
                        val ramp = 0.4f + 0.6f * (x / 0.15f).coerceIn(0f, 1f)
                        var level = ((fill * wave * ramp) * (tiles.levels + 1)).roundToInt() - 1
                        // Right after the word there is always at least a trace, so the field
                        // reads as coming out of it whatever the wave is doing.
                        if (x < 0.12f) level = level.coerceAtLeast(0)
                        if (level < 0) continue
                        val w = if (i == bands - 1) size.width - i * bandW else bandW
                        drawRect(tiles.brush(level), topLeft = Offset(i * bandW, 0f), size = Size(w, size.height))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!streaming) {
                Text(
                    text = text.lines().joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.textLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandLess,
            contentDescription = "Read the thought",
            tint = ButlerTheme.colors.textLow,
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/** A blinking block on its own, for places that are not a text layout. */
@Composable
private fun Caret(color: Color) {
    val blink = rememberCaretBlink()
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(width = 7.dp, height = 12.dp)
            .drawWithContent { if (blink()) drawRect(color) },
    )
}

/**
 * The whole thought, slid up from the bottom. Live while it is still being written,
 * following its own end; still once it is done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingSheet(text: String, streaming: Boolean, onDismiss: () -> Unit, word: String = "Thinking") {
    val scroll = rememberScrollState()
    LaunchedEffect(text.length) { if (streaming) scroll.animateScrollTo(scroll.maxValue) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opened all the way, so its last lines are never below the screen's edge.
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlateText(
                    text = if (streaming) "$word…" else "Thought",
                    level = PlateLevel.Name,
                    color = if (streaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                HairlineRule(modifier = Modifier.weight(1f), color = ButlerTheme.colors.rule)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            ) {
                if (streaming) {
                    StreamingProse(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = ButlerTheme.colors.textMed,
                    )
                } else {
                    RpText(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = ButlerTheme.colors.textMed,
                    )
                }
            }
        }
    }
}

/** A one-line state under the user's own turn: queued, sending, or counting down to a retry. */
@Composable
fun TurnState(text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
        actions()
    }
}

typealias RowScope = androidx.compose.foundation.layout.RowScope

/** A small inline action beside a state line. */
@Composable
fun RowScope.StateAction(label: String, onClick: () -> Unit, emphasis: Boolean = false) {
    TextButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The errata slip: a terminal failure inserted into the log where it happened, naming
 * the cause and the way out. The only red in the transcript apart from the plate.
 */
@Composable
fun ErrataSlip(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.danger.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
    ) {
        PlateText(text = title, level = PlateLevel.Small, color = ButlerTheme.colors.danger)
        Spacer(Modifier.height(4.dp))
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** What a failure summary means for the user, and what fixes it. */
fun guidanceFor(summary: String?): String = when {
    summary == null -> "Something went wrong. Your message is saved."
    summary.startsWith("Signed out") -> "Sign in again from Settings, then retry. Your message is saved."
    summary.startsWith("No connection") -> "Check your connection. Your message is saved and will send when you retry."
    summary.startsWith("Rate limited") -> "Janitor asked for a pause. Retry in a moment; your message is saved."
    summary.startsWith("Not allowed") -> "Janitor refused this request for your account."
    summary.contains("BALANCE", ignoreCase = true) || summary.contains("credit", ignoreCase = true) ->
        "Your proxy or router has no balance left. Top it up, then retry."
    summary.startsWith("Stopped") -> "You stopped the reply. Continue it or send something new."
    else -> "Retry when ready. Your message is saved."
}
