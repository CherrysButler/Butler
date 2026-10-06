package com.cherry.butler.core.markdown

/**
 * A purpose-built parser for the six constructs roleplay text actually uses:
 *
 * - `*action*` / `_action_`      → [InlineKind.Action]
 * - `**emphasis**` / `__emphasis__` → [InlineKind.Strong]
 * - `"speech"` / “speech” → [InlineKind.Speech]
 * - `` `thought` ``       → [InlineKind.Thought]
 * - `---` on its own line → [Block.Rule]
 * - `- item` / `1. item`  → [Block.ListItem]
 * - `![alt](url)`         → [Block.Image], only when the caller asks for images
 *
 * General CommonMark parsers mis-handle this text: they break on `**bold**` next to
 * `*action*`, and one stray `*` italicises the rest of a message. Two rules fix that here
 * and they are the whole reason this parser exists: **`**` is resolved before `*`**, and
 * **an unbalanced marker renders literally** rather than styling everything after it.
 *
 * Pure Kotlin, no Compose dependency, so it is unit-testable and reusable by the
 * streaming path. Output is plain text plus spans; a Compose mapper turns spans into
 * `AnnotatedString` styles from the theme.
 */
object RpMarkdown {

    /** [Butter] and the moods come from scene tags (`<butter>`, `<romantic>`, … [SceneTags]), not markdown. */
    enum class InlineKind { Strong, Action, Speech, Thought, Persona, Butter, Romantic, Erotic, Dangerous, Sad, Funny }

    /** A styled range over [Block.Paragraph.text] / [Block.ListItem.text] — end exclusive. */
    data class Span(val start: Int, val end: Int, val kind: InlineKind)

    sealed interface Block {
        data class Paragraph(val text: String, val spans: List<Span>) : Block
        data class ListItem(val text: String, val spans: List<Span>, val ordered: Boolean, val index: Int) : Block
        data object Rule : Block
        /** A markdown picture, lifted out of its line. Whether it may load is the renderer's call. */
        data class Image(val url: String, val alt: String) : Block
    }

