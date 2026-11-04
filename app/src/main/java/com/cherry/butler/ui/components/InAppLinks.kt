package com.cherry.butler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.cherry.butler.core.config.JanitorLink

/**
 * The link handler for everything under it: every link tapped (a creator's description, a bio,
 * a comment) passes here first. A Janitor character opens its page in Butler, a Janitor profile
 * the creator's page, and anything else goes on to the browser as before. Without it a
 * janitorai.com link leaves Butler for Janitor's own app. Provide it as [LocalUriHandler].
 */
@Composable
fun rememberInAppLinks(
    onCharacter: (String) -> Unit,
    onCreator: (String) -> Unit,
): UriHandler {
    val outside = LocalUriHandler.current
    val character by rememberUpdatedState(onCharacter)
    val creator by rememberUpdatedState(onCreator)
    return remember(outside) {
        object : UriHandler {
            override fun openUri(uri: String) {
                when (val link = JanitorLink.parse(uri)) {
                    is JanitorLink.Character -> character(link.id)
                    is JanitorLink.Creator -> creator(link.userId)
                    null -> runCatching { outside.openUri(uri) }
                }
            }
        }
    }
}
