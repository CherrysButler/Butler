package com.cherry.butler.core.markdown

/**
 * The part of a creator's CSS that Butler can honour. Janitor lets creators style their
 * profile with a stylesheet (`style.custom_style`, put on the page as `<style id="custom-css">`)
 * and with `<style>` blocks inside their bio, aimed at the website's own page parts
 * (`.pp-cc-wrapper` is a character card, `.pp-uc-title` the name, and so on; Janitor lists them
 * in `profile-ai-supported-selectors`, read 2026-10-07).
 *
 * Butler's profile page isn't the website's, so the parts that exist on both are mapped
 * ([Part]) and these properties are read: `color`, `background-color` / `background` (its
 * colour), `border-color` / `border` (its colour), `border-radius` and `text-align`. Anything
 * else is ignored rather than guessed at. No Compose here, so it's tested as data.
 */
object CreatorCss {

    enum class Part { Page, Header, Name, Bio, Tabs, TabActive, Card, CardName, CardText, Follow, Followers }

    /** What a rule asks of one part. Colours are ARGB; null means "not set". */
    data class Look(
        val color: Long? = null,
        val background: Long? = null,
        val border: Long? = null,
        val radius: Float? = null,
        val align: RichHtml.Align? = null,
    ) {
        operator fun plus(later: Look) = Look(
            color = later.color ?: color,
            background = later.background ?: background,
            border = later.border ?: border,
            radius = later.radius ?: radius,
            align = later.align ?: align,
        )

        val isEmpty: Boolean get() = this == Look()
    }

    private val PARTS: Map<String, Part> = buildMap {
        fun map(part: Part, vararg selectors: String) = selectors.forEach { put(it, part) }
        map(Part.Page, "body", "html", ":root", ".pp-page-background", ".profile-page-background", ".profile-page-container", ".profile-page-flex")
        map(Part.Header, ".pp-uc-background", ".profile-uc-background", ".profile-info-wrapper-box", ".pp-top-header")
        map(Part.Name, ".pp-uc-title", ".profile-title-heading")
        map(Part.Bio, ".pp-uc-about-me", ".profile-about-me")
        map(Part.Tabs, "#profile-tabs", ".pp-tabs-wrapper", ".profile-tabs", ".pp-tabs-button", ".profile-tabs-button", ".profile-tabs-wrapper")
        map(Part.TabActive, ".pp-tabs-indicator", ".profile-tabs-indicator")
        map(Part.Card, ".pp-cc-wrapper", ".profile-character-card-wrapper", ".profile-character-card-box")
        map(Part.CardName, ".pp-cc-name", ".profile-character-card-name-box")
        map(Part.CardText, ".pp-cc-description", ".profile-character-card-description-box", ".profile-character-card-description-markdown-container")
        map(Part.Follow, ".pp-uc-follow-button", ".profile-uc-follow-button")
        map(Part.Followers, ".pp-uc-followers-count", ".profile-followers-count")
    }

    private val COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    private val RULE = Regex("""([^{}]+)\{([^{}]*)\}""")
    private val COLOR_TOKEN = Regex("""#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|\b[a-zA-Z]+\b""")

    /** Every rule in [css] that lands on a part Butler draws, merged per part (later rules win). */
    fun parse(css: String?): Map<Part, Look> {
        if (css.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<Part, Look>()
        for ((selectors, decls) in rules(css)) {
            val look = declarations(decls).takeUnless { it.isEmpty } ?: continue
            for (selector in selectors) {
                val part = partOf(selector) ?: continue
                out[part] = (out[part] ?: Look()) + look
            }
        }
        return out
    }

    /**
     * Rules in a bio's own `<style>` blocks aimed at the page around it rather than at anything
     * inside it (a class or id the bio doesn't use, or `body`): those are what the website's
     * container shows, so Butler gives them to the bio as a whole.
     */
    fun pageLook(html: String, css: String): Look {
        var look = Look()
        for ((selectors, decls) in rules(css)) {
            if (selectors.any { aimsOutside(it, html) }) look += declarations(decls)
        }
        return look
    }

    private fun rules(css: String): List<Pair<List<String>, String>> =
        RULE.findAll(COMMENT.replace(css, "")).map { m ->
            m.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() } to m.groupValues[2]
        }.toList()

    /** The part a selector's last compound names; states (`:hover`) and the like don't count. */
    private fun partOf(selector: String): Part? {
        val last = selector.split(Regex("""\s+|>|\+|~""")).last { it.isNotBlank() }
        if (last.contains("::") || (last.contains(':') && last != ":root")) return null
        val key = Regex("""[.#][A-Za-z0-9_-]+|^[a-z]+$|^:root$""").findAll(last).map { it.value }.firstOrNull { it in PARTS } ?: return null
        return PARTS[key]
    }

    private fun aimsOutside(selector: String, html: String): Boolean {
        val last = selector.split(Regex("""\s+|>|\+|~""")).last { it.isNotBlank() }
        if (last.contains(':') && last != ":root") return false
        if (last == "body" || last == "html" || last == ":root") return true
        val names = Regex("""[.#]([A-Za-z0-9_-]+)""").findAll(last).map { it.groupValues[1] }.toList()
        return names.isNotEmpty() && names.none { html.contains(it) }
    }

    fun declarations(block: String): Look {
        var look = Look()
        for (decl in block.split(';')) {
            val prop = decl.substringBefore(':', "").trim().lowercase()
            val value = decl.substringAfter(':', "").replace("!important", "").trim()
            if (prop.isEmpty() || value.isEmpty()) continue
            look = when (prop) {
                "color" -> look.copy(color = colorIn(value))
                "background-color", "background" -> colorIn(value)?.let { look.copy(background = it) } ?: look
                "border-color", "border" -> colorIn(value)?.let { look.copy(border = it) } ?: look
                "border-radius" -> value.substringBefore(' ').removeSuffix("px").toFloatOrNull()?.let { look.copy(radius = it) } ?: look
                "text-align" -> when (value.lowercase()) {
                    "center" -> look.copy(align = RichHtml.Align.Center)
                    "right", "end" -> look.copy(align = RichHtml.Align.End)
                    "left", "start", "justify" -> look.copy(align = RichHtml.Align.Start)
                    else -> look
                }
                else -> look
            }
        }
        return look
    }

    /** The first colour in a value (`1px solid #f00`, `linear-gradient(#000, …)`, `rgb(…)`). */
    private fun colorIn(value: String): Long? =
        COLOR_TOKEN.findAll(value).firstNotNullOfOrNull { RichHtml.parseColor(it.value) }
}
