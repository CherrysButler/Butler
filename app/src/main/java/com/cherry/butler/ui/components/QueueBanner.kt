package com.cherry.butler.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.network.JanitorQueue
import kotlinx.coroutines.delay

/**
 * A quiet slip at the top of the app while Butler stands in Janitor's queue: what's happening,
 * Janitor's own estimate, and that Butler is keeping the place. It goes by itself the moment a
 * call is let through, or once nothing has met the queue for a minute. Never an error screen.
 */
@Composable
fun QueueBanner(modifier: Modifier = Modifier) {
    val waiting by JanitorQueue.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waiting != null) {
        // Once a second while in line: the time waited counts up.
        while (waiting != null) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val shown = waiting?.takeIf { now - it.lastSeen < JanitorQueue.STALE_MS }
    Box(modifier.statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = shown != null,
            enter = slideInVertically(tween(Motion.MEDIUM, easing = Motion.EaseOut)) { -it } + fadeIn(tween(Motion.MEDIUM)),
            exit = slideOutVertically(tween(Motion.SHORT)) { -it } + fadeOut(tween(Motion.SHORT)),
        ) {
            val breath = rememberSkeletonAlpha()
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 16.dp)
                    .card(shape = MaterialTheme.shapes.large, color = ButlerTheme.colors.surfaceHigh, lift = com.cherry.butler.core.design.Lift.Float)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(9.dp).alpha(breath).background(MaterialTheme.colorScheme.primary, CircleShape))
                Column {
                    Text(
                        "You're in Janitor's queue",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOfNotNull(
                            shown?.let { "Waiting ${waited(now - it.since)}" },
                            shown?.estimate?.let { "estimated $it" },
                        ).joinToString(" · ") + "\nButler keeps your place and carries on by itself",
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.textMed,
                    )
                }
            }
        }
    }
}

/** [ms] as minutes and seconds, "1:40". */
private fun waited(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
