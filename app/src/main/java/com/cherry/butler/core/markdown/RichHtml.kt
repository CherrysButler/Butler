package com.cherry.butler.core.markdown

/**
 * Janitor's character descriptions are the output of a rich-text editor: `<p>` with
 * `text-align`, `<span style="color: rgb(…)">`, strong/em/u/s, `<mark>`, `<br>`, `<hr>`,
 * headings, lists, links, and full-width `<img>` banners from Janitor's media host.
 *
 * This parses that subset into blocks and inline runs. It is deliberately tolerant: an
 * unknown tag is dropped and its text kept, an unclosed tag closes at the next block,
 * entities are decoded, and whitespace collapses the way a browser would. It has no
 * Compose dependency so it is unit-tested as data.
 */
object RichHtml {

    sealed class Block {
        data class Paragraph(
            val text: String,
            val runs: List<Run>,
            val align: Align = Align.Start,
            /** 0 for body text, 1–6 for headings. */
            val heading: Int = 0,
            /**
             * The stem of a Janitor stylesheet class the creator applied in the editor
             * (`_appModalBadge_xxqvx_134` → `appModalBadge`). The hash changes per build;
             * the stem is what the official client styles by, and so do we.
             */
            val role: String? = null,
        ) : Block()

        data class ListItem(val text: String, val runs: List<Run>, val ordered: Boolean, val index: Int) : Block()
        data class Quote(val text: String, val runs: List<Run>) : Block()
        data class Image(val url: String, val widthFraction: Float?, val align: Align) : Block()
        data object Rule : Block()
    }

    enum class Align { Start, Center, End }

    /** One styled stretch of a block's text; runs may overlap only by nesting. */
    data class Run(val start: Int, val end: Int, val style: Style)

