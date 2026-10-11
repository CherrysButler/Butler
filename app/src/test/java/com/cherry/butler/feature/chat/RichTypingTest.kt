package com.cherry.butler.feature.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RichTypingTest {

    private fun v(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    /** Types [chars] one at a time from [start], as a keyboard would. */
    private fun type(start: TextFieldValue, state: TypingState, chars: String, default: Mark? = Mark.Action): Pair<TextFieldValue, TypingState> {
        var value = start
        var st = state
        for (c in chars) {
            val at = value.selection.start
            val next = TextFieldValue(value.text.substring(0, at) + c + value.text.substring(at), TextRange(at + 1))
            val (out, s) = RichTyping.onChange(value, next, st, default)
            value = out
            st = s
        }
        return value to st
    }

    @Test
    fun `typing into an empty box opens the default mark around the words`() {
        val (value, _) = type(v(""), TypingState(Mark.Action), "she smiles")
        assertEquals("*she smiles*", value.text)
        assertEquals(11, value.selection.start)
    }

    @Test
    fun `plain default types plainly`() {
        val (value, _) = type(v(""), TypingState(null), "hi", default = null)
        assertEquals("hi", value.text)
    }

    @Test
    fun `a key nests its mark inside the one the caret is in`() {
        var (value, state) = type(v(""), TypingState(Mark.Action), "she ")
        val pressed = RichTyping.press(value, Mark.Bold, state)
        value = pressed.first
        state = pressed.second
        val typed = type(value, state, "really")
        assertEquals("*she **really***", typed.first.text)
        assertEquals(Mark.Bold, RichTyping.spanAt(typed.first.text, typed.first.selection.start)?.mark)
    }

    @Test
    fun `a quote typed inside speech pairs as apostrophes`() {
        val (value, _) = type(v("\"she said  today\"", 10), TypingState(Mark.Speech), "\"no\"", default = Mark.Speech)
        assertEquals("\"she said 'no' today\"", value.text)
        assertEquals(14, value.selection.start)
    }

    @Test
    fun `typed marks pair up and step over their closing side`() {
        type(v(""), TypingState(null), "\"", default = null).first.let {
            assertEquals("\"\"", it.text)
            assertEquals(1, it.selection.start)
        }
        val (spoke, _) = type(v(""), TypingState(null), "\"hey\"", default = null)
        assertEquals("\"hey\"", spoke.text)
        assertEquals(5, spoke.selection.start)
        val (bold, _) = type(v(""), TypingState(null), "**hi*", default = null)
        assertEquals("**hi**", bold.text)
        assertEquals(6, bold.selection.start)
    }

    @Test
    fun `a mark typed over a selection wraps it`() {
        val old = TextFieldValue("say hey now", TextRange(4, 7))
        val (out, _) = RichTyping.onChange(old, TextFieldValue("say \" now", TextRange(5)), TypingState(null), null)
        assertEquals("say \"hey\" now", out.text)
        assertEquals(TextRange(5, 8), out.selection)
    }

    @Test
    fun `a mark after a letter is typed alone`() {
        assertEquals("don\"", type(v("don"), TypingState(null), "\"", default = null).first.text)
    }

    @Test
    fun `stepping past the closing mark leaves it with a space, back to the default`() {
        val (typed, state) = type(v(""), TypingState(Mark.Speech), "hi", default = Mark.Action)
        // The caret moves from inside "hi" to just past the closing quote.
        val (out, after) = RichTyping.onChange(typed, typed.copy(selection = TextRange(typed.text.length)), state, Mark.Action)
        assertEquals("\"hi\" ", out.text)
        assertEquals(5, out.selection.start)
        assertEquals(TypingState(Mark.Action), after)
        val (more, _) = type(out, after, "waves")
        assertEquals("\"hi\" *waves*", more.text)
    }

    @Test
    fun `the lit key inside its own mark steps out to plain`() {
        val (typed, state) = type(v(""), TypingState(Mark.Action), "nods")
        val (out, after) = RichTyping.press(typed, Mark.Action, state)
        assertEquals("*nods* ", out.text)
        assertNull(after.mode)
    }

    @Test
    fun `a selection is wrapped by the key`() {
        val value = TextFieldValue("I really mean it", TextRange(2, 8))
        val (out, _) = RichTyping.press(value, Mark.Bold, TypingState(null))
        assertEquals("I **really** mean it", out.text)
    }

    @Test
    fun `spans read nested marks`() {
        val spans = RichTyping.spans("*she says \"hi\" and **means** it*")
        assertEquals(listOf(Mark.Action, Mark.Speech, Mark.Bold), spans.map { it.mark })
    }

    @Test
    fun `a quote typed inside an action becomes an apostrophe`() {
        val (value, _) = type(v(""), TypingState(Mark.Action), "she mutters \"later\"")
        assertEquals("*she mutters 'later'*", value.text)
    }

    @Test
    fun `inside speech the quote key opens an inner quote, and again steps out of it`() {
        var (value, state) = type(v(""), TypingState(Mark.Speech), "he said ", default = Mark.Speech)
        assertEquals(true, RichTyping.inSpeech(value, state))
        RichTyping.press(value, Mark.Speech, state).let { value = it.first; state = it.second }
        assertEquals("\"he said ''\"", value.text)
        assertEquals(true, state.inner)
        type(value, state, "run", default = Mark.Speech).let { value = it.first; state = it.second }
        assertEquals("\"he said 'run'\"", value.text)
        RichTyping.press(value, Mark.Speech, state).let { value = it.first; state = it.second }
        assertEquals(false, state.inner)
        type(value, state, " now", default = Mark.Speech).let { value = it.first; state = it.second }
        assertEquals("\"he said 'run' now\"", value.text)
    }

    /** Deletes [count] characters before the caret, one backspace at a time. */
    private fun backspace(start: TextFieldValue, state: TypingState, count: Int, default: Mark? = Mark.Action): Pair<TextFieldValue, TypingState> {
        var value = start
        var st = state
        repeat(count) {
            val at = value.selection.start
            val next = TextFieldValue(value.text.substring(0, at - 1) + value.text.substring(at), TextRange(at - 1))
            RichTyping.onChange(value, next, st, default).let { value = it.first; st = it.second }
        }
        return value to st
    }

    @Test
    fun `the quote key inside an action quotes with single quotes`() {
        var (value, state) = type(v(""), TypingState(Mark.Action), "she reads ")
        assertEquals(true, RichTyping.inSpeech(value, state))
        RichTyping.press(value, Mark.Speech, state).let { value = it.first; state = it.second }
        type(value, state, "Dune").let { value = it.first; state = it.second }
        assertEquals("*she reads 'Dune'*", value.text)
    }

    @Test
    fun `a star typed by hand is not wrapped again`() {
        val (value, _) = type(v(""), TypingState(Mark.Action), "*hi*")
        assertEquals("*hi*", value.text)
    }

    @Test
    fun `deleting a mark's words leaves no empty pair, and typing comes back in it`() {
        var (value, state) = type(v(""), TypingState(Mark.Action), "hi")
        backspace(value, state, 2).let { value = it.first; state = it.second }
        assertEquals("", value.text)
        type(value, state, "he").let { value = it.first; state = it.second }
        assertEquals("*he*", value.text)
    }

    @Test
    fun `emptying a mark in the middle of a line takes its marks out`() {
        val start = v("a *hi* b", 5)
        val (value, _) = backspace(start, TypingState(null), 2, default = null)
        assertEquals("a  b", value.text)
        assertEquals(2, value.selection.start)
    }

    @Test
    fun `deleting one side of a mark with words deletes just that side`() {
        backspace(v("*hi*", 4), TypingState(null), 1, default = null).first.let { assertEquals("*hi", it.text) }
        backspace(v("\"hi\" yes", 1), TypingState(null), 1, default = null).first.let {
            assertEquals("hi\" yes", it.text)
            assertEquals(0, it.selection.start)
        }
    }

    @Test
    fun `deleting one side of an empty pair takes both`() {
        backspace(v("a \"\"", 3), TypingState(null), 1, default = null).first.let {
            assertEquals("a ", it.text)
            assertEquals(2, it.selection.start)
        }
    }

    @Test
    fun `a mark typed by hand or a paste with marks is never wrapped`() {
        assertEquals("**", type(v(""), TypingState(Mark.Action), "*").first.text)
        val paste = "*she waves* \"hi\""
        val (value, _) = RichTyping.onChange(v(""), v(paste), TypingState(Mark.Action), Mark.Action)
        assertEquals(paste, value.text)
    }

    @Test
    fun `typing the closing star steps over it`() {
        val (value, _) = type(v(""), TypingState(Mark.Action), "nods*")
        assertEquals("*nods*", value.text)
        assertEquals(6, value.selection.start)
    }

    @Test
    fun `an emptied bold inside an action comes back bold`() {
        var value = v("*she **really** wants*", 13)
        var state = TypingState(Mark.Action)
        backspace(value, state, 6).let { value = it.first; state = it.second }
        assertEquals("*she  wants*", value.text)
        type(value, state, "so").let { value = it.first; state = it.second }
        assertEquals("*she **so** wants*", value.text)
    }
}
