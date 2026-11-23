package com.cherry.butler.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * Butler's dialogs. A dialog used to stand there in one frame and be gone in one; these come
 * with their card fading and growing its last few percent (DialogMotion, Motion's own timing)
 * and go the same way. A click outside, Back and Esc go out through it; a key inside the dialog
 * that ends it goes out through LocalLeave:  `val leave = LocalLeave.current … leave(onConfirm)`.
 */

/** Material's alert dialog, coming and going softly. Same parameters. */
@Composable
fun ButlerAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    properties: DialogProperties = DialogProperties(),
) {
    val motion = rememberDialogMotion()
    val leave: (() -> Unit) -> Unit = motion::leave
    AlertDialog(
        onDismissRequest = { motion.leave(onDismissRequest) },
        confirmButton = { CompositionLocalProvider(LocalLeave provides leave) { confirmButton() } },
        modifier = modifier.then(motion.modifier),
        dismissButton = dismissButton?.let { button -> { CompositionLocalProvider(LocalLeave provides leave) { button() } } },
        icon = icon,
        title = title,
        text = text?.let { body -> { CompositionLocalProvider(LocalLeave provides leave) { body() } } },
        shape = shape,
        containerColor = containerColor,
        properties = properties,
    )
}

/** A dialog with a card of its own making, coming and going softly. */
@Composable
fun ButlerDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val motion = rememberDialogMotion()
    val leave: (() -> Unit) -> Unit = motion::leave
    Dialog(onDismissRequest = { motion.leave(onDismissRequest) }, properties = properties) {
        CompositionLocalProvider(LocalLeave provides leave) {
            Box(motion.modifier) { content() }
        }
    }
}

/** The colour Butler's dialogs sit on, for the ones that name it. */
val dialogGround: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/**
 * [action], done once the sheet or dialog this is in has gone out. For a key that ends it:
 * `TextButton(onClick = leaving(onDismiss))`.
 */
@Composable
fun leaving(action: () -> Unit): () -> Unit {
    val leave = LocalLeave.current
    return { leave(action) }
}
