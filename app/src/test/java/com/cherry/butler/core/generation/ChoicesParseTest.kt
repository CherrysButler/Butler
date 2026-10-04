package com.cherry.butler.core.generation

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoicesParseTest {

    @Test
    fun numberedLinesBecomeChoices() {
        val raw = """
            1. *Rain sets the cup down.* "Fine."
            2) "Where's the coffee?"
            3: *walks out without a word*
            4. **"You could at least say happy birthday."**
            5. extra
        """.trimIndent()
        assertEquals(
            listOf("*Rain sets the cup down.* \"Fine.\"", "\"Where's the coffee?\"", "*walks out without a word*", "\"You could at least say happy birthday.\""),
            ChoicesService.parse(raw),
        )
    }

    @Test
    fun unnumberedLinesStillWork() {
        assertEquals(listOf("a", "b"), ChoicesService.parse("a\n\nb"))
    }
}
