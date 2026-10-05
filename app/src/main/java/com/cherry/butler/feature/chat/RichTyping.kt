package com.cherry.butler.feature.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** The three marks Rich typing writes: "speech", *action*, **bold**. */
enum class Mark(val open: String, val close: String, val key: String) {
    Speech("\"", "\"", "speech"),
    Action("*", "*", "action"),
    Bold("**", "**", "bold"),
    ;

    companion object {
        fun of(key: String?): Mark? = entries.firstOrNull { it.key == key }
    }
}

/** One marked stretch: [start]..[end] with the marks, [contentStart]..[contentEnd] without. */
data class MarkSpan(val mark: Mark, val start: Int, val contentStart: Int, val contentEnd: Int, val end: Int)

/**
 * Where typing goes. [mode] is what the next words open (null: plain); [armed] means a key
 * asked for it, so it opens even inside another mark (bold inside an action), where the
 * default alone would not. [inner] is a 'quote' opened inside speech by the ' key, until it
 * is stepped out of or the caret goes elsewhere.
 */
data class TypingState(val mode: Mark?, val armed: Boolean = false, val inner: Boolean = false)

/**
 * Rich typing's rules, kept apart from the screen so they can be tested on their own.
 *
 * - The first thing typed outside any mark opens the current mode around it (`h` becomes
 *   `*h*`, the caret inside), so nothing empty is ever left behind.
 * - A key arms its mark: the next thing typed opens it there, nested if need be. The lit
 *   key, pressed inside its own mark, steps out of it.
 * - Stepping just past a closing mark (a tap, or the arrow) steps out: one space goes in and
 *   typing returns to the default mode.
 * - Inside speech or an action a typed `"` becomes `'`, so a quote within them never ends
 *   or opens anything. Inside speech the " key is a ' key: it opens 'a quote' there, and
 *   pressed again steps out of it, still in the speech.
 */
object RichTyping {

    /** Every closed mark in [text], outer ones before the ones inside them. */
    fun spans(text: String): List<MarkSpan> {
        data class Open(val mark: Mark, val at: Int)
        val out = ArrayList<MarkSpan>()
        val stack = ArrayList<Open>()
        var i = 0
        while (i < text.length) {
            val mark = when {
                text[i] == '*' && i + 1 < text.length && text[i + 1] == '*' -> Mark.Bold
                text[i] == '*' -> Mark.Action
                text[i] == '"' -> Mark.Speech
                else -> null
            }
            if (mark == null) {
                i++
                continue
            }
            val opened = stack.indexOfLast { it.mark == mark }
            if (opened >= 0) {
                val o = stack[opened]
                // Anything opened inside it and never closed is just a stray mark.
                while (stack.size > opened) stack.removeAt(stack.lastIndex)
                out += MarkSpan(mark, o.at, o.at + mark.open.length, i, i + mark.close.length)
            } else {
                stack += Open(mark, i)
            }
            i += mark.open.length
        }
        return out.sortedBy { it.start }
    }

    /** The innermost mark whose words [caret] is among (edges included). */
    fun spanAt(text: String, caret: Int): MarkSpan? =
        spans(text).filter { caret in it.contentStart..it.contentEnd }.maxByOrNull { it.contentStart }

    /** Whether the caret is in speech (not about to open something else): the " key is then ' . */
    fun inSpeech(value: TextFieldValue, state: TypingState): Boolean =
        !state.armed && value.selection.collapsed && spanAt(value.text, value.selection.start)?.mark == Mark.Speech

    /** What a key would light: what the next thing typed will be. */
    fun lit(value: TextFieldValue, state: TypingState): Mark? {
        if (state.armed) return state.mode
        return spanAt(value.text, value.selection.start)?.mark ?: state.mode
    }

