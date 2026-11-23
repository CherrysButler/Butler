package com.cherry.butler.feature.chat

import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
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
import com.cherry.butler.core.design.LocalThinkingLook
import com.cherry.butler.core.design.LocalThoughtLook
import com.cherry.butler.core.design.ThinkingLook
import com.cherry.butler.core.design.ThoughtLook
import kotlin.math.ceil
import kotlin.math.min
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.RpStyles
import com.cherry.butler.core.design.rememberRpStyles
import com.cherry.butler.core.markdown.RpMarkdown
import com.cherry.butler.core.markdown.RpMarkdown.Block
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.rememberDitherTiles
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Prose that is still arriving, revealed the way a person reads it: a few words at a time, on a
 * steady beat, each handful fading up over most of a second, so several are always mid-fade and
 * the text flows instead of ticking. Text is cut only at the end of a word (and kept with a comma
 * or a full stop close after it); a little is gathered before the first words show and a little
 * is held back while the model is still writing, so a pause in the stream never stops the flow.
 * The further behind, the bigger each handful, so it never lags more than a moment. Only shown
 * text is laid out, so the reply grows a line at a time. No caret: a blink at the end fought the
 * fade, so the soft edge of the newest words is where the writing is.
 */
@Composable
fun StreamingProse(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    /** The stream has ended; call [onRevealed] once the reveal has caught up and settled. */
    finished: Boolean = false,
    onRevealed: () -> Unit = {},
) {
    val done by rememberUpdatedState(finished)
    val reportRevealed by rememberUpdatedState(onRevealed)
    val styles = rememberRpStyles(color)
    val keepQuotes = LocalRpLook.current.showQuotes
    val ink = narrationInk(color)
    val blocks = remember(text, keepQuotes) { RpMarkdown.parse(text, keepQuotes, images = true) }
    // The blocks' characters end to end, and where each block starts (a block's edge is a word's edge).
    val flat = remember(blocks) { Flat(blocks) }
    val latest by rememberUpdatedState(flat)
    // Starts at what is already there: a view rebuilt mid-stream (scrolled back into sight) does not replay.
    var revealed by remember { mutableIntStateOf(flat.length) }
    /** Each handful shown: its range and when it came, while it is still fading up. */
    val births = remember { androidx.compose.runtime.mutableStateListOf<Birth>() }
    var clock by remember { androidx.compose.runtime.mutableLongStateOf(nowMs()) }

    // The beat: decides when the next handful shows and how big it is.
    LaunchedEffect(Unit) {
        var started = revealed > 0
        var waitingSince = 0L
        var lastShown = 0L
        var reported = false
        while (true) {
            val f = latest
            if (f.length < revealed) {
                // A new reply in the same place (sent again): start over.
                revealed = 0
                births.clear()
                started = false
            }
            val backlog = f.length - revealed
            if (backlog <= 0) {
                waitingSince = 0L
                if (!done) {
                    reported = false
                    androidx.compose.runtime.snapshotFlow { latest.length > revealed || done }.first { it }
                    continue
                }
                if (!reported) {
                    // Let the last words finish fading before the finished text takes over.
                    val settle = (births.maxOfOrNull { it.at } ?: 0L) + REVEAL_FADE_MS + 100 - nowMs()
                    if (settle > 0) kotlinx.coroutines.delay(settle)
                    if (latest.length == revealed) { reported = true; reportRevealed() }
                    continue
                }
                androidx.compose.runtime.snapshotFlow { latest.length > revealed || !done }.first { it }
                continue
            }
            val now = nowMs()
            if (waitingSince == 0L) waitingSince = now
            val live = !done
            if (live && !started && backlog < REVEAL_HEAD_START && now - waitingSince < REVEAL_HEAD_WAIT_MS) {
                kotlinx.coroutines.delay(REVEAL_BEAT_MS / 3)
                continue
            }
            if (live && started && backlog - REVEAL_RESERVE < REVEAL_MIN_CHARS && now - lastShown < REVEAL_STALL_MS) {
                kotlinx.coroutines.delay(REVEAL_BEAT_MS / 3)
                continue
            }
            val size = maxOf(REVEAL_MIN_CHARS, ((if (live) backlog - REVEAL_RESERVE else backlog) + 3) / 4)
            val cut = if (live) f.wordEnd(revealed + size) else (f.wordEnd(minOf(f.length, revealed + size * 2)) ?: f.length)
            if (cut == null || cut <= revealed) {
                kotlinx.coroutines.delay(REVEAL_BEAT_MS / 3)
                continue
            }
            births.add(Birth(revealed, cut, now))
            revealed = cut
            started = true
            lastShown = now
            kotlinx.coroutines.delay(if (live) REVEAL_BEAT_MS else REVEAL_FLUSH_BEAT_MS)
        }
    }
    // The fade: frames only while something is still fading up; settled handfuls are dropped.
    LaunchedEffect(Unit) {
        while (true) {
            val now = nowMs()
            births.removeAll { now - it.at >= REVEAL_FADE_MS }
            if (births.isEmpty()) {
                androidx.compose.runtime.snapshotFlow { births.size }.first { it > 0 }
                continue
            }
            withFrameNanos { clock = nowMs() }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val shown = revealed.coerceAtMost(flat.length)
        if (blocks.isEmpty() || shown == 0) {
            Text(text = "", style = style)
            return@Column
        }
        val fadingFrom = births.minOfOrNull { it.from } ?: shown
        var start = 0
        for (block in blocks) {
            val len = block.length()
            if (start >= shown) break
            val cut = (shown - start).coerceAtMost(len)
            val fading = start + len > fadingFrom
            val base = remember(block, styles) { block.toAnnotated(styles) }
            val annotated = if (fading || cut < len) base.revealedTo(cut, start, block, styles, ink, births, clock) else base
            when (block) {
                is Block.Paragraph -> Text(text = annotated, style = style, color = ink)
                is Block.ListItem -> Row {
                    Text(
                        text = if (block.ordered) "${block.index}." else "•",
                        style = style,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(text = annotated, style = style, color = ink)
                }
                Block.Rule -> HairlineRule(color = ButlerTheme.colors.outlineFaint)
                is Block.Image -> ChatImage(url = block.url, alt = block.alt)
            }
            start += len
        }
    }
}

/** A handful of revealed text: characters [from] until [to], shown at [at] (ms). */
private class Birth(val from: Int, val to: Int, val at: Long)

private fun nowMs(): Long = System.nanoTime() / 1_000_000

/** The reply's blocks as one run of characters, for finding where words end. */
private class Flat(blocks: List<Block>) {
    private val text: String = buildString {
        for (b in blocks) when (b) {
            is Block.Paragraph -> append(b.text)
            is Block.ListItem -> append(b.text)
            else -> append('\n')
        }
    }
    private val edges: Set<Int> = buildSet {
        var at = 0
        for (b in blocks) { add(at); at += b.length() }
        add(at)
    }
    val length: Int get() = text.length

    private fun gap(i: Int) = i in edges || (i < text.length && text[i].isWhitespace())

    /**
     * The end of the word running at [from], carried past a comma or full stop that closes the
     * phrase within a few characters. Null when the text runs out mid-word (more is coming).
     */
    fun wordEnd(from: Int): Int? {
        if (from >= text.length) return null
        var i = from
        while (i < text.length && !gap(i)) i++
        if (i >= text.length && i !in edges) return null
        val limit = minOf(text.length, i + REVEAL_PHRASE_REACH)
        var j = i
        while (j < limit) {
            if (j > i && j in edges) break
            if (text[j] in PHRASE_END && (j + 1 >= text.length || gap(j + 1) || text[j + 1] in CLOSERS)) {
                var k = j + 1
                while (k < text.length && text[k] in CLOSERS) k++
                return if (k >= text.length && k !in edges) i else k
            }
            j++
        }
        return i
    }
}

private const val PHRASE_END = ".,;:!?—…"
private const val CLOSERS = "\"”’)*"

/** How often a handful shows while the model writes, and once it has finished. */
private const val REVEAL_BEAT_MS = 180L
private const val REVEAL_FLUSH_BEAT_MS = 80L
/** The smallest handful (about four or five words). */
private const val REVEAL_MIN_CHARS = 24
/** Gathered before the first words show, unless the wait runs past [REVEAL_HEAD_WAIT_MS]. */
private const val REVEAL_HEAD_START = 120
private const val REVEAL_HEAD_WAIT_MS = 800L
/** Held back while the model writes, so a pause in the stream does not stop the flow... */
private const val REVEAL_RESERVE = 36
/** ...unless keeping it back would stall the text this long. */
private const val REVEAL_STALL_MS = 450L
/** How far past a word's end a comma or full stop still pulls the cut along. */
private const val REVEAL_PHRASE_REACH = 18
/** How long a handful takes to fade fully up. */
private const val REVEAL_FADE_MS = 900L
private val REVEAL_EASE = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

private fun Block.length(): Int = when (this) {
    is Block.Paragraph -> text.length
    is Block.ListItem -> text.length
    Block.Rule -> 1
    is Block.Image -> 1
}

/** [this] cut at [cut], with each handful still fading up drawn at its fade. [offset] is the block's start in the reply. */
private fun AnnotatedString.revealedTo(
    cut: Int,
    offset: Int,
    block: Block,
    styles: RpStyles,
    ink: Color,
    births: List<Birth>,
    now: Long,
): AnnotatedString {
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
    // Where the colour can change inside a handful: span edges.
    val edges = spans.flatMap { listOf(it.start, it.end) }.toSortedSet()
    return buildAnnotatedString {
        append(this@revealedTo)
        for (b in births) {
            val a = (b.from - offset).coerceAtLeast(0)
            val z = (b.to - offset).coerceAtMost(cut)
            if (a >= z) continue
            val t = ((now - b.at).toFloat() / REVEAL_FADE_MS).coerceIn(0f, 1f)
            val alpha = REVEAL_EASE.transform(t)
            if (alpha >= 1f) continue
            var s = a
            while (s < z) {
                val e = (edges.tailSet(s + 1).firstOrNull() ?: z).coerceAtMost(z)
                val c = colorAt(s)
                addStyle(SpanStyle(color = c.copy(alpha = alpha * c.alpha)), s, e)
                s = e
            }
        }
    }.subSequence(0, cut)
}

/** A hard on/off blink, the way a game's text cursor blinks: no fade. */
@Composable
private fun rememberCaretBlink(): () -> Boolean {
    // Two changes a second, so two redraws a second: a continuous animation here asked for
    // every display frame (60 to 180 a second) to show the same two states.
    val on = remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            on.value = true
            kotlinx.coroutines.delay(550)
            on.value = false
            kotlinx.coroutines.delay(450)
        }
    }
    return { on.value }
}


