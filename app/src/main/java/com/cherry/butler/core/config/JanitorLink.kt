package com.cherry.butler.core.config

/**
 * A link to something on Janitor's website that Butler can open itself. Creators link other
 * characters and their own profile in descriptions; handed to the system, such a link opens
 * Janitor's own app (it claims janitorai.com), so Butler reads it first.
 *
 * - `https://janitorai.com/characters/{id}_character-{slug}` (or just `/characters/{id}`)
 * - `https://janitorai.com/profiles/{id}_profile-of-{name}` (or just `/profiles/{id}`)
 */
sealed interface JanitorLink {
    data class Character(val id: String) : JanitorLink
    data class Creator(val userId: String) : JanitorLink

    companion object {
        private val UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        private val LINK = Regex(
            """^(?:https?://)?(?:[a-z0-9-]+\.)*janitorai\.com(?::\d+)?/(characters|character|profiles|profile)/($UUID)(?:[_/?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

        /** What [url] points at, or null when it isn't a Janitor page Butler has. */
        fun parse(url: String): JanitorLink? {
            val m = LINK.matchEntire(url.trim()) ?: return null
            val id = m.groupValues[2].lowercase()
            return when (m.groupValues[1].lowercase()) {
                "characters", "character" -> Character(id)
                else -> Creator(id)
            }
        }
    }
}