    /** One edit from the field: [old] was on screen, the keyboard asks for [new]. */
    fun onChange(old: TextFieldValue, new: TextFieldValue, state: TypingState, default: Mark?): Pair<TextFieldValue, TypingState> {
        if (new.text.isEmpty()) return new to TypingState(default)

        if (new.text == old.text) {
            // The caret moved. Just past the closing mark it was inside: step out.
            if (old.selection.collapsed && new.selection.collapsed) {
                val inside = spanAt(old.text, old.selection.start)
                if (inside != null && old.selection.start <= inside.contentEnd && new.selection.start == inside.end) {
                    return stepOut(new.text, inside.end) to TypingState(default)
                }
            }
            // Anywhere else, an inner quote is left behind.
            return new to if (new.selection != old.selection) state.copy(inner = false) else state
        }

        // What was typed: the stretch the two texts don't share.
        val o = old.text
        val n = new.text
        var p = 0
        // Never past where the caret was: typing a mark beside the same mark ("" ) would
        // otherwise read as typed after it.
        val limit = if (old.selection.collapsed) old.selection.start else old.selection.min
        while (p < o.length && p < n.length && p < limit && o[p] == n[p]) p++
        var s = 0
        while (s < o.length - p && s < n.length - p && o[o.length - 1 - s] == n[n.length - 1 - s]) s++
        val removed = o.substring(p, o.length - s)
        val inserted = n.substring(p, n.length - s)
        if (removed.isNotEmpty() || inserted.isEmpty()) return new to state

        val ctx = spanAt(o, p)
        val typed = if (ctx?.mark == Mark.Speech || ctx?.mark == Mark.Action) inserted.replace('"', '\'') else inserted
        val mode = state.mode
        val opens = mode != null && typed.isNotBlank() && (ctx == null || (state.armed && ctx.mark != mode))
        if (opens) {
            val lead = typed.takeWhile { it.isWhitespace() }
            val body = typed.drop(lead.length)
            val text = o.substring(0, p) + lead + mode!!.open + body + mode.close + o.substring(p)
            val caret = p + lead.length + mode.open.length + body.length
            return TextFieldValue(text, TextRange(caret)) to state.copy(armed = false)
        }
        if (typed != inserted) {
            return TextFieldValue(o.substring(0, p) + typed + o.substring(p), TextRange(p + typed.length)) to state
        }
        return new to state
    }

    /** A key: wraps a selection, steps out of its own mark, or arms (or disarms) itself. */
    fun press(value: TextFieldValue, mark: Mark, state: TypingState): Pair<TextFieldValue, TypingState> {
        val t = value.text
        val sel = value.selection
        if (!sel.collapsed) {
            val a = sel.min
            val b = sel.max
            val text = t.substring(0, a) + mark.open + t.substring(a, b) + mark.close + t.substring(b)
            return TextFieldValue(text, TextRange(b + mark.open.length + mark.close.length)) to state.copy(armed = false)
        }
        val ctx = spanAt(t, sel.start)
        if (mark == Mark.Speech && ctx?.mark == Mark.Speech && !state.armed) {
            if (state.inner) {
                // Out of the inner quote: just past its closing ', still in the speech.
                val close = t.indexOf('\'', sel.start)
                val at = if (close in sel.start..ctx.contentEnd) close + 1 else sel.start
                return TextFieldValue(t, TextRange(at)) to state.copy(inner = false)
            }
            val text = t.substring(0, sel.start) + "''" + t.substring(sel.start)
            return TextFieldValue(text, TextRange(sel.start + 1)) to state.copy(inner = true)
        }
        if (ctx?.mark == mark && !state.armed) {
            // Pressing the lit key inside its own mark: out, and plain from here.
            return stepOut(t, ctx.end) to TypingState(null)
        }
        if (lit(value, state) == mark) return value to TypingState(null)
        return value to TypingState(mark, armed = true)
    }

    /** The caret just past a mark, with one space after it (unless one is already there). */
    private fun stepOut(text: String, at: Int): TextFieldValue {
        val spaced = at < text.length && text[at].isWhitespace()
        val t = if (spaced) text else text.substring(0, at) + " " + text.substring(at)
        return TextFieldValue(t, TextRange(at + 1))
    }
}

/**
 * The box's text with its marks applied, as Discord does: *actions* in italics, **bold**
 * bold, "speech" in the speech colour, and the marks themselves faded. Same characters, same
 * positions, so the caret and the keyboard never notice.
 */
class RichMarks(
    private val mark: androidx.compose.ui.graphics.Color,
    private val speech: androidx.compose.ui.graphics.Color,
    private val action: androidx.compose.ui.graphics.Color,
) : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val t = text.text
        val b = androidx.compose.ui.text.AnnotatedString.Builder(text)
        for (sp in RichTyping.spans(t)) {
            when (sp.mark) {
                Mark.Action -> b.addStyle(
                    androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = action),
                    sp.contentStart, sp.contentEnd,
                )
                Mark.Bold -> b.addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), sp.contentStart, sp.contentEnd)
                Mark.Speech -> b.addStyle(androidx.compose.ui.text.SpanStyle(color = speech), sp.contentStart, sp.contentEnd)
            }
            b.addStyle(androidx.compose.ui.text.SpanStyle(color = mark), sp.start, sp.contentStart)
            b.addStyle(androidx.compose.ui.text.SpanStyle(color = mark), sp.contentEnd, sp.end)
        }
        return androidx.compose.ui.text.input.TransformedText(b.toAnnotatedString(), androidx.compose.ui.text.input.OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is RichMarks && other.mark == mark && other.speech == speech && other.action == action
    override fun hashCode() = (mark.hashCode() * 31 + speech.hashCode()) * 31 + action.hashCode()
}
