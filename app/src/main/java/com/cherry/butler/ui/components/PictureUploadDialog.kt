package com.cherry.butler.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cherry.butler.core.data.PictureUpload.MediaStage
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion

/**
 * A picture on its way into a bio or a description: Uploading, then Janitor's check
 * (Moderating), then Done, each with its own mark. The step under way breathes in Butler red,
 * a finished one turns green; a step that fails turns red and says why, with Close. On Done the
 * caller closes it.
 */
@Composable
fun PictureUploadDialog(
    stage: MediaStage,
    failure: String?,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (failure != null) onClose() },
        properties = DialogProperties(dismissOnBackPress = failure != null, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .card()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (failure != null) 8.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Adding the picture", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center,
            ) {
                MediaStage.entries.forEachIndexed { i, step ->
                    if (i > 0) Connector(passed = stage.ordinal >= step.ordinal && !(failure != null && stage == step))
                    Step(step, state = stepState(step, stage, failure != null))
                }
            }
            Text(
                failure ?: when (stage) {
                    MediaStage.Uploading -> "Sending it to Janitor…"
                    MediaStage.Moderating -> "Janitor looks at every picture before it shows. Usually a few seconds."
                    MediaStage.Done -> "In. It sits where your cursor was."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (failure != null) ButlerTheme.colors.danger else ButlerTheme.colors.textMed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (failure != null) {
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(top = 6.dp)) { Text("Close") }
            }
        }
    }
}

private enum class StepState { Waiting, Active, Done, Failed }

private fun stepState(step: MediaStage, now: MediaStage, failed: Boolean): StepState = when {
    step.ordinal < now.ordinal -> StepState.Done
    step == now && now == MediaStage.Done -> StepState.Done
    step == now -> if (failed) StepState.Failed else StepState.Active
    else -> StepState.Waiting
}

@Composable
private fun Step(step: MediaStage, state: StepState) {
    val ink by animateColorAsState(
        when (state) {
            StepState.Waiting -> ButlerTheme.colors.textLow
            StepState.Active -> MaterialTheme.colorScheme.primary
            StepState.Done -> ButlerTheme.colors.success
            StepState.Failed -> ButlerTheme.colors.danger
        },
        animationSpec = tween(Motion.SHORT, easing = Motion.EaseOut),
        label = "step ink",
    )
    val tile = when (state) {
        StepState.Active -> MaterialTheme.colorScheme.primaryContainer
        StepState.Failed -> ButlerTheme.colors.danger.copy(alpha = 0.14f)
        else -> ButlerTheme.colors.surfaceHigh
    }
    // The step under way breathes, on the same slow breath as Butler's skeletons.
    val breath = if (state == StepState.Active) rememberSkeletonAlpha() else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(tile),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(26.dp).alpha(breath)) {
                when (step) {
                    MediaStage.Uploading -> drawUpload(ink)
                    MediaStage.Moderating -> drawEye(ink)
                    MediaStage.Done -> drawCheck(ink)
                }
            }
        }
        Text(
            when (step) {
                MediaStage.Uploading -> "Uploading"
                MediaStage.Moderating -> "Moderating"
                MediaStage.Done -> "Done"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (state == StepState.Waiting) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The line between two steps, lit once the one after it has begun. */
@Composable
private fun Connector(passed: Boolean) {
    val ink by animateColorAsState(
        if (passed) ButlerTheme.colors.success else ButlerTheme.colors.rule,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.EaseOut),
        label = "connector",
    )
    Box(Modifier.padding(top = 25.dp).width(22.dp).height(2.dp).clip(MaterialTheme.shapes.extraSmall).background(ink))
}

// ---- the marks, drawn on a 24-unit grid with one 2-unit stroke -------------------------------

private fun DrawScope.unit(): Float = size.minDimension / 24f

private fun DrawScope.line(ink: Color, path: Path) =
    drawPath(path, ink, style = Stroke(width = 2f * unit(), cap = StrokeCap.Round, join = StrokeJoin.Round))

/** An arrow rising out of a tray. */
private fun DrawScope.drawUpload(ink: Color) {
    val u = unit()
    line(ink, Path().apply {
        moveTo(4 * u, 15 * u); lineTo(4 * u, 18 * u)
        quadraticTo(4 * u, 20 * u, 6 * u, 20 * u); lineTo(18 * u, 20 * u)
        quadraticTo(20 * u, 20 * u, 20 * u, 18 * u); lineTo(20 * u, 15 * u)
    })
    line(ink, Path().apply { moveTo(12 * u, 15 * u); lineTo(12 * u, 4 * u) })
    line(ink, Path().apply { moveTo(7.5f * u, 8.5f * u); lineTo(12 * u, 4 * u); lineTo(16.5f * u, 8.5f * u) })
}

/** An eye: Janitor looking the picture over. */
private fun DrawScope.drawEye(ink: Color) {
    val u = unit()
    line(ink, Path().apply {
        moveTo(2.5f * u, 12 * u)
        quadraticTo(12 * u, 3 * u, 21.5f * u, 12 * u)
        quadraticTo(12 * u, 21 * u, 2.5f * u, 12 * u)
        close()
    })
    drawCircle(ink, radius = 3f * u, center = Offset(12 * u, 12 * u), style = Stroke(width = 2f * u))
}

/** A check. */
private fun DrawScope.drawCheck(ink: Color) {
    val u = unit()
    line(ink, Path().apply { moveTo(5 * u, 12.5f * u); lineTo(10 * u, 17 * u); lineTo(19 * u, 7.5f * u) })
}
