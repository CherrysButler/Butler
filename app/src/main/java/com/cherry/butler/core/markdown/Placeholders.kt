package com.cherry.butler.core.markdown

import com.cherry.butler.core.data.remote.dto.PronounsDto

private val PLACEHOLDER = Regex("""\{\{\s*(user|char|sub|obj|poss_p|poss|ref)\s*\}\}""", RegexOption.IGNORE_CASE)

/**
 * Brackets the persona's name where it fills `{{user}}`, so the renderers can draw it in
 * Butler red. Private-use characters: they never occur in real text, survive markdown and
 * HTML parsing as ordinary characters, and are consumed by [RpMarkdown] and [RichHtml].
 * Anything that shows text without one of those parsers must use [stripPersonaMarks].
 */
const val PERSONA_OPEN = ''
const val PERSONA_CLOSE = ''

/**
 * Janitor's template names, filled the way the official client fills them:
 * `{{user}}` becomes the persona's name and `{{char}}` the character's; `{{sub}}`,
 * `{{obj}}`, `{{poss}}`, `{{poss_p}}` and `{{ref}}` the persona's pronouns (they/them when
 * it has none). Any casing, optional inner spaces. A missing persona reads as "You", never
 * as a raw brace.
 *
 * With [markUser] the persona's name is bracketed by [PERSONA_OPEN] / [PERSONA_CLOSE] for
 * the renderers to colour; leave it off for anything copied, edited or shown raw.
 */
fun String.fillNames(user: String?, char: String?, markUser: Boolean = false, pronouns: PronounsDto? = null): String {
    if (!contains("{{")) return this
    val userName = user?.takeIf { it.isNotBlank() } ?: "You"
    val shownUser = if (markUser) "$PERSONA_OPEN$userName$PERSONA_CLOSE" else userName
    val charName = char?.takeIf { it.isNotBlank() }
    return PLACEHOLDER.replace(this) { m ->
        when (m.groupValues[1].lowercase()) {
            "user" -> shownUser
            "char" -> charName ?: m.value
            "sub" -> pronouns?.subjective ?: "they"
            "obj" -> pronouns?.objective ?: "them"
            "poss" -> pronouns?.possessive ?: "their"
            "poss_p" -> pronouns?.possessivePronoun ?: pronouns?.possessive?.let { "${it}s" } ?: "theirs"
            else -> pronouns?.reflexive ?: "themselves"
        }
    }
}

fun String.stripPersonaMarks(): String =
    if (indexOf(PERSONA_OPEN) < 0 && indexOf(PERSONA_CLOSE) < 0) this
    else filterNot { it == PERSONA_OPEN || it == PERSONA_CLOSE }
