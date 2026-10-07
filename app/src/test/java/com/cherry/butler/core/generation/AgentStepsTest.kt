package com.cherry.butler.core.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStepsTest {

    @Test
    fun `plain thought has no steps`() {
        assertNull(AgentSteps.parse("Just thinking about the scene."))
    }

    @Test
    fun `steps come back in order, closed or still running`() {
        val text = AgentSteps.open(AgentSteps.Kind.Reasoning) + "hmm" + AgentSteps.close(AgentSteps.Kind.Reasoning) +
            AgentSteps.open(AgentSteps.Kind.Draft) + "The draft." + AgentSteps.close(AgentSteps.Kind.Draft) +
            AgentSteps.open(AgentSteps.Kind.Check, "1 of 2") + "judging"
        val steps = AgentSteps.parse(text)!!
        assertEquals(listOf(AgentSteps.Kind.Reasoning, AgentSteps.Kind.Draft, AgentSteps.Kind.Check), steps.map { it.kind })
        assertEquals("hmm", steps[0].detail)
        assertEquals("The draft.", steps[1].detail)
        assertTrue(steps[0].done && steps[1].done)
        assertFalse(steps[2].done)
        assertEquals("1 of 2", steps[2].label)
        assertEquals("judging", steps[2].detail)
    }

    @Test
    fun `an empty reasoning step is kept as a step but empty`() {
        val steps = AgentSteps.parse(AgentSteps.open(AgentSteps.Kind.Reasoning) + AgentSteps.close(AgentSteps.Kind.Reasoning))!!
        assertEquals(1, steps.size)
        assertEquals("", steps[0].detail)
    }

    @Test
    fun `the summary names the work`() {
        val steps = AgentSteps.parse(
            AgentSteps.open(AgentSteps.Kind.Draft) + "d" + AgentSteps.close(AgentSteps.Kind.Draft) +
                AgentSteps.open(AgentSteps.Kind.Check, "1 of 2") + "c" + AgentSteps.close(AgentSteps.Kind.Check) +
                AgentSteps.open(AgentSteps.Kind.Fix) + "f" + AgentSteps.close(AgentSteps.Kind.Fix) +
                AgentSteps.open(AgentSteps.Kind.Check, "2 of 2") + "c" + AgentSteps.close(AgentSteps.Kind.Check) +
                AgentSteps.open(AgentSteps.Kind.Finish) + "ok" + AgentSteps.close(AgentSteps.Kind.Finish),
        )!!
        assertEquals("Agent · 2 checks, 1 fix", AgentSteps.summary(steps, streaming = false))
        val running = AgentSteps.parse(AgentSteps.open(AgentSteps.Kind.Check, "1 of 2") + "…")!!
        assertEquals("Checking 1 of 2", AgentSteps.summary(running, streaming = true))
    }
}