private fun Block.toAnnotated(styles: RpStyles): AnnotatedString = when (this) {
    is Block.Paragraph -> buildAnnotatedString { append(text); for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end) }
    is Block.ListItem -> buildAnnotatedString { append(text); for (s in spans) addStyle(styles.forKind(s.kind), s.start, s.end) }
    Block.Rule, is Block.Image -> AnnotatedString("")
}

/**
 * The model's reasoning, folded to one line.
 *
 * While the thought is being written the line shows the reader's chosen animation
 * (ThinkingLook): a little butler walking the row with the reply on his tray, bands of dither
 * drifting along it, or a field of dots like Browse's; the plate breathes red beside
 * it. Once the reply begins, the line takes the reader's chosen shape (ThoughtLook): the
 * stripe with the thought's first words, a small square where the animation goes on with an
 * arrow beside it, or a button saying how long the thought took. A tap opens the whole thought in a sheet.
 */
@Composable
fun ThinkingLine(
    text: String,
    streaming: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    word: String = "Thinking",
    /** How long the thought took, when it was recorded. */
    durationMs: Long? = null,
) {
    if (!streaming) {
        when (LocalThoughtLook.current) {
            ThoughtLook.Button -> { ThoughtButton(durationMs, onOpen, modifier); return }
            ThoughtLook.Box -> { ThoughtBox(onOpen, modifier); return }
            ThoughtLook.Stripe -> Unit
        }
    }
    val red = MaterialTheme.colorScheme.primary
    val breath by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinking-breath",
    )
    // The word and the field are laid out side by side with a fixed gap between them, so the
    // field never reaches the word, whatever its length.
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
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (streaming) {
                ThinkingField(length = text.length, look = LocalThinkingLook.current, modifier = Modifier.matchParentSize())
            } else {
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

/** "Thought for 12s": how long the model took, or just "Thought" when nothing recorded it. */
fun thoughtFor(ms: Long?): String = when {
    ms == null -> "Thought"
    ms < 1_000 -> "Thought for a moment"
    ms < 60_000 -> "Thought for ${ms / 1_000}s"
    else -> "Thought for ${ms / 60_000}m ${ms / 1_000 % 60}s"
}

/** A finished thought as a small key: how long it took, and a tap to read it. */
@Composable
private fun ThoughtButton(durationMs: Long?, onOpen: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .hairlineFrame(ButlerTheme.colors.outlineFaint)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = thoughtFor(durationMs), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
        Spacer(Modifier.width(2.dp))
        Icon(imageVector = Icons.Rounded.ExpandLess, contentDescription = "Read the thought", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(16.dp))
    }
}

