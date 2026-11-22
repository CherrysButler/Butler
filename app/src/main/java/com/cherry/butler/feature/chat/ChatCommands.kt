package com.cherry.butler.feature.chat

import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.generation.ReplyLength

/** One of Butler's own commands, typed after `/` in the message box. */
data class SlashCommand(
    val name: String,
    val summary: String,
    val beta: Boolean = false,
    /** What can follow it, offered to tap once its name and a space are typed. */
    val options: List<String> = emptyList(),
)

/**
 * The message box's `/` commands: Butler's own, never sent to the character. What each one
 * typed means ([parse]), and which are offered ([available]).
 */
object ChatCommands {
    sealed interface Command {
        data class Learn(val focus: String) : Command
        /** A note for the next reply only: sent with it, never shown in the chat. */
        data class Note(val text: String) : Command
        /** A new reply to the last one, changed as [text] says (blank: just again). */
        data class Retry(val text: String) : Command
        /** null: the level typed isn't one. */
        data class Thinking(val level: OpenRouterOptions.Thinking?) : Command
        data class Length(val length: ReplyLength?) : Command
        data class Remember(val text: String) : Command
        /** null: neither on nor off was typed. */
        data class Agent(val on: Boolean?) : Command
    }

    private val PATTERN = Regex("""(?is)^/([a-z]+)(?:\s+(.*))?$""")

    /** What [text] asks for, or null when it isn't one of the commands (then it's sent as it is). */
    fun parse(text: String): Command? {
        val m = PATTERN.matchEntire(text.trim()) ?: return null
        val arg = m.groupValues[2].trim()
        return when (m.groupValues[1].lowercase()) {
            "learn" -> Command.Learn(arg)
            "note" -> Command.Note(arg)
            "retry" -> Command.Retry(arg)
            "thinking", "think" -> Command.Thinking(thinking(arg))
            "length" -> Command.Length(ReplyLength.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) || it.label.equals(arg, ignoreCase = true) })
            "remember" -> Command.Remember(arg)
            "agent" -> Command.Agent(when (arg.lowercase()) { "on", "yes" -> true; "off", "no" -> false; else -> null })
            else -> null
        }
    }

    private fun thinking(arg: String): OpenRouterOptions.Thinking? {
        val a = arg.lowercase().replace(" ", "")
        return when (a) {
            "default", "auto" -> OpenRouterOptions.Thinking.Default
            "off", "none", "no" -> OpenRouterOptions.Thinking.Off
            "xhigh", "extrahigh" -> OpenRouterOptions.Thinking.XHigh
            else -> OpenRouterOptions.Thinking.entries.firstOrNull { it.name.equals(a, ignoreCase = true) }
        }
    }

    /** The commands to offer: [lessons] adds Lessons' own; [openRouter] offers its extra thinking levels. */
    fun available(lessons: Boolean, openRouter: Boolean): List<SlashCommand> = buildList {
        add(SlashCommand("/note", "A note for the next reply only, never shown in the chat"))
        add(SlashCommand("/retry", "A new reply to the last one; add what to change"))
        add(SlashCommand(
            "/thinking", "How hard your proxy's model thinks",
            options = listOf("default", "off", "minimal", "low", "medium", "high") + if (openRouter) listOf("xhigh", "max") else emptyList(),
        ))
        add(SlashCommand("/length", "How long replies are", options = listOf("default", "short", "medium", "long")))
        add(SlashCommand("/agent", "Agent mode: draft, check, fix, then deliver", options = listOf("on", "off")))
        if (lessons) {
            add(SlashCommand("/learn", "Learn from your OOC notes since last time", beta = true))
            add(SlashCommand("/remember", "Add a note to your Lessons straight away", beta = true))
        }
    }

    /**
     * What to offer for what's typed: commands while the name is being typed (`/th`), the
     * command's options once its name and a space are (`/thinking l`). Nothing past that.
     */
    fun offers(typed: String, commands: List<SlashCommand>): List<Offer> {
        if (!typed.startsWith("/") || '\n' in typed) return emptyList()
        val space = typed.indexOf(' ')
        if (space < 0) {
            return commands.filter { it.name.startsWith(typed, ignoreCase = true) && !it.name.equals(typed, ignoreCase = true) }
                .map { Offer(it.name, it.summary, it.name + " ", it.beta) }
        }
        val name = typed.substring(0, space)
        val rest = typed.substring(space + 1)
        if (' ' in rest) return emptyList()
        val command = commands.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return emptyList()
        return command.options.filter { it.startsWith(rest, ignoreCase = true) && !it.equals(rest, ignoreCase = true) }
            .map { Offer(it, "", "${command.name} $it", false, optionOf = command.name) }
    }

    /** One line of the list over the box: what it shows, and what a tap puts in the box. */
    data class Offer(
        val label: String,
        val summary: String,
        val fill: String,
        val beta: Boolean,
        /** An option of this command (`/thinking`), rather than a command itself. */
        val optionOf: String? = null,
    )
}
