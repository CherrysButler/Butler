package com.cherry.butler.feature.chat

import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.AutoAwesome
import com.cherry.butler.core.generation.SuggestionService
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.cherry.butler.core.design.Motion
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.ui.components.Avatar
import androidx.compose.foundation.layout.Spacer
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.card
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * The text window. A ruled field on the ground, and one red advance key at the right;
 * while a reply is being written the key becomes Stop. Beside it, the write key: empty
 * field, "write for me"; words in it, "enhance my draft". The line being written streams
 * into the field, and a written line can be undone or written again until it is edited.
 * The draft is already in saved state before it is ever sent.
 */
@Composable
fun Composer(
    draft: String,
    onDraftChanged: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    persona: PersonaOption? = null,
    onPickPersona: (() -> Unit)? = null,
    suggestion: SuggestionService.State? = null,
    onWrite: (() -> Unit)? = null,
    onStopWriting: () -> Unit = {},
    onUndoWrite: () -> Unit = {},
    onWriteAgain: () -> Unit = {},
    /** Rich typing on; [richDefault] is what typing opens on its own (null: plain). */
    rich: Boolean = false,
    richDefault: Mark? = Mark.Action,
) {
    val writing = suggestion as? SuggestionService.State.Writing
    // The field keeps its own caret; the draft (saved state) is its text. A draft changed from
    // outside (sent, written for you, undone) resets the caret to the end.
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    var typing by remember(richDefault) { mutableStateOf(TypingState(richDefault)) }
    /** The text the keyboard last reported, before the marks touched it. */
    val imeText = remember { KeyboardCopy() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(draft) {
        if (field.text != draft) {
            field = TextFieldValue(draft, TextRange(draft.length))
            if (draft.isEmpty()) typing = TypingState(richDefault)
        }
    }
    val marks = RichMarks(
        mark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        speech = com.cherry.butler.core.design.LocalRpLook.current.speech?.let { androidx.compose.ui.graphics.Color(it) } ?: ButlerTheme.colors.speech,
        action = com.cherry.butler.core.design.LocalRpLook.current.action?.let { androidx.compose.ui.graphics.Color(it) } ?: ButlerTheme.colors.textMed,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        (suggestion as? SuggestionService.State.Ready)?.let { written ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (written.original.isBlank()) "Written for you" else "Draft rewritten",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.weight(1f),
                )
                StateAction(label = "Undo", onClick = onUndoWrite)
                StateAction(label = "Again", onClick = onWriteAgain, emphasis = true)
            }
        }
        // One field across the whole width, as Janitor's message box: the persona's face at
        // its start, the words, the send key at its end. The keys sit inside the field, so the
        // writing room is the screen's width and not what two keys leave of it.
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .card(shape = MaterialTheme.shapes.medium, color = ButlerTheme.colors.surfaceHigh)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onPickPersona != null) {
                PersonaChip(persona = persona, onClick = onPickPersona)
                Spacer(Modifier.width(10.dp))
            }
            Box(modifier = Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 7.dp), contentAlignment = Alignment.CenterStart) {
                if (writing != null) {
                    // Read-only while it arrives, six lines tall at most, and held at its newest
                    // words: clipped at the top, a growing line looked as if it had stopped.
                    val style = MaterialTheme.typography.bodyLarge
                    val scroll = rememberScrollState()
                    LaunchedEffect(writing.text) { scroll.scrollTo(scroll.maxValue) }
                    Text(
                        text = writing.text.ifEmpty { if (writing.original.isBlank()) "Writing…" else "Rewriting…" },
                        style = style,
                        color = if (writing.text.isEmpty()) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = with(LocalDensity.current) { (style.lineHeight * 6).toDp() })
                            .verticalScroll(scroll),
                    )
                } else Column {
                    // The three marks, over the words while the box has the keyboard.
                    if (rich && focused) {
                        MarkKeys(
                            lit = RichTyping.lit(field, typing),
                            inSpeech = RichTyping.inSpeech(field, typing),
                            innerQuote = typing.inner,
                            onPress = { mark ->
                                val (v, st) = RichTyping.press(field, mark, typing)
                                field = v
                                typing = st
                                if (v.text != draft) onDraftChanged(v.text)
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { new ->
                            if (rich) {
                                // When the marks change what was typed, the keyboard still holds its own
                                // copy until the field redraws, and can send that copy again in the same
                                // frame (Gboard does on a backspace). Its text is then what it sent last
                                // time: not an edit, so the marks' version stands.
                                val stale = new.text == imeText.value && new.text != field.text
                                imeText.value = new.text
                                if (!stale) {
                                    val (v, st) = RichTyping.onChange(field, new, typing, richDefault)
                                    field = v
                                    typing = st
                                }
                            } else {
                                field = new
                            }
                            if (field.text != draft) onDraftChanged(field.text)
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        visualTransformation = if (rich) marks else androidx.compose.ui.text.input.VisualTransformation.None,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            if (field.text.isEmpty()) {
                                Text(
                                    text = "Write…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ButlerTheme.colors.textLow,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
            if (onWrite != null && (!busy || writing != null)) {
                Spacer(Modifier.width(4.dp))
                WriteKey(writing = writing != null, rewrite = draft.isNotBlank(), onWrite = onWrite, onStop = onStopWriting)
            }
            Spacer(Modifier.width(8.dp))
            AdvanceKey(busy = busy, enabled = draft.isNotBlank() && writing == null, onSend = onSend, onStop = onStop)
        }
    }
}

/**
 * Rich typing's keys: the bare marks, " * B. The lit one is what the next words become. Small blocks along the top of the box, so they never
 * cover what is being written.
 */
@Composable
private fun MarkKeys(lit: Mark?, inSpeech: Boolean, innerQuote: Boolean, onPress: (Mark) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        listOf(Mark.Speech, Mark.Action, Mark.Bold).forEach { mark ->
            // In speech the " key is the ' key, lit while its inner quote is open.
            val quoteKey = mark == Mark.Speech && inSpeech
            val on = if (quoteKey) innerQuote else mark == lit
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.outlineFaint, MaterialTheme.shapes.small)
                    .clickable(onClickLabel = mark.key.replaceFirstChar { it.uppercase() }) { onPress(mark) }
                    .semantics { contentDescription = if (quoteKey) "Quote inside speech" else mark.key.replaceFirstChar { it.uppercase() } },
                contentAlignment = Alignment.Center,
            ) {
                // Just the mark: " * B, the way it is typed.
                Text(
                    text = when (mark) {
                        Mark.Speech -> if (quoteKey) "'" else "\""
                        Mark.Action -> "*"
                        Mark.Bold -> "B"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (mark == Mark.Bold) androidx.compose.ui.text.font.FontWeight.Black else androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (on) ButlerTheme.colors.onAccentSoft else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Who the next line goes out as: the persona's face on a key, where Janitor puts it in its
 * message box. Tapping it opens the persona list; the pick holds for this chat.
 */
@Composable
private fun PersonaChip(persona: PersonaOption?, onClick: () -> Unit) {
    val name = persona?.name.orEmpty()
    Avatar(
        url = persona?.avatarUrl,
        name = name,
        size = 40.dp,
        initialStyle = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (name.isEmpty()) "Choose persona" else "Playing as $name. Change persona" },
    )
}

/**
 * Write for me / enhance my draft: a quiet glyph on the field, never a second red key.
 * While the line is being written it is that line's Stop.
 */
@Composable
private fun WriteKey(writing: Boolean, rewrite: Boolean, onWrite: () -> Unit, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = if (writing) onStop else onWrite)
            .semantics { contentDescription = if (writing) "Stop writing" else if (rewrite) "Enhance my draft" else "Write for me" },
        contentAlignment = Alignment.Center,
    ) {
        if (writing) {
            Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        } else {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (rewrite) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * Send when there is something to send; Stop while the character is writing. One shape for
 * both: a filled key. Stop is the red key with a soft square, so it reads as the same
 * control in its other state rather than an alarm.
 */
@Composable
private fun AdvanceKey(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val red = MaterialTheme.colorScheme.primary
    val live = busy || enabled
    // At rest the key is a quiet glyph on the field; it fills red the moment there is a draft.
    val fill by animateColorAsState(if (live) red else androidx.compose.ui.graphics.Color.Transparent, animationSpec = Motion.enter(Motion.SHORT), label = "key-fill")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .clickable(enabled = live, onClick = if (busy) onStop else onSend)
            .semantics { contentDescription = if (busy) "Stop" else "Send" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = { (fadeIn(Motion.enter(Motion.SHORT)) + scaleIn(Motion.enter(Motion.SHORT), initialScale = 0.6f)) togetherWith fadeOut(Motion.exit(100)) },
            label = "key-glyph",
        ) { stopping ->
            if (stopping) {
                Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** What the keyboard last reported; read in the edit callback only, so it is no state. */
private class KeyboardCopy(var value: String? = null)