    /**
     * @param keepQuotes keep the quote marks around speech (the reader's choice; Janitor
     *   shows them). Off, the colour alone marks speech.
     * @param images lift `![alt](url)` out as [Block.Image]; off, it stays literal text.
     */
    fun parse(source: String, keepQuotes: Boolean = false, images: Boolean = false): List<Block> {
        val blocks = ArrayList<Block>()
        val paragraph = StringBuilder()
        var orderedIndex = 0
        // Scene tags (<butter>…) ride through the inline parser as private characters and are
        // lifted out per block; one left open runs on into the next block.
        val openTags = LinkedHashSet<SceneTag>()

        fun inline(raw: String): Pair<String, List<Span>> {
            val (text, spans) = parseInline(raw, keepQuotes)
            return liftTags(text, spans, openTags)
        }

        fun flushParagraph() {
            if (paragraph.isNotBlank()) {
                val (text, spans) = inline(paragraph.toString().trim())
                blocks += Block.Paragraph(text, spans)
            }
            paragraph.setLength(0)
        }

        for (rawLine in SceneTags.toMarks(source).replace("\r\n", "\n").split('\n')) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushParagraph()
                RULE.matches(trimmed) -> { flushParagraph(); blocks += Block.Rule }
                BULLET.matches(trimmed) -> {
                    flushParagraph()
                    val (text, spans) = inline(trimmed.substring(2).trim())
                    blocks += Block.ListItem(text, spans, ordered = false, index = 0)
                }
                ORDERED.matches(trimmed) -> {
                    flushParagraph()
                    val m = ORDERED.matchEntire(trimmed)!!
                    orderedIndex = m.groupValues[1].toIntOrNull() ?: (orderedIndex + 1)
                    val (text, spans) = inline(m.groupValues[2].trim())
                    blocks += Block.ListItem(text, spans, ordered = true, index = orderedIndex)
                }
                images && IMAGE.containsMatchIn(line) -> {
                    // Text either side of a picture stays in the paragraph flow around it.
                    var from = 0
                    for (m in IMAGE.findAll(line)) {
                        val before = line.substring(from, m.range.first)
                        if (before.isNotBlank()) {
                            if (paragraph.isNotEmpty()) paragraph.append('\n')
                            paragraph.append(before)
                        }
                        flushParagraph()
                        blocks += Block.Image(url = m.groupValues[2], alt = m.groupValues[1].trim())
                        from = m.range.last + 1
                    }
                    val after = line.substring(from)
                    if (after.isNotBlank()) paragraph.append(after.trimStart())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line)
                }
            }
        }
        flushParagraph()
        return blocks
    }

    /**
     * Takes the scene-tag characters out of [text], turning each pair into a span, and moves
     * [spans] to match. Tags open at the start (carried from the block before) begin at 0;
     * tags still open at the end run to the end and stay in [open] for the next block.
     */
    private fun liftTags(text: String, spans: List<Span>, open: MutableSet<SceneTag>): Pair<String, List<Span>> {
        if (open.isEmpty() && text.none { SceneTag.ofChar(it) != null }) return text to spans
        val at = IntArray(text.length + 1)
        val out = StringBuilder(text.length)
        val startOf = HashMap<SceneTag, Int>()
        for (t in open) startOf[t] = 0
        val tagSpans = ArrayList<Span>()
        for (i in text.indices) {
            at[i] = out.length
            val mark = SceneTag.ofChar(text[i])
            if (mark == null) {
                out.append(text[i])
                continue
            }
            val (tag, opening) = mark
            if (opening) {
                startOf[tag] = out.length
            } else {
                val from = startOf.remove(tag) ?: continue
                if (out.length > from) tagSpans += Span(from, out.length, tag.kind())
            }
        }
        at[text.length] = out.length
        open.clear()
        for ((tag, from) in startOf) {
            if (out.length > from) tagSpans += Span(from, out.length, tag.kind())
            open += tag
        }
        // Tag tints go first so markdown colours (speech, the persona's name) still show on them.
        val moved = spans.map { it.copy(start = at[it.start], end = at[it.end]) }.filter { it.end > it.start }
        return out.toString() to (tagSpans + moved)
    }

    private fun SceneTag.kind(): InlineKind = when (this) {
        SceneTag.Butter -> InlineKind.Butter
        SceneTag.Romantic -> InlineKind.Romantic
        SceneTag.Erotic -> InlineKind.Erotic
        SceneTag.Dangerous -> InlineKind.Dangerous
        SceneTag.Sad -> InlineKind.Sad
        SceneTag.Funny -> InlineKind.Funny
    }

    // ---- inline ---------------------------------------------------------------

    private enum class Delim { Strong, Action, Thought, Speech, Persona }

    private class Token(val delim: Delim?, val start: Int, val length: Int, val canOpen: Boolean, val canClose: Boolean) {
        var partner: Token? = null
        /** An action `*` the writer opened and never closed (see [parseInline]). */
        var stray: Boolean = false
    }

    /**
     * Returns the text with consumed markers removed, plus spans over that text.
     *
     * Resolution is a single pass with one stack per delimiter type. Markers only count
     * as openers or closers by their flanking (an opening `*` is followed by a non-space,
     * a closing `*` is preceded by one), which is what keeps `5 * 3 * 2` from italicising.
     * Anything left unmatched at the end is emitted as the literal character it was.
     */
    fun parseInline(input: String, keepQuotes: Boolean = false): Pair<String, List<Span>> {
        val tokens = tokenize(input)

        val open = HashMap<Delim, ArrayDeque<Token>>()
        for (t in tokens) {
            val d = t.delim ?: continue
            val stack = open.getOrPut(d) { ArrayDeque() }
            if (t.canClose && stack.isNotEmpty()) {
                val opener = stack.removeLast()
                opener.partner = t
                t.partner = opener
                continue
            }
            if (t.canOpen) stack.addLast(t)
        }

        // A lone single `*` that opens and is never closed is how models slip: `"…" *she
        // laughs, "…"`. It is read as narration running to the next speech or the end,
        // which is what the writer meant, instead of printing a stray star.
        val strayAction = tokens.firstOrNull { it.delim == Delim.Action && it.partner == null && it.canOpen && it.length == 1 }
        strayAction?.stray = true

        val out = StringBuilder(input.length)
        val spans = ArrayList<Span>()
        val openAt = HashMap<Token, Int>()
        var strayFrom = -1
        for (t in tokens) {
            val d = t.delim
            if (t.stray) {
                strayFrom = out.length
                continue
            }
            if (d == null || t.partner == null) {
                if (d != Delim.Persona) out.append(input, t.start, t.start + t.length)
                continue
            }
            val partner = t.partner!!
            val quote = d == Delim.Speech && keepQuotes
            if (partner.start > t.start) {
                if (d == Delim.Speech && strayFrom >= 0) {
                    if (out.length > strayFrom) spans += Span(strayFrom, out.trimEnd().length.coerceAtLeast(strayFrom), InlineKind.Action)
                    strayFrom = -1
                }
                openAt[t] = out.length
                if (quote) out.append(input, t.start, t.start + t.length)
            } else {
                if (quote) out.append(input, t.start, t.start + t.length)
                val from = openAt[partner] ?: continue
                if (out.length > from) spans += Span(from, out.length, d.toKind())
            }
        }
        if (strayFrom >= 0) {
            val end = out.trimEnd().length
            if (end > strayFrom) spans += Span(strayFrom, end, InlineKind.Action)
        }
        // The persona's colour has to win inside speech or action, so its spans go last.
        return out.toString() to spans.sortedBy { if (it.kind == InlineKind.Persona) 1 else 0 }
    }

    private fun Delim.toKind() = when (this) {
        Delim.Strong -> InlineKind.Strong
        Delim.Action -> InlineKind.Action
        Delim.Thought -> InlineKind.Thought
        Delim.Speech -> InlineKind.Speech
        Delim.Persona -> InlineKind.Persona
    }

    private fun tokenize(s: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        var textStart = 0
        fun flushText(upTo: Int) {
            if (upTo > textStart) tokens += Token(null, textStart, upTo - textStart, canOpen = false, canClose = false)
        }
        while (i < s.length) {
            val c = s[i]
            when {
                // A backslash before punctuation is a markdown escape (`\~~~`, `\*`): the
                // backslash goes and the character after it is plain text.
                c == '\\' && s.getOrNull(i + 1)?.let { it.code < 128 && !it.isLetterOrDigit() && !it.isWhitespace() } == true -> {
                    flushText(i)
                    textStart = i + 1
                    i += 2
                }
                c == '*' -> {
                    var run = 1
                    while (i + run < s.length && s[i + run] == '*') run++
                    flushText(i)
                    val before = s.getOrNull(i - 1)
                    val after = s.getOrNull(i + run)
                    // `**` resolves before `*`: a run of two or more is a strong delimiter,
                    // with any odd remainder becoming a single action delimiter.
                    var remaining = run
                    var pos = i
                    while (remaining >= 2) {
                        tokens += Token(Delim.Strong, pos, 2, canOpen = after.isOpenFlank(), canClose = before.isCloseFlank())
                        pos += 2; remaining -= 2
                    }
                    if (remaining == 1) {
                        tokens += Token(Delim.Action, pos, 1, canOpen = after.isOpenFlank(), canClose = before.isCloseFlank())
                    }
                    i += run
                    textStart = i
                }
                c == '_' -> {
                    var run = 1
                    while (i + run < s.length && s[i + run] == '_') run++
                    val before = s.getOrNull(i - 1)
                    val after = s.getOrNull(i + run)
                    // Underscores inside a word (snake_case, file_name) are never markup.
                    if (before.isWordChar() && after.isWordChar()) {
                        i += run
                        continue
                    }
                    flushText(i)
                    var remaining = run
                    var pos = i
                    while (remaining >= 2) {
                        tokens += Token(Delim.Strong, pos, 2, canOpen = after.isOpenFlank() && !before.isWordChar(), canClose = before.isCloseFlank() && !after.isWordChar())
                        pos += 2; remaining -= 2
                    }
                    if (remaining == 1) {
                        tokens += Token(Delim.Action, pos, 1, canOpen = after.isOpenFlank() && !before.isWordChar(), canClose = before.isCloseFlank() && !after.isWordChar())
                    }
                    i += run
                    textStart = i
                }
                c == '`' -> {
                    flushText(i)
                    val before = s.getOrNull(i - 1)
                    val after = s.getOrNull(i + 1)
                    tokens += Token(Delim.Thought, i, 1, canOpen = after.isOpenFlank(), canClose = before.isCloseFlank())
                    i++; textStart = i
                }
                c == '"' -> {
                    flushText(i)
                    val before = s.getOrNull(i - 1)
                    val after = s.getOrNull(i + 1)
                    tokens += Token(Delim.Speech, i, 1, canOpen = after.isOpenFlank(), canClose = before.isCloseFlank())
                    i++; textStart = i
                }
                c == PERSONA_OPEN -> {
                    flushText(i)
                    tokens += Token(Delim.Persona, i, 1, canOpen = true, canClose = false)
                    i++; textStart = i
                }
                c == PERSONA_CLOSE -> {
                    flushText(i)
                    tokens += Token(Delim.Persona, i, 1, canOpen = false, canClose = true)
                    i++; textStart = i
                }
                c == '“' -> { // “ always opens
                    flushText(i)
                    tokens += Token(Delim.Speech, i, 1, canOpen = true, canClose = false)
                    i++; textStart = i
                }
                c == '”' -> { // ” always closes
                    flushText(i)
                    tokens += Token(Delim.Speech, i, 1, canOpen = false, canClose = true)
                    i++; textStart = i
                }
                else -> i++
            }
        }
        flushText(s.length)
        return tokens
    }

    private fun Char?.isOpenFlank(): Boolean = this != null && !this.isWhitespace()
    private fun Char?.isWordChar(): Boolean = this != null && this.isLetterOrDigit()
    private fun Char?.isCloseFlank(): Boolean = this != null && !this.isWhitespace()

    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val BULLET = Regex("""^[-•*]\s+\S.*$""")
    private val ORDERED = Regex("""^(\d{1,3})[.)]\s+(\S.*)$""")
    /** `![alt](url)` or `![alt](url "title")`; the title is dropped. */
    private val IMAGE = Regex("""!\[([^\]]*)]\(\s*<?([^\s)>]+)>?(?:\s+"[^"]*")?\s*\)""")
}
