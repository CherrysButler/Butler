package com.cherry.butler.desktop.ui

import com.cherry.butler.ui.components.frostSource
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.feature.chat.LocalComposerMaxWidth
import com.cherry.butler.feature.chat.ComposerSpot
import com.cherry.butler.feature.chat.LocalComposerSpot
import com.cherry.butler.feature.chat.LocalSecondaryClick
import com.cherry.butler.feature.chat.LocalTranscriptHost
import com.cherry.butler.feature.chat.TranscriptHost
import com.cherry.butler.ui.components.Backdrop
import com.cherry.butler.ui.components.LocalBackdropHost

/**
 * A chat in a PC window: the phone's chat, whole (its bar, the story, the composer), in a
 * column down the middle of the pane, under the faint light an open chat casts over the window
 * (ChatLight). Its background is behind the column, or across the pane when the chat hands
 * it over (BackdropHost). No pattern, nothing that follows the pointer: the story is what moves. The wheel scrolls the story from
 * anywhere, a scrollbar runs down the column's edge, a right-click on a line opens its actions.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DesktopChat(content: @Composable () -> Unit) {
    var list by remember { mutableStateOf<LazyListState?>(null) }
    var busy by remember { mutableStateOf(false) }
    val host = remember {
        TranscriptHost(
            onList = { list = it },
            decor = { state ->
                // The newest line is at the bottom of a list laid out from the bottom.
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(state),
                    reverseLayout = true,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 6.dp, horizontal = 2.dp),
                )
            },
            onBusy = { busy = it },
        )
    }
    // The light is the shell's, drawn over the whole window (the rail too): the chat says where
    // its column is and whether a reply is coming.
    val light = LocalChatLight.current
    DisposableEffect(light) {
        light?.on = true
        onDispose { light?.on = false; light?.warm = false }
    }
    LaunchedEffect(busy) { light?.warm = busy }
    val spread = LocalBackdropHost.current?.shown
    // Spread across the window, the picture is drawn here: the chat's frame frosts it from here.
    val windowHaze = io.github.fletchmckee.liquid.rememberLiquidState()
    // A press anywhere in the chat but the message box takes the keyboard off the box, so the
    // box closes back to a line (Compose leaves it there otherwise).
    val spot = remember { ComposerSpot() }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // The keyboard goes to the column itself then, so the story's keys still work: ↑ takes your
    // last line into the box, ← and → step through the last reply's versions.
    val keys = remember { com.cherry.butler.feature.chat.ChatKeys() }
    val column = remember { androidx.compose.ui.focus.FocusRequester() }
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The chat's own ground, under everything: a chat whose picture spreads across the
            // window paints none itself, and the picture comes a few frames after the chat does.
            // Without this the story stood over the screen it was opened from until it came.
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .wheelScrolls(list, reverse = true)
            .focusRequester(column)
            .focusable()
            .onKeyEvent { e ->
                if (e.isCtrlPressed || e.isAltPressed || e.isShiftPressed) return@onKeyEvent false
                val act: (() -> Unit)? = when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionUp -> keys.onEditLast
                    androidx.compose.ui.input.key.Key.DirectionLeft -> keys.onStepReply?.let { step -> { step(-1) } }
                    androidx.compose.ui.input.key.Key.DirectionRight -> keys.onStepReply?.let { step -> { step(1) } }
                    else -> null
                }
                if (act == null) return@onKeyEvent false
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) act()
                true
            }
            .onGloballyPositioned { origin = it.positionInWindow() }
            .onPointerEvent(PointerEventType.Press, androidx.compose.ui.input.pointer.PointerEventPass.Initial) { e ->
                val at = (e.changes.firstOrNull()?.position ?: return@onPointerEvent) + origin
                if (spot.bounds?.contains(at) != true) {
                    focus.clearFocus()
                    runCatching { column.requestFocus() }
                }
            },
    ) {
        spread?.let { Backdrop(dim = it.dim, parallax = false, file = it.file, fileKey = it.key, modifier = Modifier.frostSource(windowHaze)) }
        val column = (maxWidth * ColumnShare).coerceAtLeast(ColumnAtLeast).coerceAtMost(maxWidth)
        PaneSize(column, maxHeight, Modifier.align(Alignment.TopCenter).width(column).castsChatLight(light)) {
            CompositionLocalProvider(
                LocalTranscriptHost provides host,
                // There from the chat's first frame, picture or not yet: the bars are glass at
                // once, instead of plain until the picture is handed over and glass after.
                com.cherry.butler.ui.components.LocalBackdropHaze provides windowHaze,
                LocalSecondaryClick provides RightClick,
                // The text window is the column's: it stays with the story.
                LocalComposerMaxWidth provides Dp.Unspecified,
                LocalComposerSpot provides spot,
                com.cherry.butler.feature.chat.LocalChatKeys provides keys,
            ) { content() }
        }
    }
}

/** The column is this share of the pane (about 1,120 dp maximised at 1440p)... */
private const val ColumnShare = 0.45f

/** ...and never narrower than a comfortable reading line. */
private val ColumnAtLeast = 700.dp

@OptIn(ExperimentalComposeUiApi::class)
private val RightClick: Modifier.(() -> Unit) -> Modifier = { onClick ->
    onPointerEvent(PointerEventType.Press) { event -> if (event.buttons.isSecondaryPressed) onClick() }
}
