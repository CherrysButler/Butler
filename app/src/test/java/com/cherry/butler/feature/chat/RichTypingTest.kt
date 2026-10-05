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
    fun `a quote typed inside speech becomes an apostrophe`() {
        val (value, _) = type(v(""), TypingState(Mark.Speech), "she said \"no\"", default = Mark.Speech)
        assertEquals("\"she said 'no'\"", value.text)
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
}
