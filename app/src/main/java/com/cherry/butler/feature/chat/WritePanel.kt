package com.cherry.butler.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.Perspective
import com.cherry.butler.core.data.remote.dto.PronounsDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.generation.SuggestionPrompt
import com.cherry.butler.core.generation.SuggestionService
import com.cherry.butler.ui.components.LocalPointerMotion
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.hairlineFrame

/**
 * Write for me and Enhance, in a panel of their own. An empty composer opens "Write for me",
 * the user's next line from scratch; words in it open "Enhance", that draft rewritten, shown
 * at the top. Before the key, how: the voice, quick picks that stack, and a line of the user's
 * own, all sent with this one request. While it is written the thinking line shows the model
 * at work (a tap reads the thought), then the words come in here, not into the composer.
 * Use takes them; Again asks once more as the panel now says.
 *
 * On a phone it is a sheet. With a mouse it is a card risen from the composer at the column's
 * right, the story dimmed behind it: a sheet across a PC window still reads as a phone.
 */
@Composable
fun WritePanel(
    panel: ChatViewModel.WritePanel,
    /** The service's state for this chat; only a line being written, written, or failed shows. */
    state: SuggestionService.State?,
    personaPronouns: PronounsDto?,
    word: String,
    onWrite: (SuggestionService.Ask) -> Unit,
    onStop: () -> Unit,
    onUse: () -> Unit,
    onOpenThought: () -> Unit,
    onClose: () -> Unit,
    /** How tall the composer stands, so the desktop card rises from just above it. */
    composerHeight: Dp = 0.dp,
) {
    val flight = state?.takeIf { it is SuggestionService.State.Writing || it is SuggestionService.State.Ready || it is SuggestionService.State.Failed }
    if (LocalPointerMotion.current) {
        DeskCard(onClose = onClose, composerHeight = composerHeight) {
            WritePanelBody(panel, flight, personaPronouns, word, onWrite, onStop, onUse, onOpenThought, onClose, focusGuide = true)
        }
    } else {
        PhoneSheet(onClose = onClose) {
            WritePanelBody(panel, flight, personaPronouns, word, onWrite, onStop, onUse, onOpenThought, onClose, focusGuide = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Box(Modifier.navigationBarsPadding().imePadding()) { content() }
    }
}

/**
 * The desktop's panel: a card at the bottom right of the chat, over the story and clear of
 * the composer, at most as wide as a reading line. A click on the dimmed story or Esc puts it away.
 */
@Composable
private fun DeskCard(onClose: () -> Unit, composerHeight: Dp, content: @Composable () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(SheetScrim.copy(alpha = 0.3f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close", onClick = onClose),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 12.dp, end = 12.dp, top = 72.dp, bottom = composerHeight + 4.dp)
                .widthIn(max = DeskCardWidth)
                .fillMaxWidth()
                .shadow(18.dp, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.large)
                // Its own clicks stay on it; the dimmed story under it would take them.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(top = 14.dp),
        ) { content() }
    }
}

/** The desktop card's width: a comfortable line for the draft, and room for the picks. */
private val DeskCardWidth = 480.dp

/** Lays the content [by] wider on each side than it was given, for a child with side room of its own. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wide)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritePanelBody(
    panel: ChatViewModel.WritePanel,
    flight: SuggestionService.State?,
    personaPronouns: PronounsDto?,
    word: String,
    onWrite: (SuggestionService.Ask) -> Unit,
    onStop: () -> Unit,
    onUse: () -> Unit,
    onOpenThought: () -> Unit,
    onClose: () -> Unit,
    focusGuide: Boolean,
) {
    val rewrite = panel.original.isNotBlank()
    // The panel's own say, from how it was last asked; it lives as long as the panel is open.
    var perspective by remember { mutableStateOf(panel.ask.perspective) }
    var picks by remember { mutableStateOf(panel.ask.picks) }
    var words by remember { mutableStateOf(panel.ask.words) }
    val writing = flight is SuggestionService.State.Writing
    val ask = SuggestionService.Ask(picks = picks, words = words.trim(), perspective = if (rewrite) perspective else perspective ?: Perspective.First)
    val run = { if (!writing) onWrite(ask) }
    val focus = remember { FocusRequester() }
    if (focusGuide) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val scroll = rememberScrollState()
    // The words as they come in are below the picks: follow them.
    val live = (flight as? SuggestionService.State.Writing)?.text?.length
    LaunchedEffect(live) { if (live != null && live > 0) scroll.animateScrollTo(scroll.maxValue) }

    Column {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (rewrite) "Enhance" else "Write for me",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (rewrite) "Your draft, made to read well. Say how, or leave it." else "Your next line, written for you. Say how, or leave it.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (rewrite) {
                RpText(
                    text = panel.original,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(ButlerTheme.colors.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            Text("Written as", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(bottom = 6.dp))
            // Enhance can also leave the draft's own voice alone, and does unless told.
            val voices = if (rewrite) listOf(null) + Perspective.entries else Perspective.entries
            val third = personaPronouns?.subjective?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() } ?: "They"
            SegmentedChoice(
                options = voices.map { v ->
                    when (v) {
                        null -> "As written"
                        Perspective.First -> "I"
                        Perspective.Third -> third
                        Perspective.Second -> "You"
                    }
                },
                selected = voices.indexOf(if (rewrite) perspective else perspective ?: Perspective.First).coerceAtLeast(0),
                onSelect = { perspective = voices[it] },
                // The bar brings its own side room; the panel's is already there.
                modifier = Modifier.bleed(12.dp),
            )

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(ButlerTheme.colors.surfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = words,
                    onValueChange = { words = it.take(300) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { run() }),
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        // On a PC keyboard Enter writes and Shift+Enter starts a new line, as in the composer.
                        .then(
                            if (LocalEnterSends.current) Modifier.onPreviewKeyEvent { e ->
                                val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                                if (!enter || e.isShiftPressed) return@onPreviewKeyEvent false
                                if (e.type == KeyEventType.KeyDown) run()
                                true
                            } else Modifier,
                        ),
                    decorationBox = { inner ->
                        if (words.isEmpty()) {
                            Text(
                                if (rewrite) "e.g. Keep it playful" else "e.g. Tease them a little",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ButlerTheme.colors.textLow,
                            )
                        }
                        inner()
                    },
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SuggestionPrompt.picks(rewrite).forEach { pick ->
                    val on = pick.label in picks
                    Text(
                        text = pick.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textMed,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.rule, MaterialTheme.shapes.small)
                            .clickable { picks = SuggestionPrompt.toggle(picks, pick.label, rewrite) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Written(flight, word, onOpenThought)
        }

        Actions(
            flight = flight,
            rewrite = rewrite,
            onRun = run,
            onStop = onStop,
            onUse = onUse,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        )
    }
}

/**
 * What came back: the thinking line while the model thinks (the reader's own look), folded to
 * "Thought" once the words begin; the words as they arrive; or why nothing did.
 */
@Composable
private fun Written(flight: SuggestionService.State?, word: String, onOpenThought: () -> Unit) {
    when (flight) {
        is SuggestionService.State.Writing -> {
            val waiting = flight.text.isEmpty()
            // Before the first word the line shows even for a model that doesn't think: it is
            // what "it is on its way" looks like everywhere in Butler.
            if (waiting || flight.thinking.isNotEmpty()) {
                ThinkingLine(
                    text = flight.thinking,
                    streaming = waiting,
                    onOpen = { if (flight.thinking.isNotEmpty()) onOpenThought() },
                    word = word,
                    durationMs = flight.thoughtMs,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (!waiting) WrittenText(flight.text)
        }
        is SuggestionService.State.Ready -> {
            if (flight.thinking.isNotEmpty()) {
                ThinkingLine(
                    text = flight.thinking,
                    streaming = false,
                    onOpen = onOpenThought,
                    word = word,
                    durationMs = flight.thoughtMs,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            WrittenText(flight.text)
        }
        is SuggestionService.State.Failed -> Text(
            text = "Couldn't write it. ${flight.message}",
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.danger,
            modifier = Modifier.padding(top = 14.dp),
        )
        else -> Unit
    }
}

/** The line, as the story shows a line: actions and speech in their own looks. */
@Composable
private fun WrittenText(text: String) {
    RpText(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .hairlineFrame(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** The panel's key, pinned under it: Write (or Enhance); Stop while it runs; Again and Use once it is written. */
@Composable
private fun Actions(
    flight: SuggestionService.State?,
    rewrite: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tall = Modifier.heightIn(min = 50.dp)
    when (flight) {
        is SuggestionService.State.Writing -> OutlinedButton(
            onClick = onStop,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, ButlerTheme.colors.rule),
            modifier = modifier.fillMaxWidth().then(tall),
        ) {
            Box(Modifier.size(11.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Stop", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        is SuggestionService.State.Ready -> Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRun,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, ButlerTheme.colors.rule),
                modifier = Modifier.weight(1f).then(tall),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Again", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            RedKey(label = "Use", onClick = onUse, modifier = Modifier.weight(1f).then(tall))
        }
        else -> RedKey(
            label = if (rewrite) "Enhance" else "Write",
            onClick = onRun,
            icon = true,
            modifier = modifier.fillMaxWidth().then(tall),
        )
    }
}

@Composable
private fun RedKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Boolean = false) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (icon) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
