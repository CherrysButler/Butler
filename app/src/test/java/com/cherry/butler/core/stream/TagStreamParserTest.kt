package com.cherry.butler.core.stream

import com.cherry.butler.core.stream.TagStreamParser.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class TagStreamParserTest {

    private fun parser() = TagStreamParser(listOf("think", "thinking", "butter"))

    @Test
    fun `a tag split across chunks resolves without leaking a partial`() {
        val p = parser()
        assertEquals(listOf(Event.Text("Hello ")), p.feed("Hello <thi"))
        assertEquals(
            listOf(Event.Open("think"), Event.Text("x"), Event.Close("think"), Event.Text("y")),
            p.feed("nk>x</think>y"),
        )
        assertEquals(emptyList(), p.finish())
    }

    @Test
    fun `unknown tags pass through as text`() {
        val p = parser()
        val events = p.feed("she <grins> at you")
        assertEquals("she <grins> at you", events.joinToString("") { (it as Event.Text).text })
        assertEquals(emptyList(), events.filter { it !is Event.Text })
    }

    @Test
    fun `a heart is not a tag`() {
        val p = parser()
        val events = p.feed("love you <3 forever")
        assertEquals("love you <3 forever", events.joinToString("") { (it as Event.Text).text })
    }

    @Test
    fun `a newline releases a pending bracket`() {
        val p = parser()
        val events = p.feed("a < b\nc")
        assertEquals("a < b\nc", events.joinToString("") { (it as Event.Text).text })
    }

    @Test
    fun `finish flushes an unterminated candidate`() {
        val p = parser()
        assertEquals(listOf(Event.Text("end ")), p.feed("end <thi"))
        assertEquals(listOf(Event.Text("<thi")), p.finish())
    }

    @Test
    fun `tag names are case-insensitive and tolerate whitespace`() {
        val p = parser()
        assertEquals(listOf(Event.Open("think"), Event.Close("think")), p.feed("<Think ></THINK>"))
    }

    @Test
    fun `a second bracket inside a candidate restarts the candidate`() {
        val p = parser()
        val events = p.feed("<a<think>t</think>")
        assertEquals(listOf(Event.Text("<a"), Event.Open("think"), Event.Text("t"), Event.Close("think")), events)
    }

    @Test
    fun `an over-long candidate is released as text`() {
        val p = parser()
        val long = "<" + "x".repeat(40) + ">"
        val events = p.feed(long)
        assertEquals(long, events.joinToString("") { (it as Event.Text).text })
    }
}
