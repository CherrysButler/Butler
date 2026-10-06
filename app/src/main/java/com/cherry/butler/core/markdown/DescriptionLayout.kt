package com.cherry.butler.core.markdown

import com.cherry.butler.core.markdown.RpMarkdown.Block
import com.cherry.butler.core.markdown.RpMarkdown.InlineKind
import com.cherry.butler.core.markdown.RpMarkdown.Span

/**
 * Turns a character description's markdown into the shape Janitor's own page gives it.
 *
 * Creators write descriptions as loose markdown: a short line ending in a colon is a
 * section head, the lines under it are one fact each, an italic line on its own is an
 * aside. The official client reads those conventions and lays them out as labels,
 * panels and notes; this does the same from the same cues, so a description that looks
 * alive there looks alive here. Prose before the first head stays prose.
 */
object DescriptionLayout {

    sealed interface Doc {
        data class Prose(val text: String, val spans: List<Span>) : Doc
        data class Label(val text: String) : Doc
        /** One fact under a head; [note] when the whole line is an aside in italics. */
        data class Panel(val text: String, val spans: List<Span>, val note: Boolean) : Doc
        data class ListItem(val text: String, val spans: List<Span>, val ordered: Boolean, val index: Int) : Doc
        data object Rule : Doc
    }

    fun layout(blocks: List<Block>): List<Doc> {
        val out = ArrayList<Doc>()
        var inSection = false
        for (block in blocks) {
            when (block) {
                Block.Rule -> out += Doc.Rule
                is Block.Image -> Unit // never parsed for descriptions
                is Block.ListItem -> out += Doc.ListItem(block.text, block.spans, block.ordered, block.index)
                is Block.Paragraph -> {
                    if (isLabel(block)) {
                        out += Doc.Label(block.text.trimEnd(':').trim())
                        inSection = true
                    } else if (inSection) {
                        for ((text, spans) in splitLines(block)) {
                            out += Doc.Panel(text, spans, note = isAside(text, spans))
                        }
                    } else {
                        out += Doc.Prose(block.text, block.spans)
                    }
                }
            }
        }
        return out
    }

    /** A head: one short line, ending in a colon, with no sentence inside it. */
    fun isLabel(block: Block.Paragraph): Boolean {
        val t = block.text.trim()
        return t.length in 3..48 && !t.contains('\n') && t.endsWith(':') &&
            !t.dropLast(1).contains(Regex("""[.!?:]""")) && t.count { it == ' ' } <= 6
    }

    /** Wholly italic, or wholly italic apart from surrounding quotes. */
    private fun isAside(text: String, spans: List<Span>): Boolean {
        val action = spans.filter { it.kind == InlineKind.Action }
        if (action.isEmpty()) return false
        val covered = action.sumOf { it.end - it.start }
        return covered >= text.trim().length - 2
    }

    /** Each line of a paragraph as its own text plus the spans that fall inside it. */
    private fun splitLines(block: Block.Paragraph): List<Pair<String, List<Span>>> {
        val result = ArrayList<Pair<String, List<Span>>>()
        var start = 0
        val text = block.text
        while (start <= text.length) {
            val nl = text.indexOf('\n', start).let { if (it == -1) text.length else it }
            val line = text.substring(start, nl)
            val lead = line.length - line.trimStart().length
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val base = start + lead
                val spans = block.spans.mapNotNull { s ->
                    val a = (s.start - base).coerceAtLeast(0)
                    val b = (s.end - base).coerceAtMost(trimmed.length)
                    if (b > a && s.end > base && s.start < base + trimmed.length) Span(a, b, s.kind) else null
                }
                result += trimmed to spans
            }
            if (nl == text.length) break
            start = nl + 1
        }
        return result
    }
}
