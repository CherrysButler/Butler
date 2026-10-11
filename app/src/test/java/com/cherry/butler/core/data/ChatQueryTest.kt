package com.cherry.butler.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatQueryTest {

    @Test
    fun `plain words have no filter`() {
        val q = ChatQuery.parse("  gayle ")
        assertEquals("gayle", q.text)
        assertNull(q.persona)
    }

    @Test
    fun `a filter being typed at the end`() {
        val q = ChatQuery.parse("gayle persona:mo")
        assertEquals("gayle", q.text)
        assertEquals("mo", q.persona)
        assertTrue(q.typingPersona)
        assertFalse(q.exact)
    }

    @Test
    fun `a picked name is whole and done`() {
        val q = ChatQuery.parse("persona:\"Night Owl\" gayle")
        assertEquals("Night Owl", q.persona)
        assertTrue(q.exact)
        assertFalse(q.typingPersona)
        assertEquals("gayle", q.text)
    }

    @Test
    fun `an empty filter just begun`() {
        val q = ChatQuery.parse("persona:")
        assertEquals("", q.persona)
        assertTrue(q.typingPersona)
        assertEquals("", q.text)
    }

    @Test
    fun `a word that only contains persona is text`() {
        assertNull(ChatQuery.parse("mypersona:x").persona)
    }

    @Test
    fun `picking a suggestion fills the filter and leaves room to type`() {
        assertEquals("persona:\"Night Owl\" ", ChatQuery.withPersona("persona:ni", "Night Owl"))
        assertEquals("persona:\"Mona\" gayle", ChatQuery.withPersona("gayle persona:mo", "Mona"))
        assertEquals("persona:\"Mona\" ", ChatQuery.withPersona("", "Mona"))
    }

    @Test
    fun `starting a filter adds it at the end once`() {
        assertEquals("gayle persona:", ChatQuery.startPersona("gayle"))
        assertEquals("persona:", ChatQuery.startPersona(""))
        assertEquals("persona:mo", ChatQuery.startPersona("persona:mo"))
    }
}