/**
 * A finished thought as two squares joined in one frame: the thinking animation, then the
 * arrow. A tap on either reads it.
 */
@Composable
private fun ThoughtBox(onOpen: () -> Unit, modifier: Modifier) {
    val outline = ButlerTheme.colors.outlineFaint
    Row(
        modifier = modifier
            .height(ThoughtSquare)
            .hairlineFrame(outline)
            .clickable(onClickLabel = "Read the thought", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ThoughtSquare)) {
            ThinkingField(length = 600, look = LocalThinkingLook.current, modifier = Modifier.matchParentSize(), still = true)
        }
        Box(Modifier.width(hairline()).fillMaxHeight().background(outline))
        Box(Modifier.size(ThoughtSquare), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Rounded.ExpandLess, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(18.dp))
        }
    }
}

/** Each of the after-thought's two squares. */
private val ThoughtSquare = 40.dp

/** The animation of a thought being written, filling its box. */
@Composable
private fun ThinkingField(length: Int, look: ThinkingLook, modifier: Modifier, still: Boolean = false) {
    when (look) {
        // The thought is done: in its box the butler stands, he doesn't march.
        ThinkingLook.Butler -> ThinkingButler(modifier, still = still)
        ThinkingLook.Dither -> DitherBands(length, modifier)
        ThinkingLook.Field -> DotField(length, modifier)
    }
}

