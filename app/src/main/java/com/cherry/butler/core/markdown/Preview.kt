package com.cherry.butler.core.markdown

private val HEADING = Regex("""^\s{0,3}#{1,6}\s*""", RegexOption.MULTILINE)
private val QUOTE = Regex("""^\s*>\s?""", RegexOption.MULTILINE)
private val ESCAPE = Regex("""\\(.)""")
private val MARKS = Regex("""[*_~`]+""")
private val SPACE = Regex("""\s+""")

/**
 * The first [max] characters as plain prose: a card preview has no room to render
 * markdown, and showing the raw markers ("## **_You live…") reads as a bug.
 *
 * Editor HTML is flattened to its text first (tags out, entities decoded). This is lossy
 * on purpose — underscores inside words go too — which is fine for a two-line glance
 * and never used for anything that gets sent back.
 */
fun String.plainPreview(max: Int = 240): String =
    (if (RichHtml.looksLikeHtml(this)) RichHtml.toPlainText(if (length > max * 4) substring(0, max * 4) else this) else this)
        .let { if (it.length > max) it.substring(0, max) else it }
        .replace(HEADING, "")
        .replace(QUOTE, "")
        .replace(ESCAPE, "$1")
        .replace(MARKS, "")
        .replace(SPACE, " ")
        .stripPersonaMarks()
        .trim()
