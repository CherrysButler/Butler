package com.cherry.butler.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.feature.chat.LocalComposerMaxWidth
import com.cherry.butler.feature.chat.LocalSecondaryClick
import com.cherry.butler.feature.chat.LocalTranscriptHost
import com.cherry.butler.feature.chat.TranscriptHost

/**
 * A chat in a PC window: the phone's chat, whole (its bar, the story, the composer), in a
 * column down the middle of the pane, under the faint light an open chat casts over the window
 * (ChatLight). No pattern, nothing that follows the pointer: the story is what moves. The wheel scrolls the story from
 * anywhere, a scrollbar runs down the column's edge, a right-click on a line opens its actions.
 */
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
    BoxWithConstraints(Modifier.fillMaxSize().wheelScrolls(list, reverse = true)) {
        val column = (maxWidth * ColumnShare).coerceAtLeast(ColumnAtLeast).coerceAtMost(maxWidth)
        PaneSize(column, maxHeight, Modifier.align(Alignment.TopCenter).width(column).castsChatLight(light)) {
            CompositionLocalProvider(
                LocalTranscriptHost provides host,
                LocalSecondaryClick provides RightClick,
                // The text window is the column's: it stays with the story.
                LocalComposerMaxWidth provides Dp.Unspecified,
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
