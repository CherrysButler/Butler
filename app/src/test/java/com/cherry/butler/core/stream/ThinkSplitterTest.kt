package com.cherry.butler.core.stream

import kotlin.test.Test
import kotlin.test.assertEquals

class ThinkSplitterTest {
    /** Feeds [chunks] through a splitter and returns (reply, thought). */
    private fun split(vararg chunks: String, reply: String = "", thought: String = ""): Pair<String, String> {
        val r = StringBuilder(reply)
        val t = StringBuilder(thought)
        val s = ThinkSplitter(r, t)
        chunks.forEach(s::feed)
        s.finish()
        return r.toString() to t.toString()
    }

    @Test
    fun `a closed thought is split from the reply`() {
        assertEquals("She smiles." to "plan it", split("<think>plan it</think>", "She smiles."))
    }

    @Test
    fun `tags split across chunks still split`() {
        assertEquals("Hi." to "hm", split("<thi", "nk>hm</th", "ink>Hi."))
    }

    @Test
    fun `the word think in prose is reply text`() {
        val prose = "\"I think so,\" she says. <3 *thinks about it*"
        assertEquals(prose to "", split(prose))
    }

    @Test
    fun `an unclosed think at the start comes back as the reply`() {
        assertEquals("She walks in. \"Hello.\"" to "", split("<think>", "She walks in. \"Hello.\""))
    }

    @Test
    fun `an unclosed think after reply text stays a thought`() {
        assertEquals("She waves. " to "wait", split("She waves. <think>wait"))
    }

    @Test
    fun `a close with no open moves what came before into the thought`() {
        assertEquals("\n\nShe nods." to "The user wants a nod.", split("The user wants", " a nod.</think>", "\n\nShe nods."))
    }

    @Test
    fun `a later stray close is text-neutral`() {
        assertEquals("A B" to "x", split("<think>x</think>A", "</think> B"))
    }

    @Test
    fun `what was there before is never touched`() {
        // A continued reply: the earlier text and thought stay; only the new part is split.
        val (reply, thought) = split("more</think> and on", reply = "Earlier part, ", thought = "old thought")
        assertEquals("Earlier part,  and on", reply)
        assertEquals("old thoughtmore", thought)
        // And an unclosed open on a continued reply that adds nothing gives back only its own text.
        assertEquals("Earlier part, then" to "old", split("<think>then", reply = "Earlier part, ", thought = "old"))
    }

    @Test
    fun `the thinking tag works like think`() {
        assertEquals("Reply" to "t", split("<thinking>t</thinking>Reply"))
    }
}
