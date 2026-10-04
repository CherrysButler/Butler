package com.cherry.butler.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.network.ApiError

/**
 * The shared vocabulary for "nothing to show yet": one composition for empty, error and
 * offline so every screen reads the same. The extra slot carries the one action that
 * actually resolves the state — never a generic Retry for a terminal error.
 */
@Composable
fun CenteredMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ButlerTheme.colors.textLow,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = ButlerTheme.colors.textMed,
            textAlign = TextAlign.Center,
        )
        extra()
    }
}

/**
 * A red-tinted card inserted where the problem is, naming it.
 */
@Composable
fun InlineErrorCard(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(color = ButlerTheme.colors.danger.copy(alpha = 0.14f))
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlateText(text = error.userTitle(), level = PlateLevel.Small, color = ButlerTheme.colors.danger)
            Spacer(Modifier.height(4.dp))
            Text(
                text = error.userMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textMed,
            )
        }
        if (error.isRetryable()) {
            TextButton(onClick = onRetry, shape = MaterialTheme.shapes.small) {
                Text("Retry", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * The one loading motion in the app: a slow breath on the placeholder blocks. It says
 * "still working" without a spinner in the middle of content, and the whole skeleton
 * shares a phase so it reads as one surface, not a field of blinking parts.
 */
@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return alpha
}

/** A quiet block standing in for text or an image that is on its way. */
@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
    alpha: Float = rememberSkeletonAlpha(),
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(radius))
            .background(ButlerTheme.colors.surfaceHigh),
    )
}

/**
 * The typed error is what keeps these honest: a Retry only appears where retrying can
 * change the outcome, so the user is never invited to hammer a terminal failure.
 */
fun Throwable.isRetryable(): Boolean = (this as? ApiError)?.retryable ?: true

fun Throwable.userTitle(): String = when (this) {
    is ApiError.Network -> "You're offline"
    is ApiError.RateLimited -> "Slow down a moment"
    is ApiError.Server -> "Janitor is having trouble"
    is ApiError.Unauthorized -> "Session expired"
    is ApiError.Serialization -> "Unexpected response"
    else -> "Couldn't load this"
}

fun Throwable.userMessage(): String = when (this) {
    is ApiError.Network -> "Check your connection. Anything already synced stays readable."
    is ApiError.RateLimited -> "Too many requests in a row. Give it a few seconds."
    is ApiError.Server -> "Janitor's servers returned an error. This usually clears on its own."
    is ApiError.Unauthorized -> "Sign in again to keep going."
    is ApiError.Forbidden -> "This isn't available on your account."
    is ApiError.Serialization -> "The server sent something we couldn't read. This is a bug in Butler."
    is ApiError.Api -> serverMessage ?: "Request failed ($code)."
    else -> "Something went wrong."
}
