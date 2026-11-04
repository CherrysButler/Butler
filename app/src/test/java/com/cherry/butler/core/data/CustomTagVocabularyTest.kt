package com.cherry.butler.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTagVocabularyTest {
    @Test
    fun `typed text finds tags that start with it first, most seen first`() {
        val v = CustomTagVocabulary()
        v.learn(listOf("ocxoc", "OCxCanon", "#ocxcanon", "canonxocx", "fantasy", "ocxcanon"))
        assertEquals(listOf("ocxcanon", "ocxoc", "canonxocx"), v.suggest("ocx"))
        assertEquals(listOf("ocxcanon", "ocxoc", "canonxocx"), v.suggest("#OCX"))
    }

    @Test
    fun `tags already picked and the typed tag itself are left out`() {
        val v = CustomTagVocabulary()
        v.learn(listOf("ocx", "ocxoc", "ocxcanon"))
        assertEquals(listOf("ocxoc"), v.suggest("ocx", exclude = listOf("#ocxcanon")))
    }

    @Test
    fun `nothing typed suggests nothing, and it keeps across a restore`() {
        val v = CustomTagVocabulary()
        v.learn(listOf("isekai"))
        assertEquals(emptyList<String>(), v.suggest("  "))
        val again = CustomTagVocabulary().apply { restore(v.snapshot()) }
        assertEquals(listOf("isekai"), again.suggest("ise"))
    }
}
