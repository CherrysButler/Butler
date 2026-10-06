package com.cherry.butler.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.ui.components.card
import java.net.URI

/**
 * A markdown picture in a chat. Janitor's own image host loads straight away; any other
 * host waits for a tap, because fetching it hands that host the reader's IP address (and
 * that they are reading this chat now). Anything that isn't http(s) never loads.
 */
@Composable
fun ChatImage(url: String, alt: String, modifier: Modifier = Modifier) {
    val uri = remember(url) { runCatching { URI(url) }.getOrNull() }
    val scheme = uri?.scheme?.lowercase()
    val host = uri?.host?.lowercase()
    if (host == null || (scheme != "https" && scheme != "http")) {
        if (alt.isNotBlank()) Text(text = alt, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow, modifier = modifier)
        return
    }
    val trusted = scheme == "https" && host == JANITOR_IMAGE_HOST
    var allowed by rememberSaveable(url) { mutableStateOf(trusted) }
    if (allowed) {
        AsyncImage(
            model = url,
            contentDescription = alt.ifBlank { null },
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .card(color = ButlerTheme.colors.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (alt.isNotBlank()) "Image: $alt" else "Image from another site",
            style = MaterialTheme.typography.bodyMedium,
            color = ButlerTheme.colors.textMed,
        )
        Text(
            text = "This picture is hosted on $host, not Janitor. Loading it shows $host your IP address.",
            style = MaterialTheme.typography.bodySmall,
            color = ButlerTheme.colors.textLow,
        )
        TextButton(onClick = { allowed = true }, shape = Pill) { Text("Load image") }
    }
}

private val JANITOR_IMAGE_HOST = URI(JanitorConfig.MEDIA_BASE).host
