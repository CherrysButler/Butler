package com.cherry.butler.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.proseStyle
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.generation.ChoicesService
import com.cherry.butler.ui.components.rememberSkeletonAlpha

/**
 * Four next moves for the user, under the last reply. They are the user's own words, so
 * they read in the prose face, each in the Story box the user's lines use; a tap sends it.
 */
@Composable
fun ChoicesPanel(
    state: ChoicesService.State,
    onPick: (String) -> Unit,
    onAgain: () -> Unit,
    onHide: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
        label = "choices",
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) { s ->
        when (s) {
            is ChoicesService.State.Loading -> Text(
                "Thinking up four choices…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(rememberSkeletonAlpha()).padding(vertical = 8.dp),
            )
            is ChoicesService.State.Failed -> TurnState("No choices. ${s.message}") {
                StateAction("Try again", onClick = onAgain, emphasis = true)
                StateAction("Hide", onClick = onHide)
            }
            is ChoicesService.State.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.choices.forEach { choice -> Choice(choice) { onPick(choice) } }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    StateAction("Other choices", onClick = onAgain)
                    Spacer(Modifier.width(4.dp))
                    StateAction("Hide", onClick = onHide)
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        RpText(text = text, style = proseStyle(), paragraphSpacing = 4.dp)
    }
}
