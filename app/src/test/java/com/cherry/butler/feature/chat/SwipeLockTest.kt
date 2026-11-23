package com.cherry.butler.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeLockTest {

    /** Decided after 40 px of travel; a swipe from 200 px across. */
    private fun lock() = SwipeLock(decideAt = 40f, reach = 200f)

    private fun SwipeLock.drag(dx: Float, dy: Float, steps: Int) = repeat(steps) { move(dx, dy) }

    @Test
    fun `dragged left far enough is the next reply`() {
        val l = lock()
        l.drag(-10f, 0f, 25)
        assertEquals(SwipeLock.Axis.Across, l.axis)
        assertTrue(l.armed)
        assertEquals(1, l.step)
    }

    @Test
    fun `dragged right far enough is the reply before`() {
        val l = lock()
        l.drag(10f, 1f, 25)
        assertEquals(-1, l.step)
    }

    @Test
    fun `let go short of the reach is nothing`() {
        val l = lock()
        l.drag(-10f, 0f, 15)
        assertEquals(SwipeLock.Axis.Across, l.axis)
        assertFalse(l.armed)
        assertEquals(0, l.step)
    }

    @Test
    fun `brought back under the reach is nothing again`() {
        val l = lock()
        l.drag(-10f, 0f, 25)
        assertTrue(l.armed)
        l.drag(10f, 0f, 10)
        assertFalse(l.armed)
        assertEquals(0, l.step)
    }

    @Test
    fun `a scroll whose thumb turns sideways never becomes a swipe`() {
        val l = lock()
        // Up the screen first.
        l.drag(0f, -10f, 6)
        assertEquals(SwipeLock.Axis.Refused, l.axis)
        // Then all the way across.
        l.drag(20f, -1f, 30)
        assertEquals(SwipeLock.Axis.Refused, l.axis)
        assertFalse(l.armed)
        assertEquals(0, l.step)
    }

    @Test
    fun `a diagonal start is refused`() {
        val l = lock()
        // About 34 degrees off level.
        l.drag(-9f, -6f, 40)
        assertEquals(SwipeLock.Axis.Refused, l.axis)
        assertEquals(0, l.step)
    }

    @Test
    fun `a level start with a little wobble is still a swipe`() {
        val l = lock()
        // About 14 degrees off level.
        l.drag(-8f, 2f, 30)
        assertEquals(SwipeLock.Axis.Across, l.axis)
        assertEquals(1, l.step)
    }

    @Test
    fun `a swipe that wanders far off level by its end does not count`() {
        val l = lock()
        l.drag(-10f, 0f, 10)
        assertEquals(SwipeLock.Axis.Across, l.axis)
        // Across 220 in all, but 150 down.
        l.drag(-8f, 10f, 15)
        assertFalse(l.armed)
        assertEquals(0, l.step)
    }

    @Test
    fun `nothing is decided inside the first few pixels`() {
        val l = lock()
        l.drag(0f, -6f, 3)
        assertEquals(SwipeLock.Axis.Undecided, l.axis)
    }
}
