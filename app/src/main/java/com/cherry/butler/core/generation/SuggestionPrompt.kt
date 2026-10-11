package com.cherry.butler.core.generation

import com.cherry.butler.core.data.Perspective
import com.cherry.butler.core.data.remote.dto.PronounsDto

/**
 * The trailing user line Write for me and Enhance send (the envelope's draft): the draft to
 * rewrite, if there is one, then Butler's guidance in the voice the user writes in, then what
 * the Write panel asked for, as an out-of-character note for this one line ([GuidedRetry]).
 *
 * The voice is first person ("I eat the cheese"), third ("she eats the cheese": the persona's
 * pronouns, or their name and they/them when it has none), or second ("you eat the cheese").
 * Write for me always has one; Enhance with none keeps the draft's own.
 */
object SuggestionPrompt {

    /** [name] is who the user plays, for the third person; blank leaves `{{user}}` to be filled. */
    fun line(
        draft: String,
        perspective: Perspective?,
        guidance: String = "",
        name: String = "",
        pronouns: PronounsDto? = null,
    ): String {
        val asked = if (draft.isBlank()) {
            scratch(perspective ?: Perspective.First, name, pronouns)
        } else {
            "${draft.trim()}\n\n${rewrite(perspective, name, pronouns)}"
        }
        val note = guidance.trim().takeIf { it.isNotEmpty() } ?: return asked
        return "$asked\n\n${GuidedRetry.noteForLine(note)}"
    }

    /** Write for me: the user's next line, theirs and short, never the character's. */
    fun scratch(perspective: Perspective, name: String = "", pronouns: PronounsDto? = null): String =
        "(Write my next line for me. ${voice(perspective, name, pronouns).replaceFirstChar { it.uppercase() }}, in the story's style, " +
            "actions in asterisks and speech in quotes. React to what just happened and do one clear thing. Two to four " +
            "short sentences, and finish every one: end on a complete sentence. Never write the other " +
            "character's words or reactions.)"

    /** Enhance my draft: the same line, better written, not a longer scene. */
    fun rewrite(perspective: Perspective?, name: String = "", pronouns: PronounsDto? = null): String {
        val person = perspective?.let { "${voice(it, name, pronouns)}, same tense" } ?: "the same person and tense as my draft"
        return "(Rewrite it as my own line, not a scene: $person, keep what I do " +
            "and say and add nothing new, just make it read well. About the same length as my draft, " +
            "a sentence or two longer at most, and finish every sentence: end on a complete one. Never " +
            "write the other character's reaction.)"
    }

    /** A quick pick in the Write panel: what it says on the chip, and what the model is told. */
    data class Pick(val label: String, val ask: String)

    /** Write for me's picks: the shape of a line, and where it takes the scene. */
    val WRITE_PICKS = listOf(
        Pick("More dialogue", "More of my speech, less narration"),
        Pick("Action only", "Only what I do, in asterisks, no speech"),
        Pick("Short", "One or two sentences at most"),
        Pick("Longer", "Longer, a full paragraph"),
        Pick("Bolder", "Make me bolder and more forward"),
        Pick("Shyer", "Make me shyer and more hesitant"),
        Pick("Ask a question", "Have me ask them a question"),
        Pick("Move the scene on", "Move the scene forward: do something that changes it"),
    )

    /** Enhance's picks: what to do to the draft. */
    val ENHANCE_PICKS = listOf(
        Pick("Fix grammar", "Only fix spelling, grammar and punctuation, and change nothing else"),
        Pick("More descriptive", "More descriptive: what I do, see and feel, in more detail"),
        Pick("More in character", "More in character for who I am playing"),
        Pick("More emotional", "Put more feeling into it"),
        Pick("Shorter", "Shorter and tighter"),
        Pick("Longer", "Longer, with more detail"),
        Pick("More dialogue", "Turn more of it into my speech"),
    )

    /** Picks that undo each other: choosing one lets go of the other. */
    private val OPPOSITES = listOf("Short" to "Longer", "Shorter" to "Longer", "Bolder" to "Shyer", "Action only" to "More dialogue")

    fun picks(rewrite: Boolean): List<Pick> = if (rewrite) ENHANCE_PICKS else WRITE_PICKS

    /** [picked] with [label] added, and whatever pulls against it in the same panel let go. */
    fun toggle(picked: List<String>, label: String, rewrite: Boolean): List<String> {
        if (label in picked) return picked - label
        val offered = picks(rewrite).map { it.label }
        val against = OPPOSITES.mapNotNull { (a, b) -> if (a == label) b else if (b == label) a else null }.filter { it in offered }
        return picked - against.toSet() + label
    }

    /** The picks' instructions and the user's own [words], as one note's text; blank for none. */
    fun guidance(picks: List<String>, words: String, rewrite: Boolean): String {
        val offered = picks(rewrite)
        val asks = picks.map { label -> offered.firstOrNull { it.label == label }?.ask ?: label }
        return (asks + listOfNotNull(words.trim().removeSuffix(".").takeIf { it.isNotEmpty() })).joinToString(". ")
    }

    /** How the line speaks of the user, lower case: "first person as me" and the rest. */
    fun voice(perspective: Perspective, name: String = "", pronouns: PronounsDto? = null): String = when (perspective) {
        Perspective.First -> "first person as me"
        Perspective.Second -> "second person as me, \"you\" for me"
        Perspective.Third -> {
            val who = name.trim().ifEmpty { "{{user}}" }
            val sub = pronouns?.subjective?.trim()?.lowercase().orEmpty()
            val obj = pronouns?.objective?.trim()?.lowercase().orEmpty()
            if (sub.isNotEmpty() && obj.isNotEmpty()) {
                "third person as me, $who, with $sub/$obj pronouns"
            } else {
                "third person as me, by my name, $who, and they/them where a pronoun is needed"
            }
        }
    }
}
