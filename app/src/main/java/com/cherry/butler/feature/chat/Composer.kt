package com.cherry.butler.feature.chat

import com.cherry.butler.ui.components.rim
import com.cherry.butler.ui.components.pressSpring
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
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
) {
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
    // A command is typed plain: rich typing would wrap the `/` in its marks.
    val command = field.text.startsWith("/")
    val richNow = rich && !command
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
    Column(Modifier.widthIn(max = LocalComposerMaxWidth.current).fillMaxWidth()) {
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
            if (offered.isNotEmpty()) {
                CommandList(offered) { picked ->
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
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .card(shape = MaterialTheme.shapes.medium, color = ButlerTheme.colors.surfaceHigh)
                .padding(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (onPickPersona != null) {
                PersonaChip(persona = persona, onClick = onPickPersona)
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.weight(1f).heightIn(min = 36.dp).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                Column {
                    // The three marks, over the words while the box has the keyboard.
                    if (richNow && focused) {
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
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
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
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            // On a PC keyboard one of Enter and Shift+Enter sends, the other starts
                            // a new line; a phone's Enter stays a new line.
                            .then(
                                if (LocalEnterSends.current) Modifier.onPreviewKeyEvent { e ->
                                    enterKey(e, shiftEnterSends, field, change) { if (!busy && draft.isNotBlank()) onSend() }
                                } else Modifier,
                            ),
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
            if (onAddPicture != null && !busy) {
                Spacer(Modifier.width(2.dp))
                PictureKey(attached = picture != null, onClick = { onAddPicture(field.selection.end) })
            }
            if (onWrite != null && !busy) {
                Spacer(Modifier.width(2.dp))
                WriteKey(rewrite = draft.isNotBlank(), onClick = onWrite)
            }
            Spacer(Modifier.width(4.dp))
            AdvanceKey(busy = busy, enabled = draft.isNotBlank(), onSend = onSend, onStop = onStop)
        }
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
                    .size(width = 36.dp, height = 28.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .rim(1.dp, if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.outlineFaint, MaterialTheme.shapes.small)
                    // Never takes the focus: a click with a mouse moved it off the box, which hid
                    // the keys (they show while the box has it) and lost the caret.
                    .focusProperties { canFocus = false }
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
private fun AdvanceKey(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
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
            .semantics { contentDescription = if (busy) "Stop" else "Send" },
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
                    Icons.AutoMirrored.Rounded.Send,
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
