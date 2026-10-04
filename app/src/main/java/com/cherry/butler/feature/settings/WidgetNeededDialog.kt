package com.cherry.butler.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.cherry.butler.core.background.BackgroundReplies
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill

/**
 * Turning on background replies on a phone that freezes apps (Transsion's): says what the
 * phone does and that the widget is the fix. [manual]: the launcher can't place widgets on
 * request, so the steps are spelled out instead.
 */
@Composable
fun WidgetNeededDialog(manual: Boolean, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val maker = BackgroundReplies.maker
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Your phone is an $maker", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                text = if (manual) {
                    "Touch and hold an empty spot on your home screen, tap Widgets, then drag Butler out."
                } else {
                    "$maker phones freeze apps a few seconds after you leave them, which cuts replies off. " +
                        "Butler needs its widget on your home screen to keep running."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
            )
        },
        confirmButton = {
            TextButton(onClick = if (manual) onDismiss else onAdd, shape = Pill) {
                Text(if (manual) "Got it" else "Add widget", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (manual) null else {
            { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
        },
    )
}
