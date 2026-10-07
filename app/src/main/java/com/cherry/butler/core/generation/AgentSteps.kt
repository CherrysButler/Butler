package com.cherry.butler.core.generation

/**
 * Agent mode's progress, written into the reply's thought as marked steps so the thought
 * panel can show them as events (reasoning, draft, check, fixes, done) with what happened
 * inside each. The marks are `⟦kind|label⟧` to open and `⟦/kind⟧` to close; a step with no
 * closing mark is still running. Plain thought text has no marks and is shown as before.
 */
object AgentSteps {
    enum class Kind(val title: String, val doing: String) {
        Reasoning("Reasoning", "Reasoning"),
        Draft("Draft", "Drafting"),
        Check("Check", "Checking"),
        Fix("Fixes", "Fixing"),
        Rewrite("Rewrite", "Rewriting"),
        Finish("Delivered", "Finishing"),
    }

    /** One step: what it was, its label ("1 of 2"), what happened inside, and whether it ended. */
    data class Step(val kind: Kind, val label: String?, val detail: String, val done: Boolean)

    private const val OPEN = '⟦'
    private const val CLOSE = '⟧'

    fun open(kind: Kind, label: String? = null): String = "$OPEN${kind.name.lowercase()}${label?.let { "|$it" }.orEmpty()}$CLOSE\n"
    fun close(kind: Kind): String = "\n$OPEN/${kind.name.lowercase()}$CLOSE\n"

    fun isAgent(text: String): Boolean = text.indexOf(OPEN) >= 0

    /** The steps in [text], in order, or null when it carries no marks. */
    fun parse(text: String): List<Step>? {
        if (!isAgent(text)) return null
        val steps = mutableListOf<Step>()
        var i = 0
        while (true) {
            val a = text.indexOf(OPEN, i)
            if (a < 0) break
            val b = text.indexOf(CLOSE, a)
            if (b < 0) break
            val tag = text.substring(a + 1, b)
            i = b + 1
            if (tag.startsWith("/")) continue
            val name = tag.substringBefore('|')
            val label = tag.substringAfter('|', "").ifEmpty { null }
            val kind = Kind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: continue
            val closing = text.indexOf("$OPEN/$name$CLOSE", i)
            val next = text.indexOf(OPEN, i)
            val end = when {
                closing >= 0 && (next < 0 || closing <= next) -> closing
                next >= 0 -> next
                else -> text.length
            }
            val done = closing >= 0 && closing == end
            steps += Step(kind, label, text.substring(i, end).trim(), done)
            i = if (done) closing + name.length + 3 else end
        }
        return steps
    }

    /** One line for the folded thought: what the agent is doing, or did. */
    fun summary(steps: List<Step>, streaming: Boolean): String {
        if (steps.isEmpty()) return "Agent"
        val last = steps.last()
        if (streaming && !last.done) return last.kind.doing + (last.label?.let { " $it" } ?: "")
        val checks = steps.count { it.kind == Kind.Check }
        val fixes = steps.count { it.kind == Kind.Fix || it.kind == Kind.Rewrite }
        return buildString {
            append("Agent · ")
            append(if (checks == 1) "1 check" else "$checks checks")
            if (fixes > 0) append(", ").append(if (fixes == 1) "1 fix" else "$fixes fixes")
        }
    }
}
