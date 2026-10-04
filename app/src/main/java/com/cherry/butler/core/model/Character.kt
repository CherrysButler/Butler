package com.cherry.butler.core.model

import com.cherry.butler.core.config.JanitorConfig
import java.util.Locale

/**
 * A character as the UI needs it — the browse card's worth of fields, not the whole
 * wire object. Keeping this separate from `CharacterDto` means a server field rename
 * breaks one mapping function instead of every composable.
 */
data class Character(
    val id: String,
    val name: String,
    val description: String,
    /** Bare filename from the API; use [avatarUrl] to render it. */
    val avatar: String?,
    val creatorName: String,
    val creatorVerified: Boolean,
    val creatorPlusBadge: Boolean,
    val chatCount: Long,
    val messageCount: Long,
    val isNsfw: Boolean,
    val isImageNsfw: Boolean,
    val tagNames: List<String>,
    val customTags: List<String>,
    /** The description as a short plain line for cards, worked out off the main thread. */
    val blurb: String = "",
    val totalTokens: Int = 0,
    val isProxyEnabled: Boolean = false,
    /** The creator's username colour as a CSS hex, if they set one. */
    val creatorColor: String? = null,
) {
    val avatarUrl: String? get() = JanitorConfig.avatarUrl(avatar)
}

/**
 * Compact counts for list cards: 861540 reads as "861.5k" in the width available.
 *
 * Locale is pinned to US so the decimal separator stays a dot — a comma here would
 * collide with the separators used between stats on the card.
 */
fun Long.compactCount(): String = when {
    this >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", this / 1_000_000_000.0)
    this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
    this >= 1_000 -> String.format(Locale.US, "%.1fk", this / 1_000.0)
    else -> toString()
}
