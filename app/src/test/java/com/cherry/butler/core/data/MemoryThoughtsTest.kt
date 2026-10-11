package com.cherry.butler.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryThoughtsTest {
    @Test
    fun `a thought shows only beside the summary it led to`() {
        val thought = MemoryThoughts.Thought("weighing it", 1_200L, MemoryThoughts.fingerprint("Mia is a knight."))
        assertEquals(thought, MemoryThoughts.forSummary(thought, "Mia is a knight.\n"))
        assertNull(MemoryThoughts.forSummary(thought, "Mia is a knight, retired."))
        assertNull(MemoryThoughts.forSummary(thought, null))
        assertNull(MemoryThoughts.forSummary(null, "Mia is a knight."))
    }
}
