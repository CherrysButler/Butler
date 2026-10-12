package com.cherry.butler.core.markdown

/**
 * Between the website's editor HTML (a bio, a character's description) and the HTML of the
 * rich editor Butler writes them in (Compose Rich Editor). The two describe the same things
 * differently; this keeps what's saved in the website's own shape, so the website shows and
 * edits it as if it had written it:
 *
 * | | website | rich editor |
 * |---|---|---|
 * | an empty line | `<p></p>` | `<br>` |
 * | a heading | `<h2 style="text-align: center">…</h2>` | `<p style="text-align: center;"><h2>…</h2></p>` |
 * | bold, italic | `<strong>`, `<em>` | `<b>`, `<i>` |
 * | a highlight | `<mark data-color="#9d0808" style="background-color: rgb(…); color: inherit;">` | `<span style="background: rgba(…);">` |
 * | a colour | `<span style="color: rgb(…);">` | `<span style="color: rgba(…, 1);">` |
 * | a picture | `<img src width="208" height="341">` | `<img src width="208.0" height="341.0">` |
 */
object EditorHtml {

    /** The website's HTML, ready to load into the editor. */
    fun toEditor(website: String): String {
        if (website.isBlank()) return ""
        if (!RichHtml.looksLikeHtml(website)) return website
        val nodes = Dom.parse(website)
        return buildString { nodes.forEach { appendForEditor(it, topLevel = true) } }
    }

    /** The editor's HTML, as the website would have written it. */
    fun toWebsite(editor: String): String {
        if (editor.isBlank()) return ""
        val nodes = Dom.parse(editor)
        val out = StringBuilder()
        nodes.forEach { node ->
            when {
                node is Dom.Element && node.name == "br" -> out.append("<p></p>")
                node is Dom.Element && node.name == "p" -> out.appendBlock(node)
                node is Dom.Element && (node.name == "ul" || node.name == "ol") -> out.appendList(node)
                else -> out.appendInline(node)
            }
        }
        // A trailing empty line is the editor's caret room, not the writer's.
        return out.toString().removeSuffix("<p></p>").let { if (it.replace("<p></p>", "").isEmpty()) "" else it }
    }

    // ---- into the editor ------------------------------------------------------------------

    private fun StringBuilder.appendForEditor(node: Dom.Node, topLevel: Boolean) {
        when (node) {
            is Dom.Text -> append(node.raw)
            is Dom.Element -> when {
                // An empty paragraph would merge into the next; the editor's empty line is a <br>.
                topLevel && node.name == "p" && node.children.all { it is Dom.Text && it.raw.isBlank() } -> append("<br>")
                // The website's highlight, its colour kept: a bare <mark> would take the editor's yellow.
                node.name == "mark" -> {
                    val colour = node.attrs["data-color"] ?: Css.parse(node.attrs["style"])["background-color"]
                    append(if (colour != null) """<span style="background-color: $colour;">""" else "<mark>")
                    node.children.forEach { appendForEditor(it, topLevel = false) }
                    append(if (colour != null) "</span>" else "</mark>")
                }
                else -> {
                    append(node.openTag())
                    node.children.forEach { appendForEditor(it, topLevel = false) }
                    if (!node.void) append("</${node.name}>")
                }
            }
        }
    }

    // ---- out of the editor ----------------------------------------------------------------

    private val HEADING = Regex("h[1-6]")