/**
 * The line's first animation: one band of dither tiles per 12 px along the row, two waves
 * travelling it at different speeds so it reads as drift, not a scan. The field rises out of
 * the word and thickens with the thought: a few dots at the first token, a dense field by
 * ~1500 characters.
 */
@Composable
private fun DitherBands(length: Int, modifier: Modifier) {
    val tiles = rememberDitherTiles(ButlerTheme.colors.rule, dotPx = 3)
    // Drifts only while on screen.
    val shown = rememberOnScreen()
    val clock = rememberThinkingClock(running = shown.value)
    val fill = 0.15f + 0.55f * (length / 1_500f).coerceIn(0f, 1f)
    Box(
        modifier.onScreen(shown).drawBehind {
            val phase = (clock.value / 1_000_000L % 2_600L) / 2_600f
            val bandW = 12f
            val bands = (size.width / bandW).toInt().coerceAtLeast(1)
            for (i in 0 until bands) {
                val x = (i + 0.5f) / bands
                val wave = 0.5f + 0.35f * sin((x - phase) * TWO_PI) + 0.15f * sin((x * 2.3f + phase * 1.7f) * TWO_PI)
                val ramp = 0.4f + 0.6f * (x / 0.15f).coerceIn(0f, 1f)
                var level = ((fill * wave * ramp) * (tiles.levels + 1)).roundToInt() - 1
                // Right after the word there is always at least a trace, so the field reads
                // as coming out of it whatever the wave is doing.
                if (x < 0.12f) level = level.coerceAtLeast(0)
                if (level < 0) continue
                val w = if (i == bands - 1) size.width - i * bandW else bandW
                drawRect(tiles.brush(level), topLeft = Offset(i * bandW, 0f), size = Size(w, size.height))
            }
        },
    )
}

/**
 * The dot field, as the desktop's Browse has it: two waves crossing a grid of dither
 * tiles (four dots of 2 dp), so density changes in fine steps aligned to the dot grid. It
 * rises out of the word and thickens with the thought: a few dots at the first token, a
 * dense field by ~1500 characters.
 */
