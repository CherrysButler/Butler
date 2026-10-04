package com.cherry.butler.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.Pill
import com.cherry.butler.ui.components.rememberSkeletonAlpha

/** Where a "Make default" swap is. One pop-up carries it from the ask to the end. */
data class SwapFlow(
    val newName: String,
    val oldName: String,
    val stage: Stage = Stage.Confirm,
    /** Picture, names, default: the three steps the pop-up lists. */
    val steps: List<StepState> = List(3) { StepState.Waiting },
    /** Janitor's own words when it refused the picture, or why the swap stopped. */
    val reason: String? = null,
) {
    enum class Stage { Confirm, Running, PictureRefused, Done, Failed }
    enum class StepState { Waiting, Working, Done, Skipped, Failed }
}

@Composable
fun SwapDialog(
    flow: SwapFlow,
    onConfirm: () -> Unit,
    onRevert: () -> Unit,
    onContinueWithoutPicture: () -> Unit,
    onClose: () -> Unit,
) {
    val stage = flow.stage
    val settled = stage == SwapFlow.Stage.Confirm || stage == SwapFlow.Stage.Done || stage == SwapFlow.Stage.Failed
    AlertDialog(
        onDismissRequest = { if (settled) onClose() },
        // Wider than the platform default: three steps with notes need the measure.
        modifier = Modifier.padding(horizontal = 20.dp),
        properties = DialogProperties(
            dismissOnBackPress = settled,
            dismissOnClickOutside = stage == SwapFlow.Stage.Confirm,
            usePlatformDefaultWidth = false,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            AnimatedContent(
                targetState = if (stage == SwapFlow.Stage.Done) "${flow.newName} is your default" else "Make ${flow.newName} your default?",
                transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                label = "swap-title",
            ) { title ->
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column {
                Text(
                    text = buildAnnotatedString {
                        append("JanitorAI doesn't normally let you give the ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) { append("default") }
                        append(" property to another persona, so we found a workaround by doing it manually:")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ButlerTheme.colors.textMed,
                )
                Spacer(Modifier.height(14.dp))
                Step(
                    glyph = SwapGlyph.Pictures,
                    text = "We will swap the pictures between ${flow.oldName} and ${flow.newName}.",
                    note = "This will change your pfp.",
                    state = flow.steps[0],
                    skipped = "${flow.newName} has no picture, so this was skipped.",
                )
                Step(
                    glyph = SwapGlyph.Names,
                    text = "Swap names and pronouns!",
                    note = "This will also affect your main profile.",
                    state = flow.steps[1],
                )
                Step(
                    glyph = SwapGlyph.Crown,
                    text = "Your default persona is swapped. Change back any time.",
                    note = null,
                    state = flow.steps[2],
                )
                AnimatedVisibility(
                    visible = stage == SwapFlow.Stage.PictureRefused || stage == SwapFlow.Stage.Failed,
                    enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
                    exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit()),
                ) {
                    Problem(
                        title = if (stage == SwapFlow.Stage.PictureRefused) "Janitor refused the picture" else "The swap stopped",
                        body = flow.reason.orEmpty(),
                    )
                }
            }
        },
        confirmButton = {
            when (stage) {
                SwapFlow.Stage.Confirm -> Action("Swap", onConfirm, strong = true)
                SwapFlow.Stage.PictureRefused -> Action("Continue without a picture", onContinueWithoutPicture, strong = true)
                SwapFlow.Stage.Done, SwapFlow.Stage.Failed -> Action("Done", onClose, strong = true)
                SwapFlow.Stage.Running -> {}
            }
        },
        dismissButton = {
            when (stage) {
                SwapFlow.Stage.Confirm -> Action("Cancel", onClose)
                SwapFlow.Stage.PictureRefused -> Action("Revert", onRevert)
                else -> {}
            }
        },
    )
}

/**
 * One line of the plan. Its glyph is the progress: quiet while waiting, breathing red while
 * it runs (the app's one slow breath, never a spinner), a tick when done, a cross on failure.
 */
@Composable
private fun Step(glyph: SwapGlyph, text: String, note: String?, state: SwapFlow.StepState, skipped: String? = null) {
    val colors = ButlerTheme.colors
    val behind = MaterialTheme.colorScheme.surfaceContainer
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
            label = "step-mark",
        ) { s ->
            when (s) {
                SwapFlow.StepState.Working ->
                    Glyph(glyph, MaterialTheme.colorScheme.primary, behind, Modifier.alpha(rememberSkeletonAlpha()))
                SwapFlow.StepState.Done -> Glyph(SwapGlyph.Tick, colors.success, behind)
                SwapFlow.StepState.Failed -> Glyph(SwapGlyph.Cross, colors.danger, behind)
                SwapFlow.StepState.Skipped -> Glyph(glyph, colors.textLow, behind)
                SwapFlow.StepState.Waiting -> Glyph(glyph, colors.textMed, behind)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            val line = if (state == SwapFlow.StepState.Skipped && skipped != null) skipped else note
            if (line != null) {
                Text(
                    text = buildAnnotatedString {
                        if (line == note) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.textMed)) { append("Note: ") }
                        }
                        append(line)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** The house error card: danger at 14%, a danger title naming the cause, the detail beneath. */
@Composable
private fun Problem(title: String, body: String) {
    val colors = ButlerTheme.colors
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(colors.danger.copy(alpha = 0.14f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = colors.danger)
        if (body.isNotBlank()) {
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textMed, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit, strong: Boolean = false) {
    TextButton(onClick = onClick, shape = Pill) {
        Text(
            label,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
