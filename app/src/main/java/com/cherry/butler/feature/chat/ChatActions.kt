package com.cherry.butler.feature.chat

import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.userMessage

/**
 * What a long press on a line offers: copy it, rewrite it, or cut the log from it. The
 * same ruled sheet as the rest of the app, rows a thumb's height, the destructive one in
 * red and last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineActionsSheet(
    preview: String,
    canEdit: Boolean,
    canDelete: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onBranch: (() -> Unit)? = null,
    /** A reply's 1–5 stars, when it can be rated; [rating] is the saved one. */
    rating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                text = preview.lines().joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HairlineRule(color = ButlerTheme.colors.rule)
            if (onRate != null) {
                Stars(rating, onRate)
                HairlineRule(color = ButlerTheme.colors.outlineFaint)
            }
            ActionRow(Icons.Rounded.ContentCopy, "Copy", onClick = onCopy)
            if (canEdit) ActionRow(Icons.Rounded.Edit, "Edit", onClick = onEdit)
            if (onBranch != null) ActionRow(Icons.Rounded.CallSplit, "Branch from here", onClick = onBranch)
            if (canDelete) ActionRow(Icons.Rounded.DeleteOutline, "Delete from here", tint = ButlerTheme.colors.danger, onClick = onDelete)
        }
    }
}

/** Five stars across the sheet; the tapped one is the rating, sent at once. */
@Composable
private fun Stars(saved: Int?, onRate: (Int) -> Unit) {
    var chosen by remember(saved) { mutableStateOf(saved ?: 0) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Rate this reply",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        (1..5).forEach { n ->
            IconButton(onClick = { chosen = n; onRate(n) }, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (n <= chosen) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = "$n star" + if (n == 1) "" else "s",
                    tint = if (n <= chosen) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint)
    }
}

/**
 * The one question that interrupts: deleting reaches Janitor and cannot be undone, and it
 * takes everything after the line with it.
 */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Delete from here",
    body: String? = null,
    confirmLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Text(
                text = body ?: if (count <= 1) {
                    "This line will be removed from the chat on Janitor too. This can't be undone."
                } else {
                    "This line and the ${count - 1} after it will be removed from the chat on Janitor too. This can't be undone."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = ButlerTheme.colors.textMed,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shape = com.cherry.butler.core.design.Pill) {
                Text(confirmLabel ?: if (count <= 1) "Delete" else "Delete $count", color = ButlerTheme.colors.danger, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = com.cherry.butler.core.design.Pill) {
                Text("Keep", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

/**
 * A line opened for rewriting, in place. The field takes the line's own frame and
 * width; Save is the red key, Cancel the quiet one. A failed save keeps every word and
 * says why under the field.
 */
@Composable
fun LineEditor(
    text: String,
    onChange: (String) -> Unit,
    status: EditStatus,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The caret opens at the end of the line: an edit is usually an addition.
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (value.text != text) value = value.copy(text = text, selection = TextRange(text.length))
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineFrame(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                .background(ButlerTheme.colors.surfaceHigh)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onChange(it.text)
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                enabled = !status.saving,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        status.error?.let { error ->
            Text(
                text = "Not saved. ${error.userMessage()}",
                style = MaterialTheme.typography.labelSmall,
                color = ButlerTheme.colors.danger,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !status.saving, shape = MaterialTheme.shapes.small) {
                Text("Cancel", color = ButlerTheme.colors.textMed)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .background(
                        if (text.isBlank() || status.saving) ButlerTheme.colors.surfaceHigh else MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.small,
                    )
                    .clickable(enabled = text.isNotBlank() && !status.saving, onClick = onSave)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (status.saving) "Saving…" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (text.isBlank() || status.saving) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/**
 * Under the last reply: which variant this is, in the dot face, with a step either way.
 * Past the last variant the step becomes a new reply. Continue sits at the far end.
 */
@Composable
fun VariantBar(
    index: Int,
    count: Int,
    canSwipe: Boolean,
    busy: Boolean,
    canContinue: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNew: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    /** null hides it: not on JLLM, not while busy, not when the choices are already up. */
    onChoices: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canSwipe) {
            IconButton(onClick = onPrevious, enabled = !busy && index > 0, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous reply",
                    tint = if (!busy && index > 0) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.outlineFaint,
                )
            }
            PlateText(
                text = "${index + 1}/$count",
                level = PlateLevel.Small,
                color = if (count > 1) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
            )
            val atEnd = index >= count - 1
            IconButton(onClick = if (atEnd) onNew else onNext, enabled = !busy, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (atEnd) Icons.Rounded.Refresh else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (atEnd) "New reply" else "Next reply",
                    tint = when {
                        busy -> ButlerTheme.colors.outlineFaint
                        atEnd -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(if (atEnd) 20.dp else 24.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onChoices != null) {
            StateAction(label = "Choices", onClick = onChoices)
        }
        if (canContinue) {
            StateAction(label = "Continue", onClick = onContinue, emphasis = true)
        }
    }
}

/**
 * "New reply", as ChatGPT's retry: try again as it is, or say what to change first.
 * The field is optional; quick picks fill it. Sent only with this one request.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RetrySheet(onRetry: (String) -> Unit, onDismiss: () -> Unit) {
    var guidance by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The keyboard comes up once the sheet has finished rising, not ahead of it.
    LaunchedEffect(sheetState) {
        androidx.compose.runtime.snapshotFlow { sheetState.currentValue }
            .first { it == androidx.compose.material3.SheetValue.Expanded }
        // "Expanded" is reported before the slide settles; wait until it stops moving.
        var last = Float.NaN
        var still = 0
        while (still < 3) {
            androidx.compose.runtime.withFrameNanos { }
            val now = runCatching { sheetState.requireOffset() }.getOrDefault(Float.NaN)
            still = if (!now.isNaN() && kotlin.math.abs(now - last) < 0.5f) still + 1 else 0
            last = now
        }
        runCatching { focus.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("New reply", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Say what to change, or leave it empty to simply try again.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(ButlerTheme.colors.surfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = guidance,
                    onValueChange = { guidance = it.take(300) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { onRetry(guidance) }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (guidance.isEmpty()) Text("e.g. Be more enthusiastic", style = MaterialTheme.typography.bodyLarge, color = ButlerTheme.colors.textLow)
                        inner()
                    },
                )
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QUICK_GUIDES.forEach { pick ->
                    Text(
                        text = pick,
                        style = MaterialTheme.typography.labelLarge,
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.small)
                            .clickable { guidance = pick }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            androidx.compose.material3.Button(
                onClick = { onRetry(guidance) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 50.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(if (guidance.isBlank()) Icons.Rounded.Refresh else Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (guidance.isBlank()) "Try again" else "Rewrite with this", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}

private val QUICK_GUIDES = listOf(
    "Shorter", "Longer", "More dialogue", "Less narration", "Slow down", "More emotional", "Take it somewhere new",
)
