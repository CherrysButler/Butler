package com.cherry.butler.feature.chat

import com.cherry.butler.ui.components.rim
import com.cherry.butler.ui.components.pressSpring
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import com.cherry.butler.core.generation.SuggestionService
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import com.cherry.butler.core.design.Motion
import androidx.compose.runtime.getValue
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.ui.components.Avatar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.glassSurface
import com.cherry.butler.ui.components.liftShadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * The text window. A ruled field on the ground, and one red advance key at the right;
 * while a reply is being written the key becomes Stop. Beside it, the write key: empty
 * field, "write for me"; words in it, "enhance my draft". The line is written in its own
 * panel (WritePanel); once used it can be undone or written again until it is edited.
 * The draft is already in saved state before it is ever sent.
 */
/** True where Enter should send rather than start a new line (a PC keyboard). */
val LocalEnterSends = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Where one is open: Shift+Enter sends and Enter starts a new line (ChatPrefs.shiftEnterSends). */
val LocalShiftEnterSends = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Enter in a message field on a PC keyboard: one of Enter and Shift+Enter sends ([send]), the
 * other starts a new line, as [shiftSends] says (Settings › Chat › Send with). The line break
 * is put in here: Compose's desktop field types nothing for Shift+Enter. Returns whether the
 * key was Enter (and so taken); Ctrl or Alt with it is left alone.
 */
internal fun enterKey(
    e: androidx.compose.ui.input.key.KeyEvent,
    shiftSends: Boolean,
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    send: () -> Unit,
): Boolean {
    val enter = e.key == androidx.compose.ui.input.key.Key.Enter || e.key == androidx.compose.ui.input.key.Key.NumPadEnter
    if (!enter || e.isCtrlPressed || e.isAltPressed) return false
    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
        if (e.isShiftPressed == shiftSends) {
            send()
        } else {
            val from = minOf(value.selection.start, value.selection.end)
            val to = maxOf(value.selection.start, value.selection.end)
            onValue(TextFieldValue(value.text.replaceRange(from, to, "\n"), TextRange(from + 1)))
        }
    }
    return true
}

/** A line of the story being edited in the message box: what its strip says, and how the save is going. */
class ComposerEdit(val label: String, val saving: Boolean = false, val error: String? = null)

/**
 * The story's own keys on a PC, for when the message box does not have the keyboard: the chat
 * sets them, the window's chat column presses them.
 */
class ChatKeys {
    var onEditLast: (() -> Unit)? = null
    var onStepReply: ((Int) -> Unit)? = null
}

val LocalChatKeys = androidx.compose.runtime.staticCompositionLocalOf<ChatKeys?> { null }

/** Ctrl+P: plain from here (out of the mark the caret is in, or an empty pair taken away). */
internal fun plainKey(e: androidx.compose.ui.input.key.KeyEvent): Boolean =
    e.isCtrlPressed && !e.isAltPressed && !e.isShiftPressed && e.key == androidx.compose.ui.input.key.Key.P

/**
 * Where the message box is on screen, so a PC window can take the keyboard off it when a click
 * lands anywhere else (Compose keeps it in the box otherwise, and the opened box never closes).
 */
class ComposerSpot { var bounds: androidx.compose.ui.geometry.Rect? = null }

val LocalComposerSpot = androidx.compose.runtime.staticCompositionLocalOf<ComposerSpot?> { null }

/** The mark a keyboard shortcut stands for: Ctrl+' speech, Ctrl+I action, Ctrl+B bold. */
internal fun markKey(e: androidx.compose.ui.input.key.KeyEvent): Mark? {
    if (!e.isCtrlPressed || e.isAltPressed || e.isShiftPressed) return null
    return when (e.key) {
        androidx.compose.ui.input.key.Key.Apostrophe -> Mark.Speech
        androidx.compose.ui.input.key.Key.I -> Mark.Action
        androidx.compose.ui.input.key.Key.B -> Mark.Bold
        else -> null
    }
}

/**
 * The widest the text window grows, centred, with the ground either side: a wide PC window
 * otherwise stretches it edge to edge. Unbounded on the phone, where it spans the screen.
 */