    data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val code: Boolean = false,
        /** ARGB, or null for the page's own ink. */
        val color: Long? = null,
        /** ARGB highlight behind the text (`<mark>`), or null. */
        val background: Long? = null,
        /** Relative to the body size; null when unspecified. */
        val sizeScale: Float? = null,
        val href: String? = null,
        /** The persona's name where it filled `{{user}}`. */
        val persona: Boolean = false,
    ) {
        val isPlain: Boolean
            get() = !bold && !italic && !underline && !strike && !code && color == null && background == null && sizeScale == null && href == null && !persona
    }

    private val TAG_HINT = Regex("""<(p|br|span|strong|b|em|i|u|s|img|h[1-6]|ul|ol|li|hr|a|mark|div|code|blockquote)[\s>/]""", RegexOption.IGNORE_CASE)

    /** True when the text is editor HTML rather than plain or markdown prose. */
    fun looksLikeHtml(text: String): Boolean = TAG_HINT.containsMatchIn(text)

    /**
     * [html] as blocks. `<style>` and `<script>` contents are never text; page-level rules in a
     * `<style>` block (see [pageLook]) set the alignment of paragraphs that don't set their own.
     */
    fun parse(html: String): List<Block> =
        Parser(withoutCode(html), defaultAlign = pageLook(html).align ?: Align.Start).run()

    private val STYLE_BLOCK = Regex("""<style[^>]*>(.*?)</style\s*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SCRIPT_BLOCK = Regex("""<script[^>]*>.*?</script\s*>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** [html] without `<style>` and `<script>` blocks, which a page runs or applies but never shows. */
    fun withoutCode(html: String): String = html.replace(STYLE_BLOCK, "").replace(SCRIPT_BLOCK, "")

    /**
     * What the `<style>` blocks inside [html] ask of the text as a whole: rules aimed at the
     * page around it (Janitor's own container classes, `body`), which the website shows as the
     * look of the whole description. ShyLoL's profile centres everything this way.
     */
    fun pageLook(html: String): CreatorCss.Look =
        STYLE_BLOCK.findAll(html).fold(CreatorCss.Look()) { look, m -> look + CreatorCss.pageLook(withoutCode(html), m.groupValues[1]) }

    /** The text with every tag removed and entities decoded; line breaks become spaces. */
    fun toPlainText(raw: String): String {
        val html = withoutCode(raw)
        val sb = StringBuilder(html.length)
        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val end = html.indexOf('>', i)
                if (end == -1) break
                val tag = html.substring(i + 1, end).trimStart('/').lowercase()
                if (tag.startsWith("br") || tag.startsWith("p") || tag.startsWith("li") || tag.startsWith("h") || tag.startsWith("div")) sb.append(' ')
                i = end + 1
            } else {
                sb.append(c); i++
            }
        }
        return decodeEntities(sb.toString()).stripPersonaMarks().replace(Regex("""\s+"""), " ").trim()
    }

    // ---- parser --------------------------------------------------------------------

    private class Parser(private val src: String, private val defaultAlign: Align = Align.Start) {
        private val blocks = ArrayList<Block>()
        private val text = StringBuilder()
        private val open = ArrayList<Pair<Style, Int>>()   // style stack with run starts
        private val runs = ArrayList<Run>()
        private var align = defaultAlign
        private var heading = 0
        private var inList: Boolean? = null                 // ordered?
        private var listIndex = 0
        private var inQuote = false
        private var inItem = false
        private var pendingSpace = false
        private var role: String? = null

        fun run(): List<Block> {
            var i = 0
            while (i < src.length) {
                val c = src[i]
                if (c == '<') {
                    val end = src.indexOf('>', i)
                    if (end == -1) break
                    handleTag(src.substring(i + 1, end))
                    i = end + 1
                } else {
                    val next = src.indexOf('<', i).let { if (it == -1) src.length else it }
                    appendText(decodeEntities(src.substring(i, next)))
                    i = next
                }
            }
            flush()
            return blocks
        }

        private fun appendText(raw: String) {
            for (ch in raw) {
                if (ch == PERSONA_OPEN) {
                    if (pendingSpace && text.isNotEmpty()) text.append(' ')
                    pendingSpace = false
                    push(currentStyle().copy(persona = true))
                    continue
                }
                if (ch == PERSONA_CLOSE) {
                    if (open.lastOrNull()?.first?.persona == true) pop()
                    continue
                }
                if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') {
                    pendingSpace = true
                } else {
                    if (pendingSpace && text.isNotEmpty()) text.append(' ')
                    pendingSpace = false
                    text.append(ch)
                }
            }
        }

        private fun currentStyle(): Style = open.lastOrNull()?.first ?: Style()

        private fun push(style: Style) {
            // A space waiting from before the tag belongs outside it: `out <a>here</a>`
            // underlines "here", not " here".
            if (pendingSpace && text.isNotEmpty() && text.last() != '\n') {
                text.append(' ')
                pendingSpace = false
            }
            open += style to text.length
        }

        private fun pop() {
            val (style, start) = open.removeLastOrNull() ?: return
            if (text.length > start && !style.isPlain) runs += Run(start, text.length, style)
        }

        private fun handleTag(body: String) {
            val closing = body.startsWith("/")
            val raw = body.trimStart('/').trim()
            val name = raw.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
            val attrs = raw.drop(name.length)
            when (name) {
                "br" -> { text.append('\n'); pendingSpace = false }
                "p", "div" -> if (closing) flush() else {
                    flush()
                    align = parseAlign(attrs) ?: defaultAlign
                    role = parseRole(attrs)
                    if (role == "dividerLine") { blocks += Block.Rule; role = null }
                }
                "h1", "h2", "h3", "h4", "h5", "h6" -> if (closing) flush() else { flush(); heading = name[1] - '0'; align = parseAlign(attrs) ?: defaultAlign }
                "ul", "ol" -> if (closing) { flush(); inList = null } else { flush(); inList = name == "ol"; listIndex = parseStart(attrs) - 1 }
                "li" -> if (closing) { flush(); inItem = false } else { flush(); inItem = true; listIndex++ }
                "blockquote" -> if (closing) { flush(); inQuote = false } else { flush(); inQuote = true }
                "hr" -> { flush(); blocks += Block.Rule }
                "img" -> {
                    val src = attr(attrs, "src") ?: return
                    val keep = align
                    flush()
                    val width = parseWidthFraction(attr(attrs, "style"))
                        ?: attr(attrs, "width")?.removeSuffix("px")?.toFloatOrNull()?.div(EDITOR_WIDTH_PX)?.coerceIn(0.1f, 1f)
                    blocks += Block.Image(src, width, keep)
                    align = keep
                }
                "strong", "b" -> inline(closing) { it.copy(bold = true) }
                "em", "i" -> inline(closing) { it.copy(italic = true) }
                "u" -> inline(closing) { it.copy(underline = true) }
                "s", "del", "strike" -> inline(closing) { it.copy(strike = true) }
                "code" -> inline(closing) { it.copy(code = true) }
                "mark" -> inline(closing) { it.copy(background = parseColor(styleProp(attr(attrs, "style"), "background-color")) ?: DEFAULT_MARK) }
                "a" -> inline(closing) { it.copy(href = attr(attrs, "href")) }
                "span", "font" -> inline(closing) { base ->
                    val style = attr(attrs, "style")
                    base.copy(
                        color = parseColor(styleProp(style, "color") ?: attr(attrs, "color")) ?: base.color,
                        background = parseColor(styleProp(style, "background-color")) ?: base.background,
                        sizeScale = parseSize(styleProp(style, "font-size")) ?: base.sizeScale,
                        bold = base.bold || styleProp(style, "font-weight")?.let { it == "bold" || (it.toIntOrNull() ?: 0) >= 600 } == true,
                        italic = base.italic || styleProp(style, "font-style") == "italic",
                        underline = base.underline || styleProp(style, "text-decoration")?.contains("underline") == true,
                    )
                }
                else -> Unit // unknown tags contribute their text only
            }
        }

        private inline fun inline(closing: Boolean, derive: (Style) -> Style) {
            if (closing) pop() else push(derive(currentStyle()))
        }

        private fun flush() {
            // Close every inline run at the block boundary, then reopen for the next block.
            val reopen = open.map { it.first }
            while (open.isNotEmpty()) pop()
            val body = text.toString().trim('\n', ' ')
            if (body.isNotEmpty()) {
                val trimmedLeading = text.length - text.toString().trimStart('\n', ' ').length
                val shifted = runs.mapNotNull { r ->
                    val s = (r.start - trimmedLeading).coerceAtLeast(0)
                    val e = (r.end - trimmedLeading).coerceAtMost(body.length)
                    if (e > s) Run(s, e, r.style) else null
                }
                blocks += when {
                    inItem && inList != null -> Block.ListItem(body, shifted, inList == true, listIndex)
                    inQuote -> Block.Quote(body, shifted)
                    else -> Block.Paragraph(body, shifted, align, heading, role)
                }
            }
            text.setLength(0)
            runs.clear()
            pendingSpace = false
            heading = 0
            align = defaultAlign
            role = null
            for (style in reopen) push(style)
        }
    }

    // ---- attribute helpers ---------------------------------------------------------

    private val ATTR = Regex("""([a-zA-Z-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""")

    private fun attr(attrs: String, name: String): String? =
        ATTR.findAll(attrs).firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
            ?.let { it.groupValues[3].ifEmpty { it.groupValues[4].ifEmpty { it.groupValues[5] } } }

    private fun styleProp(style: String?, prop: String): String? =
        style?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith(prop, ignoreCase = true) && it.substringAfter(prop).trimStart().startsWith(":") }
            ?.substringAfter(':')?.trim()?.lowercase()

    /** A tag's own `text-align`, or null when it sets none (the page's default then applies). */
    private fun parseAlign(attrs: String): Align? = when (styleProp(attr(attrs, "style"), "text-align")) {
        "center" -> Align.Center
        "right", "end" -> Align.End
        "left", "start", "justify" -> Align.Start
        else -> null
    }

    private fun parseStart(attrs: String): Int = attr(attrs, "start")?.toIntOrNull() ?: 1

    private val CLASS_STEM = Regex("""^_?([A-Za-z][A-Za-z0-9]*?)(?:_[A-Za-z0-9]{4,6}_\d+)?$""")

    /** The first class that names one of the looks the official client ships. */
    private fun parseRole(attrs: String): String? =
        attr(attrs, "class")?.split(' ')?.mapNotNull { CLASS_STEM.find(it.trim())?.groupValues?.get(1) }
            ?.firstOrNull { it in KNOWN_ROLES }

    val KNOWN_ROLES = setOf(
        "appModalBadge", "characterInfoMarkdownContent", "card", "dividerLine", "bonus", "dropzone",
        "characterInfoShinyBox", "iosAlert", "confirm", "button", "pageTitle", "detailName",
        "metaCaption", "moderationNotes", "holidays2025Pattern1",
    )

    private fun parseWidthFraction(style: String?): Float? {
        val w = styleProp(style, "width") ?: return null
        return when {
            w.endsWith("%") -> w.dropLast(1).toFloatOrNull()?.div(100f)?.coerceIn(0.1f, 1f)
            w.endsWith("px") -> w.dropLast(2).toFloatOrNull()?.div(EDITOR_WIDTH_PX)?.coerceIn(0.1f, 1f)
            else -> null
        }
    }

    private fun parseSize(value: String?): Float? {
        val v = value ?: return null
        val px = when {
            v.endsWith("px") -> v.dropLast(2).toFloatOrNull()
            v.endsWith("pt") -> v.dropLast(2).toFloatOrNull()?.times(4f / 3f)
            v.endsWith("em") -> v.dropLast(2).toFloatOrNull()?.times(16f)
            else -> null
        } ?: return null
        return (px / 16f).coerceIn(0.7f, 2.0f)
    }

    private val RGB = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""")

    /** CSS colour → ARGB long, or null for anything unparseable. */
    fun parseColor(value: String?): Long? {
        val v = value?.trim()?.lowercase() ?: return null
        RGB.find(v)?.let { m ->
            val (r, g, b) = m.destructured
            return argb(r.toInt(), g.toInt(), b.toInt())
        }
        if (v.startsWith("#")) {
            val hex = v.drop(1)
            return when (hex.length) {
                3 -> argb(hex[0].dup(), hex[1].dup(), hex[2].dup())
                6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
                8 -> hex.toLongOrNull(16)?.let { (it and 0xFF) shl 24 or (it ushr 8) }
                else -> null
            }
        }
        return NAMED[v]
    }

    private fun Char.dup(): Int = "$this$this".toInt(16)
    private fun argb(r: Int, g: Int, b: Int): Long = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

    private val NAMED = mapOf(
        "white" to 0xFFFFFFFFL, "black" to 0xFF000000L, "red" to 0xFFFF0000L, "yellow" to 0xFFFFFF00L,
        "pink" to 0xFFFFC0CBL, "blue" to 0xFF0000FFL, "green" to 0xFF008000L, "purple" to 0xFF800080L,
        "orange" to 0xFFFFA500L, "gray" to 0xFF808080L, "grey" to 0xFF808080L, "cyan" to 0xFF00FFFFL,
        "magenta" to 0xFFFF00FFL, "gold" to 0xFFFFD700L, "silver" to 0xFFC0C0C0L,
    )

    private const val DEFAULT_MARK = 0xFFFFE066L

    /** The editor's content column, which `width: 395px` style images were sized against. */
    private const val EDITOR_WIDTH_PX = 400f

    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY.replace(text) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> when (e) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
                    "nbsp" -> " "; "hellip" -> "…"; "mdash" -> "—"; "ndash" -> "–"
                    "lsquo" -> "‘"; "rsquo" -> "’"; "ldquo" -> "“"; "rdquo" -> "”"
                    else -> m.value
                }
            }
        }
    }
}