@Composable
private fun DotField(length: Int, modifier: Modifier) {
    // Browse's dots: 2 dp each, whatever the screen's density, so the field reads as full.
    val dot = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.roundToPx() }.coerceAtLeast(2)
    val tiles = rememberDitherTiles(ButlerTheme.colors.rule, dotPx = dot)
    // Drifts only while on screen.
    val shown = rememberOnScreen()
    val clock = rememberThinkingClock(running = shown.value)
    // Denser than the bands from the start, as Browse's field is; still thickening as the thought grows.
    val fill = 0.6f + 0.3f * (length / 1_500f).coerceIn(0f, 1f)
    Box(
        modifier.onScreen(shown).drawBehind {
            val phase = (clock.value / 1_000_000L % 9_000L) / 9_000f
            val tile = 4f * dot
            val cols = ceil(size.width / tile).toInt().coerceAtLeast(1)
            val rows = ceil(size.height / tile).toInt().coerceAtLeast(1)
            for (j in 0 until rows) for (i in 0 until cols) {
                val x = (i + 0.5f) / cols
                val y = (j + 0.5f) / rows
                val wave = 0.55f + 0.3f * sin((x * 1.3f + y * 0.8f - phase) * TWO_PI) + 0.15f * sin((x * 2.7f - y * 1.9f + phase * 2f) * TWO_PI)
                val ramp = 0.4f + 0.6f * (x / 0.15f).coerceIn(0f, 1f)
                var level = ((fill * wave * ramp) * (tiles.levels + 1)).roundToInt() - 1
                // Right after the word there is always at least a trace, so the field reads
                // as coming out of it whatever the waves are doing.
                if (x < 0.12f) level = level.coerceAtLeast(0)
                if (level < 0) continue
                drawRect(
                    tiles.brush(level),
                    topLeft = Offset(i * tile, j * tile),
                    size = Size(min(tile, size.width - i * tile), min(tile, size.height - j * tile)),
                )
            }
        },
    )
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
    com.cherry.butler.ui.components.ButlerSheet(onDismiss = onDismiss) {
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
                val steps = remember(text) { com.cherry.butler.core.generation.AgentSteps.parse(text) }
                when {
                    steps != null -> AgentStepsView(steps, streaming)
                    streaming -> StreamingProse(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = ButlerTheme.colors.textMed,
                    )
                    else -> RpText(
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
        modifier = Modifier.heightIn(min = 28.dp),
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

/**
 * Agent mode's thought as events: one row per step with its mark, name and state (a
 * breathing dot while it runs, a check once done), and what happened inside it under a tap.
 * The running step and the last one start open; an empty reasoning step is left out.
 */
@Composable
private fun AgentStepsView(steps: List<com.cherry.butler.core.generation.AgentSteps.Step>, streaming: Boolean) {
    val shown = steps.filterIndexed { i, s -> s.detail.isNotBlank() || (!s.done && i == steps.lastIndex) }
    var opened by remember { mutableStateOf(setOf<Int>()) }
    var touched by remember { mutableStateOf(false) }
    val lastIndex = shown.lastIndex
    val red = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "agent-step")
    val breath by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "agent-breath",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEachIndexed { i, step ->
            val running = !step.done && streaming
            val open = if (touched) i in opened else (running || i == lastIndex)
            val icon = when (step.kind) {
                com.cherry.butler.core.generation.AgentSteps.Kind.Reasoning -> androidx.compose.material.icons.Icons.Rounded.Psychology
                com.cherry.butler.core.generation.AgentSteps.Kind.Draft -> androidx.compose.material.icons.Icons.Rounded.Edit
                com.cherry.butler.core.generation.AgentSteps.Kind.Check -> androidx.compose.material.icons.Icons.Rounded.FactCheck
                com.cherry.butler.core.generation.AgentSteps.Kind.Fix -> androidx.compose.material.icons.Icons.Rounded.Build
                com.cherry.butler.core.generation.AgentSteps.Kind.Rewrite -> androidx.compose.material.icons.Icons.Rounded.AutoFixHigh
                com.cherry.butler.core.generation.AgentSteps.Kind.Finish -> androidx.compose.material.icons.Icons.Rounded.CheckCircle
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineFrame(if (running) ButlerTheme.colors.rule else ButlerTheme.colors.outlineFaint)
                    .clickable {
                        touched = true
                        opened = if (open) opened - i else opened + i
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        icon,
                        contentDescription = null,
                        tint = if (running) red else ButlerTheme.colors.textMed,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    PlateText(
                        text = (if (running) step.kind.doing else step.kind.title) + (step.label?.let { " $it" } ?: ""),
                        level = PlateLevel.Small,
                        color = if (running) red else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (running) {
                        Box(Modifier.size(8.dp).graphicsLayer { alpha = breath }.background(red, androidx.compose.foundation.shape.CircleShape))
                    } else if (step.done) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Rounded.Check,
                            contentDescription = "Done",
                            tint = ButlerTheme.colors.textLow,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (open && step.detail.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    if (running) {
                        StreamingProse(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = ButlerTheme.colors.textMed,
                        )
                    } else {
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = if (step.kind == com.cherry.butler.core.generation.AgentSteps.Kind.Draft || step.kind == com.cherry.butler.core.generation.AgentSteps.Kind.Rewrite) FontStyle.Normal else FontStyle.Italic),
                            color = ButlerTheme.colors.textMed,
                        )
                    }
                }
            }
        }
    }
}