val LocalComposerMaxWidth = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.ui.unit.Dp.Unspecified }

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
    /** Opens the Write panel; null hides the write key. */
    onWrite: (() -> Unit)? = null,
    onUndoWrite: () -> Unit = {},
    onWriteAgain: () -> Unit = {},
    /** Rich typing on; [richDefault] is what typing opens on its own (null: plain). */
    rich: Boolean = false,
    richDefault: Mark? = Mark.Action,
    /** Picks what typing starts as (null: plain) from the mode box on a PC; null hides the box. */
    onRichMode: ((Mark?) -> Unit)? = null,
    /** A picture in the draft, and its keys; [onAddPicture] null hides the picture key. */
    picture: ChatViewModel.Picture? = null,
    /** Called with the caret's place in the draft, so the picture lands where the user is. */
    onAddPicture: ((cursor: Int) -> Unit)? = null,
    onUndoPicture: () -> Unit = {},
    onDescribeAgain: () -> Unit = {},
    onRemovePicture: () -> Unit = {},
    /** On a PC keyboard, Shift+Enter sends and Enter starts a new line (see [enterKey]). */
    shiftEnterSends: Boolean = false,
    /** What `/` offers: Butler's own commands, never sent to the character. Empty offers none. */
    commands: List<SlashCommand> = emptyList(),
    /** Over the box, inside its width: Lessons' strip while /learn runs. */
    above: (@Composable () -> Unit)? = null,
    /**
     * Over a background picture with the frame as glass (Glass.kt): the box itself is the
     * glass, floating over the picture, and no ground is painted behind it.
     */
    glass: io.github.fletchmckee.liquid.LiquidState? = null,
    /**
     * A line of the story being edited here, in the box, instead of where it stands: [draft] is
     * its words, Send saves it. What the strip over the box says of it.
     */
    editing: ComposerEdit? = null,
    onCancelEdit: () -> Unit = {},
    /** In the empty box on a keyboard: ↑ takes your last line into the box to edit. */
    onEditLast: (() -> Unit)? = null,
    /** In the empty box on a keyboard: ← the reply before, → the next one, or a new one after the last. */
    onStepReply: ((Int) -> Unit)? = null,
) {
    // A line taken into the box brings the keyboard with it.
    val boxFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(editing != null) { if (editing != null) runCatching { boxFocus.requestFocus() } }
    // The field keeps its own caret; the draft (saved state) is its text. A draft changed from
    // outside (sent, written for you, undone) resets the caret to the end.
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    var typing by remember(richDefault) { mutableStateOf(TypingState(richDefault)) }
    /** The text the keyboard last reported, before the marks touched it. */
    val imeText = remember { KeyboardCopy() }
    var focused by remember { mutableStateOf(false) }
    /** Counts drafts emptied from outside (a line sent): the box offers its default mark again. */
    var emptied by remember { mutableStateOf(0) }
    LaunchedEffect(draft) {
        if (field.text != draft) {
            field = TextFieldValue(draft, TextRange(draft.length))
            if (draft.isEmpty()) {
                typing = TypingState(richDefault)
                emptied++
            }
        }
    }
    // A command is typed plain: rich typing would wrap the `/` in its marks.
    val command = field.text.startsWith("/")
    val richNow = rich && !command
    // On a PC the box opens up while it has the keyboard: a line at rest, three when writing,
    // wider by its gutters. Only the opening animates; lines typed past that grow it at once.
    val desk = LocalEnterSends.current
    val open = focused
    // The phone's box does the same, a slim line at rest and opened out while it is written in,
    // but not on a clock of its own: it is as open as the keyboard is up (0 to 1), so the two
    // rise and fall as one thing. Without an on-screen keyboard (keys plugged in, or one that
    // floats) it opens on the PC's beat a moment after it is given the keyboard.
    val rise = if (desk) 0f else com.cherry.butler.core.platform.keyboardRise()
    var keysOnly by remember { mutableStateOf(false) }
    val riseNow by androidx.compose.runtime.rememberUpdatedState(rise)
    if (!desk) {
        LaunchedEffect(focused) {
            if (!focused) keysOnly = false
            else { kotlinx.coroutines.delay(450); if (riseNow == 0f) keysOnly = true }
        }
    }
    val timed by androidx.compose.animation.core.animateFloatAsState(
        if (keysOnly && focused) 1f else 0f,
        androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)),
        label = "composer opened by keys",
    )
    val opened = if (desk) 0f else maxOf(rise, timed)
    if (!desk) {
        // The keyboard put away is the writing put away: the box closes with it, as it starts
        // down and not once it has landed, so the two move together.
        val keyboardUp = com.cherry.butler.core.platform.keyboardStaying()
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        var keyboardSeen by remember { mutableStateOf(false) }
        LaunchedEffect(keyboardUp) {
            if (keyboardUp) keyboardSeen = true
            else if (keyboardSeen) { keyboardSeen = false; focusManager.clearFocus() }
        }
    }
    val opening = androidx.compose.animation.core.tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 300,
        easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f),
    )
    val roomMin by androidx.compose.animation.core.animateDpAsState(if (open) 88.dp else 36.dp, opening, label = "composer room")
    // On a PC the default mark is already in the empty box when it opens, the caret inside it, so
    // the box shows what typing will be. A box holding only that pair is still empty: it is not
    // sent, not kept as a draft, and goes when the box closes.
    val bare = if (desk && richNow) richDefault?.let { it.open + it.close } else null
    val pairs = remember { Mark.entries.map { it.open + it.close }.toSet() }
    // Any empty pair is still nothing written: switching modes swaps the pair a frame after the
    // mode changes, and the old one must not light Send and Write for that frame.
    val empty = field.text.isBlank() || (desk && richNow && field.text in pairs)
    // Offered when the box opens or a line has gone out; never because the user emptied it. A
    // new mode picked while the box holds only an empty pair swaps it (or clears it, for plain).
    LaunchedEffect(focused, emptied, bare) {
        val leftover = field.text in pairs
        if (focused && (field.text.isEmpty() || (leftover && field.text != bare))) {
            if (bare != null) {
                field = TextFieldValue(bare, TextRange(bare.length / 2))
                typing = TypingState(null)
            } else if (leftover) {
                field = TextFieldValue("")
                if (draft.isNotEmpty()) onDraftChanged("")
            }
        } else if (!focused && leftover) {
            field = TextFieldValue("")
            if (draft.isNotEmpty()) onDraftChanged("")
        }
    }
    // The words take the room the box opened, so the mark keys sit at its bottom.
    // Three lines' room on a PC, two on a phone, where the keyboard has half the screen.
    val wordsMinDesk by androidx.compose.animation.core.animateDpAsState(if (desk && open) 76.dp else 0.dp, opening, label = "composer words")
    val wordsMin = if (desk) wordsMinDesk else 52.dp * opened
    // At rest the box sits in from the column's edges; writing, it opens out to them.
    val gutterDesk by androidx.compose.animation.core.animateDpAsState(if (open) 12.dp else 44.dp, opening, label = "composer gutter")
    val gutter = if (desk) gutterDesk else 28.dp - 16.dp * opened
    val marks = RichMarks(
        mark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        speech = com.cherry.butler.core.design.LocalRpLook.current.speech?.let { androidx.compose.ui.graphics.Color(it) } ?: ButlerTheme.colors.speech,
        action = com.cherry.butler.core.design.LocalRpLook.current.action?.let { androidx.compose.ui.graphics.Color(it) } ?: ButlerTheme.colors.textMed,
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (glass == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Column(Modifier.widthIn(max = LocalComposerMaxWidth.current).fillMaxWidth()) {
        editing?.let { edit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = edit.error ?: if (edit.saving) "Saving…" else edit.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (edit.error != null) ButlerTheme.colors.danger else ButlerTheme.colors.textLow,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StateAction(label = "Cancel", onClick = onCancelEdit)
            }
        }
        (suggestion as? SuggestionService.State.Used)?.let { written ->
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
        picture?.let { PictureStrip(it, onUndo = onUndoPicture, onAgain = onDescribeAgain, onRemove = onRemovePicture) }
        // `/` and a command's name being typed, or its options after it: what to tap instead of type.
        val offered = if (command) ChatCommands.offers(field.text, commands) else emptyList()
        // Lessons' strip and the commands float over the chat, just above the box, taking no
        // room: grown into the box, they pushed the whole chat up as they came and went.
        Column(Modifier.fillMaxWidth().overTheChat().padding(bottom = 2.dp)) {
            above?.invoke()
            // The list rises out of the box and sinks back into it, keeping its rows while it goes.
            var lastOffered by remember { mutableStateOf(offered) }
            if (offered.isNotEmpty()) lastOffered = offered
            AnimatedVisibility(
                visible = offered.isNotEmpty(),
                enter = fadeIn(com.cherry.butler.core.design.Motion.enter()) +
                    androidx.compose.animation.slideInVertically(com.cherry.butler.core.design.Motion.enter()) { it / 6 },
                exit = fadeOut(com.cherry.butler.core.design.Motion.exit()) +
                    androidx.compose.animation.slideOutVertically(com.cherry.butler.core.design.Motion.exit()) { it / 6 },
            ) {
                CommandList(lastOffered) { picked ->
                    field = TextFieldValue(picked.fill, TextRange(picked.fill.length))
                    onDraftChanged(picked.fill)
                }
            }
        }
        // One field across the whole width, as Janitor's message box: the persona's face at
        // its start, the words, the send key at its end. The keys sit inside the field, so the
        // writing room is the screen's width and not what two keys leave of it.
        // Concentric: the box's corner is the keys' corner plus the 6 dp around them, so the send
        // key's curve runs parallel to the box's instead of jamming into it. On the 12 dp gutter
        // the cards keep, resting on the ground rather than hovering over it.
        val showMode = desk && open && onRichMode != null
        Box(
            modifier = Modifier
                .padding(start = gutter, end = gutter, top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .then(LocalComposerSpot.current?.let { spot ->
                    Modifier.onGloballyPositioned { spot.bounds = it.boundsInWindow() }
                } ?: Modifier)
                .heightIn(min = 48.dp)
                .then(
                    if (glass == null) {
                        Modifier.card(shape = MaterialTheme.shapes.medium, color = ButlerTheme.colors.surfaceHigh)
                    } else {
                        // Glass: lifted off the picture by its shadow, lit along its rim, as the nav island is.
                        Modifier.glassSurface(glass, MaterialTheme.shapes.medium)
                    },
                )
                .padding(6.dp),
        ) {
        // The words, in the room they are given: between the keys on a PC, over them on a phone.
        val words: @Composable (Modifier) -> Unit = { room ->
            Box(modifier = room, contentAlignment = if (if (desk) open else opened > 0f) Alignment.TopStart else Alignment.CenterStart) {
                // While a reply is written the picture and write keys step aside, and the corner
                // chip would sit over the words' first line: they leave it room then.
                val keysNarrow = (onAddPicture == null || busy) && (onWrite == null || busy)
                Column(Modifier.padding(end = if (showMode && keysNarrow) 64.dp else 0.dp)) {
                    // The field has drawn the marks' version: the keyboard has it now.
                    SideEffect { imeText.unseen = false }
                    val change: (TextFieldValue) -> Unit = { new ->
                            if (rich && !new.text.startsWith("/")) {
                                // When the marks change what was typed, the keyboard still holds its own
                                // copy until the field redraws, and can send that copy again in the same
                                // frame (Gboard does on a backspace). Its text is then what it sent last
                                // time: not an edit, so the marks' version stands. Only until the field
                                // redraws: after it, the same text is a real edit (deleting the space a
                                // step out of `*test*` added gives back exactly what was sent before).
                                val stale = imeText.unseen && new.text == imeText.value && new.text != field.text
                                imeText.value = new.text
                                if (!stale) {
                                    val (v, st) = RichTyping.onChange(field, new, typing, richDefault)
                                    field = v
                                    typing = st
                                    imeText.unseen = v.text != new.text
                                }
                            } else {
                                field = new
                            }
                            if (field.text != draft) onDraftChanged(field.text)
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = change,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        visualTransformation = if (richNow) marks else androidx.compose.ui.text.input.VisualTransformation.None,
                        maxLines = if (desk) { if (open) 14 else 6 } else { if (opened > 0f) 8 else 3 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = wordsMin)
                            .focusRequester(boxFocus)
                            .onFocusChanged { focused = it.isFocused }
                            // Ctrl+' " , Ctrl+I * and Ctrl+B ** press the mark keys, exactly as a click
                            // on them would: the mark opens at the caret for the words to come.
                            .onPreviewKeyEvent { e ->
                                // With nothing written, the arrows are the story's: ↑ edits your
                                // last line, ← and → step through the last reply's versions.
                                if (empty && editing == null && !e.isCtrlPressed && !e.isAltPressed && !e.isShiftPressed) {
                                    val act: (() -> Unit)? = when (e.key) {
                                        androidx.compose.ui.input.key.Key.DirectionUp -> onEditLast
                                        androidx.compose.ui.input.key.Key.DirectionLeft -> onStepReply?.let { step -> { step(-1) } }
                                        androidx.compose.ui.input.key.Key.DirectionRight -> onStepReply?.let { step -> { step(1) } }
                                        else -> null
                                    }
                                    if (act != null) {
                                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) act()
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                if (plainKey(e)) {
                                    if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                        val v = RichTyping.plainHere(field)
                                        field = v
                                        typing = TypingState(null)
                                        if (v.text != draft) onDraftChanged(v.text)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                markKey(e)?.let { mark ->
                                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                                    val (v, st) = if (richNow) RichTyping.shortcut(field, mark, typing) else RichTyping.pair(field, mark) to typing
                                    field = v
                                    typing = st
                                    if (v.text != draft) onDraftChanged(v.text)
                                }
                                true
                            } ?: false }
                            // On a PC keyboard one of Enter and Shift+Enter sends, the other starts
                            // a new line; a phone's Enter stays a new line.
                            .then(
                                if (LocalEnterSends.current) Modifier.onPreviewKeyEvent { e ->
                                    enterKey(e, shiftEnterSends, field, change) { if (!busy && !empty) onSend() }
                                } else Modifier,
                            ),
                        decorationBox = { inner ->
                            // Pinned to the top: in the opened box the words start at its first
                            // line and run down, instead of floating in the middle of the room.
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                                if (field.text.isEmpty()) {
                                    Text(
                                        text = "Write…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = ButlerTheme.colors.textLow,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }
        val tools: @Composable () -> Unit = {
            // A line being edited is saved as it is written: no picture, no writing for it.
            if (onAddPicture != null && !busy && editing == null) {
                Spacer(Modifier.width(2.dp))
                PictureKey(attached = picture != null, onClick = { onAddPicture(field.selection.end) })
            }
            if (onWrite != null && !busy && editing == null) {
                Spacer(Modifier.width(2.dp))
                WriteKey(rewrite = !empty, onClick = onWrite)
            }
            Spacer(Modifier.width(4.dp))
            AdvanceKey(busy = busy, enabled = !empty && editing?.saving != true, onSend = onSend, onStop = onStop, saves = editing != null)
        }
        if (desk) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                if (onPickPersona != null) {
                    PersonaChip(persona = persona, onClick = onPickPersona)
                    Spacer(Modifier.width(8.dp))
                }
                words(Modifier.weight(1f).heightIn(min = roomMin).padding(vertical = 6.dp))
                tools()
            }
        } else {
            // On a phone the box is a slim line at rest, the face and the keys in the line with
            // the words. Written in, it opens: the words take its whole width, and every key
            // sits in one row under them, the face and the marks at its start, picture, write
            // and send at its end.
            val side = 8.dp * opened
            val over = 6.dp + 2.dp * opened
            Column(Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    if (opened < 1f && onPickPersona != null) {
                        Row(Modifier.folded(1f - opened, vertical = false), verticalAlignment = Alignment.CenterVertically) {
                            PersonaChip(persona = persona, onClick = onPickPersona)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    words(Modifier.weight(1f).heightIn(min = 36.dp).padding(horizontal = side, vertical = over))
                    if (opened < 1f) {
                        Row(Modifier.folded(1f - opened, vertical = false), verticalAlignment = Alignment.CenterVertically) { tools() }
                    }
                }
                if (opened > 0f) {
                Row(modifier = Modifier.fillMaxWidth().folded(opened, vertical = true), verticalAlignment = Alignment.CenterVertically) {
                    if (onPickPersona != null) {
                        PersonaChip(persona = persona, onClick = onPickPersona)
                        Spacer(Modifier.width(6.dp))
                    }
                    PhoneMarks(shown = richNow) {
                        MarkKeys(
                            lit = RichTyping.lit(field, typing),
                            inSpeech = RichTyping.inSpeech(field, typing),
                            innerQuote = typing.inner,
                            onPress = { mark ->
                                val (v, st) = RichTyping.press(field, mark, typing)
                                field = v
                                typing = st
                                if (v.text != draft) onDraftChanged(v.text)
                                // A mark pressed in a box at rest starts the writing.
                                if (!focused) runCatching { boxFocus.requestFocus() }
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    tools()
                }
                }
            }
        }
        // The typing mode, floating in the open box's top corner over the keys' column, so the
        // words still start at the box's first line. It comes and goes with the box.
        CornerMode(
            shown = showMode,
            mode = if (rich) richDefault else null,
            onPick = { onRichMode?.invoke(it) },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        }
    }
    }
}

/** A typing mode's icon: quote for speech, italic for action, bold, and clear for plain. */
private fun Mark?.icon(): ImageVector = when (this) {
    Mark.Speech -> Icons.Rounded.FormatQuote
    Mark.Action -> Icons.Rounded.FormatItalic
    Mark.Bold -> Icons.Rounded.FormatBold
    null -> Icons.Rounded.FormatClear
}

/** The modes in the order the chip's menu and wheel go through them. */
private val MODES: List<Mark?> = listOf(Mark.Speech, Mark.Action, Mark.Bold, null)

private fun Mark?.label(): String = when (this) {
    Mark.Speech -> "Speech"
    Mark.Action -> "Action"
    Mark.Bold -> "Bold"
    null -> "Plain"
}

private fun Mark?.shortcut(): String = when (this) {
    Mark.Speech -> "Ctrl '"
    Mark.Action -> "Ctrl I"
    Mark.Bold -> "Ctrl B"
    null -> "Ctrl P"
}

/**
 * What typing starts as, in the open box's top corner on a PC: a small chip with the mode's
 * icon and name and the up-and-down chevron of a picker. Clicking it opens the four modes in
 * the chat's own menu, each with its shortcut on a keycap. The menu never takes the keyboard,
 * so the box stays open behind it.
 */
@Composable
private fun ModeChip(mode: Mark?, onPick: (Mark?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    // A click on the chip while the menu is open first closes the menu (a click outside it), and
    // would open it again at once: a click that soon after closing leaves it closed.
    var closedAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val ink = if (open) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed
    Box(modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 26.dp, max = 26.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                .focusProperties { canFocus = false }
                .clickable(onClickLabel = "Typing mode") { if (System.currentTimeMillis() - closedAt > 250) open = !open }
                // The wheel steps through the modes, as the chevron says: down to the next, up back.
                .pointerInput(mode) {
                    var acc = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type != androidx.compose.ui.input.pointer.PointerEventType.Scroll) continue
                            acc += e.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                            e.changes.forEach { it.consume() }
                            if (kotlin.math.abs(acc) >= 1f) {
                                val all = MODES
                                val step = if (acc > 0) 1 else -1
                                acc = 0f
                                onPick(all[(all.indexOf(mode) + step).mod(all.size)])
                            }
                        }
                    }
                }
                .semantics { contentDescription = "Typing mode: ${mode.label()}" }
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(mode.icon(), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(mode.label(), style = MaterialTheme.typography.labelMedium, color = ink)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(14.dp))
        }
        // The chat menu's look, with its own way in and out: it grows out of the chip's corner
        // and shrinks back into it, the same both ways (the stock menu all but vanishes on close).
        val shown = remember { androidx.compose.animation.core.MutableTransitionState(false) }
        shown.targetState = open
        if (shown.currentState || shown.targetState) androidx.compose.ui.window.Popup(
            popupPositionProvider = AboveRightEdge,
            onDismissRequest = { open = false; closedAt = System.currentTimeMillis() },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        ) {
            val corner = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
            AnimatedVisibility(
                visibleState = shown,
                enter = fadeIn(tween(MENU_MS)) + scaleIn(tween(MENU_MS, easing = com.cherry.butler.core.design.Motion.Glide), initialScale = 0.9f, transformOrigin = corner),
                exit = fadeOut(tween(MENU_MS)) + androidx.compose.animation.scaleOut(tween(MENU_MS, easing = com.cherry.butler.core.design.Motion.Glide), targetScale = 0.9f, transformOrigin = corner),
            ) {
            androidx.compose.material3.Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(4.dp),
            ) { Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).padding(vertical = 8.dp)) {
            MODES.forEach { m ->
                val on = m == mode
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            m.label(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            m.icon(),
                            contentDescription = null,
                            tint = if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = { Keycap(m.shortcut()) },
                    onClick = { onPick(m); open = false },
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
            } }
            }
        }
    }
}

private const val MENU_MS = 160

/** Opens a popup just above its anchor, right edges lined up; below it when there is no room above. */
private object AboveRightEdge : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/** The mode chip in the open box's corner, coming in with the box and going with it. */
@Composable
private fun CornerMode(shown: Boolean, mode: Mark?, onPick: (Mark?) -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(300)) + scaleIn(
            tween(300, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.2f, 1f)),
            initialScale = 0.9f,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
        ),
        exit = fadeOut(tween(150)) + androidx.compose.animation.scaleOut(
            tween(150, easing = com.cherry.butler.core.design.Motion.Glide),
            targetScale = 0.9f,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
        ),
        modifier = modifier,
    ) {
        ModeChip(mode = mode, onPick = onPick)
    }
}

/** A shortcut, the way it is pressed: on a faint keycap. */
@Composable
private fun Keycap(keys: String) {
    Text(
        text = keys,
        style = MaterialTheme.typography.labelSmall,
        color = ButlerTheme.colors.textLow,
        modifier = Modifier
            .rim(1.dp, ButlerTheme.colors.outlineFaint, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Rich typing's keys: the bare marks, " * B. The lit one is what the next words become. Small blocks along the top of the box, so they never
 * cover what is being written.
 */
@Composable
private fun MarkKeys(lit: Mark?, inSpeech: Boolean, innerQuote: Boolean, onPress: (Mark) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        listOf(Mark.Speech, Mark.Action, Mark.Bold).forEach { mark ->
            // In speech the " key is the ' key, lit while its inner quote is open.
            val quoteKey = mark == Mark.Speech && inSpeech
            val on = if (quoteKey) innerQuote else mark == lit
            val ground by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                tween(com.cherry.butler.core.design.Motion.SHORT),
                label = "mark key",
            )
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = KEY_HEIGHT)
                    .clip(MaterialTheme.shapes.small)
                    .background(ground)
                    // Never takes the focus: a click with a mouse moved it off the box and lost the caret.
                    .focusProperties { canFocus = false }
                    .clickable(onClickLabel = mark.key.replaceFirstChar { it.uppercase() }) { onPress(mark) }
                    .semantics { contentDescription = if (quoteKey) "Quote inside speech" else mark.key.replaceFirstChar { it.uppercase() } },
                contentAlignment = Alignment.Center,
            ) {
                // The mark's icon; in speech the " key opens an inner quote, drawn smaller.
                Icon(
                    mark.icon(),
                    contentDescription = null,
                    tint = if (on) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(if (quoteKey) 16.dp else 20.dp),
                )
            }
        }
    }
}

/**
 * Folds what it holds away along one axis: all of it at 1, none of it at 0, fading as it goes.
 * The room it takes shrinks with it, so its neighbours close up; what is left of it is its
 * near edge, the rest cut off.
 */
private fun Modifier.folded(shown: Float, vertical: Boolean): Modifier = this
    .graphicsLayer { alpha = shown; clip = true }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val width = if (vertical) placeable.width else (placeable.width * shown).toInt()
        val height = if (vertical) (placeable.height * shown).toInt() else placeable.height
        layout(width, height) { placeable.place(0, 0) }
    }

/** The mark keys' place in the phone's row: they fade away while a command is typed, and back. */
@Composable
private fun PhoneMarks(shown: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(com.cherry.butler.core.design.Motion.enter()),
        exit = fadeOut(com.cherry.butler.core.design.Motion.exit()),
    ) { content() }
}

/**
 * Who the next line goes out as: the persona's face on a key, where Janitor puts it in its
 * message box. Tapping it opens the persona list (the pick holds for this chat); holding it
 * shows the face full size.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PersonaChip(persona: PersonaOption?, onClick: () -> Unit) {
    val name = persona?.name.orEmpty()
    val viewer = com.cherry.butler.ui.components.LocalPictureViewer.current
    val face = persona?.avatarUrl?.takeIf { it.isNotBlank() }
    // As tall as the send key, so the face sits level with it beside a one-line draft.
    Box(modifier = Modifier.size(width = 32.dp, height = KEY_HEIGHT), contentAlignment = Alignment.Center) {
        Avatar(
            url = persona?.avatarUrl,
            name = name,
            size = 32.dp,
            initialStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = "Show picture",
                    onLongClick = if (viewer != null && face != null) ({ viewer(face, name) }) else null,
                )
                .semantics { contentDescription = if (name.isEmpty()) "Choose persona" else "Playing as $name. Change persona" },
        )
    }
}

/**
 * Write for me / enhance my draft: a quiet glyph on the field, never a second red key. It
 * opens the Write panel, where the line is asked for, written and taken.
 */
@Composable
private fun WriteKey(rewrite: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = KEY_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (rewrite) "Enhance my draft" else "Write for me" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = if (rewrite) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The picture key (a Butler special): a picture goes into the draft as `[pic]` and is written in. */
@Composable
private fun PictureKey(attached: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = KEY_HEIGHT)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (attached) "Change the picture" else "Add a picture" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AddPhotoAlternate,
            contentDescription = null,
            tint = if (attached) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The picture over the field: its thumbnail, what is happening to it, and its keys. While
 * the model writes it the line says so; written in, the user reads the draft and sends it
 * themselves.
 */
@Composable
private fun PictureStrip(picture: ChatViewModel.Picture, onUndo: () -> Unit, onAgain: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coil.compose.AsyncImage(
            model = picture.file,
            contentDescription = "The picture",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (picture.status) {
                    ChatViewModel.PictureStatus.Describing -> "Writing the picture in\u2026"
                    ChatViewModel.PictureStatus.Described -> "Written in. Read it over, then send."
                    ChatViewModel.PictureStatus.Attached -> "In the draft as [pic]"
                },
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textLow,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (picture.status == ChatViewModel.PictureStatus.Described) StateAction(label = "Undo", onClick = onUndo)
        when (picture.status) {
            ChatViewModel.PictureStatus.Attached -> StateAction(label = "Write it in", onClick = onAgain, emphasis = true)
            ChatViewModel.PictureStatus.Described -> StateAction(label = "Again", onClick = onAgain)
            ChatViewModel.PictureStatus.Describing -> Unit
        }
        StateAction(label = "Remove", onClick = onRemove)
    }
}

/**
 * Send when there is something to send; Stop while the character is writing. One shape for
 * both: a filled key. Stop is the red key with a soft square, so it reads as the same
 * control in its other state rather than an alarm.
 */
@Composable
private fun AdvanceKey(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit, saves: Boolean = false) {
    val red = MaterialTheme.colorScheme.primary
    val live = busy || enabled
    // At rest the key is a quiet glyph on the field; it fills red the moment there is a draft.
    val fill by animateColorAsState(if (live) red else androidx.compose.ui.graphics.Color.Transparent, animationSpec = Motion.enter(Motion.SHORT), label = "key-fill")
    val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(KEY_HEIGHT)
            .pressSpring(press)
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .clickable(interactionSource = press, indication = androidx.compose.foundation.LocalIndication.current, enabled = live, onClick = if (busy) onStop else onSend)
            .semantics { contentDescription = if (busy) "Stop" else if (saves) "Save" else "Send" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = busy,
            transitionSpec = { (fadeIn(Motion.enter(Motion.SHORT)) + scaleIn(Motion.enter(Motion.SHORT), initialScale = 0.6f)) togetherWith fadeOut(Motion.exit(100)) },
            label = "key-glyph",
        ) { stopping ->
            if (stopping) {
                Box(Modifier.size(13.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)))
            } else {
                Icon(
                    if (saves) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** The send key's size, and the height every key in the field shares so they line up. */
private val KEY_HEIGHT = 36.dp

/** What the keyboard last reported; read in the edit callback only, so it is no state. */
private class KeyboardCopy(var value: String? = null) {
    /** The marks changed the keyboard's text and the field hasn't drawn that yet. */
    var unseen = false
}

/**
 * What `/` could be, floating over the chat above the box like a small palette: each command
 * with its icon, its name and a line on what it does; once a command is chosen, its options as
 * a row of keys. A tap takes it into the box.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CommandList(offers: List<ChatCommands.Offer>, onPick: (ChatCommands.Offer) -> Unit) {
    val options = offers.firstOrNull()?.optionOf
    Column(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
            .fillMaxWidth()
            // A tone under the keys' and tiles', so they read to the bottom of the card.
            .card(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, lift = com.cherry.butler.core.design.Lift.Float)
            .padding(6.dp),
    ) {
        if (options != null) {
            Text(
                COMMAND_TITLES[options] ?: options.removePrefix("/"),
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            ) {
                offers.forEach { o ->
                    Text(
                        OPTION_LABELS[o.label] ?: o.label.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(ButlerTheme.colors.surfaceHigh)
                            .clickable(onClickLabel = "Use ${o.label}") { onPick(o) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        } else {
            offers.forEach { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClickLabel = "Use ${c.label}") { onPick(c) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(ButlerTheme.colors.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(COMMAND_ICONS[c.label] ?: Icons.Rounded.AutoAwesome, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    pushStyle(androidx.compose.ui.text.SpanStyle(color = ButlerTheme.colors.textLow))
                                    append("/")
                                    pop()
                                    append(c.label.removePrefix("/"))
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (c.beta) {
                                Text(
                                    "BETA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(c.summary, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Each command's mark in the palette. */
private val COMMAND_ICONS = mapOf(
    "/note" to Icons.Rounded.StickyNote2,
    "/retry" to Icons.Rounded.Refresh,
    "/thinking" to Icons.Rounded.Psychology,
    "/length" to Icons.Rounded.ShortText,
    "/learn" to Icons.Rounded.School,
    "/remember" to Icons.Rounded.BookmarkAdd,
    "/agent" to Icons.Rounded.Loop,
)

/** How an option reads on its key, where the typed word isn't a word. */
private val OPTION_LABELS = mapOf("xhigh" to "Extra high")

/** What an options row is headed with. */
private val COMMAND_TITLES = mapOf("/thinking" to "Thinking level", "/length" to "Reply length", "/agent" to "Agent mode")

/**
 * Laid out at no height, drawn upward from here: what floats over the chat above the box,
 * leaving the box (and the chat, which ends where the box starts) where they are.
 */
private fun Modifier.overTheChat(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    layout(p.width, 0) { p.placeRelative(0, -p.height) }
}