    /** A paragraph; a heading inside it makes the whole line that heading, as the website keeps it. */
    private fun StringBuilder.appendBlock(p: Dom.Element) {
        val level = p.find { it.name.matches(HEADING) }?.name?.get(1)?.digitToInt() ?: 0
        val tag = if (level > 0) "h$level" else "p"
        val align = Css.parse(p.attrs["style"])["text-align"]?.takeIf { it in setOf("center", "right", "justify") }
        append("<$tag")
        if (align != null) append(""" style="text-align: $align"""")
        append(">")
        p.children.forEach { appendInline(it) }
        append("</$tag>")
    }

    private fun StringBuilder.appendList(list: Dom.Element) {
        append("<${list.name}>")
        list.children.forEach { item ->
            if (item is Dom.Element && item.name == "li") {
                append("<li><p>")
                item.children.forEach { appendInline(it) }
                append("</p></li>")
            } else if (item is Dom.Element && (item.name == "ul" || item.name == "ol")) {
                appendList(item)
            }
        }
        append("</${list.name}>")
    }

    private fun StringBuilder.appendInline(node: Dom.Node) {
        when (node) {
            is Dom.Text -> append(node.raw)
            is Dom.Element -> {
                val inner: StringBuilder.() -> Unit = { node.children.forEach { appendInline(it) } }
                when (node.name) {
                    "b", "strong" -> wrap("<strong>", "</strong>", inner)
                    "i", "em" -> wrap("<em>", "</em>", inner)
                    "u", "s", "code", "sub", "sup" -> wrap("<${node.name}>", "</${node.name}>", inner)
                    "mark" -> wrap("<mark>", "</mark>", inner)
                    // Headings are the line's (see appendBlock), not a word's.
                    "h1", "h2", "h3", "h4", "h5", "h6", "p", "span" -> {
                        val css = Css.parse(node.attrs["style"])
                        val colour = css["color"]?.let(Css::rgb)
                        val highlight = (css["background"] ?: css["background-color"])?.let(Css::rgb)
                        val open = StringBuilder()
                        val close = StringBuilder()
                        if (highlight != null) {
                            open.append("""<mark data-color="${Css.hex(highlight)}" style="background-color: ${highlight.css}; color: inherit;">""")
                            close.insert(0, "</mark>")
                        }
                        if (colour != null) {
                            open.append("""<span style="color: ${colour.css};">""")
                            close.insert(0, "</span>")
                        }
                        wrap(open.toString(), close.toString(), inner)
                    }
                    "a" -> wrap("""<a target="_blank" rel="noopener noreferrer nofollow" href="${node.attrs["href"].orEmpty()}">""", "</a>", inner)
                    "img" -> {
                        append("""<img src="${node.attrs["src"].orEmpty()}"""")
                        node.attrs["width"]?.toFloatOrNull()?.let { append(""" width="${it.toInt()}"""") }
                        node.attrs["height"]?.toFloatOrNull()?.let { append(""" height="${it.toInt()}"""") }
                        append(">")
                    }
                    "br" -> append("<br>")
                    else -> inner()
                }
            }
        }
    }

    private inline fun StringBuilder.wrap(open: String, close: String, inner: StringBuilder.() -> Unit) {
        append(open); inner(); append(close)
    }

    // ---- a small HTML reader --------------------------------------------------------------

    /** Just enough of HTML for editor output: elements, attributes, raw text kept encoded. */
    internal object Dom {
        sealed class Node
        data class Text(val raw: String) : Node()
        data class Element(val name: String, val attrs: Map<String, String>, val children: MutableList<Node> = mutableListOf()) : Node() {
            val void: Boolean get() = name in VOID

            fun openTag(): String = buildString {
                append("<").append(name)
                attrs.forEach { (k, v) -> append(" ").append(k).append("=\"").append(v.replace("\"", "&quot;")).append("\"") }
                append(">")
            }

            /** The first element inside, at any depth, that [match]es. */
            fun find(match: (Element) -> Boolean): Element? {
                for (c in children) {
                    if (c is Element) {
                        if (match(c)) return c
                        c.find(match)?.let { return it }
                    }
                }
                return null
            }
        }

        private val VOID = setOf("br", "hr", "img", "input", "wbr", "source")
        private val TAG = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\s+[^\s=>/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(/?)>""")
        private val ATTR = Regex("""([^\s=>/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""")

        fun parse(html: String): List<Node> {
            val root = Element("#root", emptyMap())
            val stack = ArrayDeque<Element>().apply { addLast(root) }
            var at = 0
            for (m in TAG.findAll(html)) {
                if (m.range.first > at) stack.last().children += Text(html.substring(at, m.range.first))
                at = m.range.last + 1
                val name = m.groupValues[2].lowercase()
                if (m.groupValues[1] == "/") {
                    // Close up to the matching element; a stray closing tag is dropped.
                    val i = stack.indexOfLast { it.name == name }
                    if (i > 0) repeat(stack.size - i) { stack.removeLast() }
                    continue
                }
                val attrs = ATTR.findAll(m.groupValues[3]).associate { a ->
                    a.groupValues[1].lowercase() to (a.groups[2]?.value ?: a.groups[3]?.value ?: a.groups[4]?.value ?: "")
                }
                val el = Element(name, attrs)
                stack.last().children += el
                if (name !in VOID && m.groupValues[4] != "/") stack.addLast(el)
            }
            if (at < html.length) stack.last().children += Text(html.substring(at))
            return root.children
        }
    }

    /** Inline CSS: a declaration list, and the colours in it. */
    private object Css {
        data class Rgb(val r: Int, val g: Int, val b: Int) {
            val css: String get() = "rgb($r, $g, $b)"
        }

        fun parse(style: String?): Map<String, String> =
            style.orEmpty().split(';').mapNotNull { d ->
                val k = d.substringBefore(':', "").trim().lowercase()
                val v = d.substringAfter(':', "").trim()
                if (k.isEmpty() || v.isEmpty()) null else k to v
            }.toMap()

        /** A CSS colour as RGB; transparent and unknown ones as null. */
        fun rgb(value: String): Rgb? {
            // rgba(…, 0) is no colour at all; the parser below reads past the alpha.
            Regex("""rgba\([^)]*,\s*([0-9.]+)\s*\)""").find(value)?.let { if (it.groupValues[1].toFloatOrNull() == 0f) return null }
            val argb = RichHtml.parseColor(value) ?: return null
            if (((argb ushr 24) and 0xFF) == 0L) return null
            return Rgb(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt())
        }

        fun hex(c: Rgb): String = "#%02x%02x%02x".format(c.r, c.g, c.b)
    }
}
