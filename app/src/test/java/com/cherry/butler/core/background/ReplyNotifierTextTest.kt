package com.cherry.butler.core.background

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyNotifierTextTest {

    @Test
    fun plainDropsMarkupAndCollapsesSpace() {
        assertEquals(
            "She looks up. \"You came back.\"",
            ReplyNotifier.plain("*She looks up.*\n\n<b>\"You came back.\"</b>"),
        )
    }

    @Test
    fun firstSentenceStopsAtTheFirstRealEnd() {
        assertEquals(
            "Laine sets the cup down without a word.",
            ReplyNotifier.firstSentence("Laine sets the cup down without a word. The rain keeps on."),
        )
    }

    @Test
    fun firstSentenceSkipsVeryShortOpeners() {
        assertEquals(
            "Oh. You're early, and the kettle isn't even on yet.",
            ReplyNotifier.firstSentence("Oh. You're early, and the kettle isn't even on yet. Sit."),
        )
    }

    @Test
    fun firstSentenceCutsLongRunsAtAWord() {
        val long = "word ".repeat(60).trim()
        val cut = ReplyNotifier.firstSentence(long, max = 30)
        assertEquals("word word word word word word…", cut)
    }

    @Test
    fun wordForIsTheThinkingLinesWordLowerCased() {
        assertEquals("thinking", ReplyNotifier.wordFor(null))
    }
}
