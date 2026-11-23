package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill

/** Every bottom sheet's grip: a short pill. */
@Composable
fun SheetHandle() {
    // Counts this sheet in while it is up, so the page under it can step back (sheetPushBack).
    androidx.compose.runtime.DisposableEffect(Unit) {
        SheetStack.open++
        onDispose { SheetStack.open = (SheetStack.open - 1).coerceAtLeast(0) }
    }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).background(ButlerTheme.colors.textLow.copy(alpha = 0.6f), Pill))
    }
}

/** The dim behind a sheet or dialog. */
val SheetScrim = Color.Black.copy(alpha = 0.6f)

/**
 * True inside the PC chat panel: a sheet there is one of the panel's pages, laid out in place,
 * instead of a sheet sliding up over the chat.
 */
val LocalSheetInline = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Closes the sheet (or dialog) it is called in the way a swipe or a click outside does: it goes
 * out with its animation, and only then does what was asked. Every key inside one that ends it
 * (Copy, Delete, a pick from a list) goes through this: flipping the flag that shows it takes it
 * off the screen in one frame, which is how they came in softly and left with none.
 * Outside of one (a page of the PC chat panel) there is nothing to close: the action runs at once.
 */
val LocalLeave = androidx.compose.runtime.staticCompositionLocalOf<(then: () -> Unit) -> Unit> { { then -> then() } }

/**
 * Butler's bottom sheet, the one shape and grip every sheet shares. Inside the PC chat panel
 * ([LocalSheetInline]) its content is the panel's page instead, with no sheet around it.
 * Its content closes it through [LocalLeave].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ButlerSheet(
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    sheetMaxWidth: androidx.compose.ui.unit.Dp = androidx.compose.material3.BottomSheetDefaults.SheetMaxWidth,
    /** The sheet's ground, when it is not the usual raised one. */
    containerColor: Color = Color.Unspecified,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (LocalSheetInline.current) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) { content() }
        return
    }
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val leaving = androidx.compose.runtime.remember { booleanArrayOf(false) }
    val leave: (() -> Unit) -> Unit = androidx.compose.runtime.remember(state) {
        { then ->
            // Once: a second press while it slides away asks for nothing more.
            if (!leaving[0]) {
                leaving[0] = true
                scope.launch { runCatching { state.hide() } }.invokeOnCompletion { then() }
            }
        }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = if (containerColor != Color.Unspecified) containerColor else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalLeave provides leave) { content() }
    }
}

/**
 * A dialog's way in and out: its card fades and grows the last few percent as it comes, and
 * goes the same way before it is taken down. [modifier] goes on the dialog's card; its keys
 * end it through [leave].
 */
@androidx.compose.runtime.Stable
class DialogMotion internal constructor(private val scope: kotlinx.coroutines.CoroutineScope) {
    internal val shown = androidx.compose.animation.core.Animatable(0f)
    private var leaving = false

    /** 0 while away, 1 once in: for a surface that moves with the card (its scrim, a slide). */
    val progress: Float get() = shown.value

    val modifier: Modifier = Modifier.graphicsLayer {
        val t = shown.value
        alpha = t
        val scale = 0.96f + 0.04f * t
        scaleX = scale
        scaleY = scale
    }

    fun leave(then: () -> Unit) {
        if (leaving) return
        leaving = true
        scope.launch {
            shown.animateTo(0f, com.cherry.butler.core.design.Motion.exit())
        }.invokeOnCompletion { then() }
    }
}

@Composable
fun rememberDialogMotion(): DialogMotion {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val motion = androidx.compose.runtime.remember { DialogMotion(scope) }
    androidx.compose.runtime.LaunchedEffect(motion) { motion.shown.animateTo(1f, com.cherry.butler.core.design.Motion.enter()) }
    return motion
}
