package com.cherry.butler.core.generation

import android.util.Log
import com.cherry.butler.core.data.AgentPrefs
import com.cherry.butler.core.network.ApiError
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent mode (beta): the reply as an agent's work, not one shot.
 *
 * 1. **Draft.** The assembled payload goes to the proxy as usual; the reply is collected
 *    rather than shown.
 * 2. **Check.** The same context, the draft as the assistant's turn, and one more user
 *    turn: the goals, and an order to judge the draft against each one strictly and answer
 *    in JSON with exact edits (`find` copied verbatim from the draft, `replace` its new
 *    text), or a whole rewrite when the draft needs more than a few.
 * 3. **Fix.** The edits are applied to the draft as to a file: exact text first, then the
 *    same words with any whitespace between them. Edits that can't be placed, or none with
 *    a failing verdict, fall back to the rewrite, or to asking for one.
 * 4. Again, up to the effort's rounds, until the check passes. Then the text is delivered.
 *
 * The goals are Butler's rubric for a roleplay reply plus the user's own lines. Every call
 * streams, so a reasoning model's thinking shows as it happens and Stop cuts it at once.
 * Progress goes out as the reply's thought, marked as steps ([AgentSteps]) that the thought
 * panel shows as events with what happened inside each. Proxies only (the loop needs the
 * payload in hand), and meant for reasoning models; the check is asked at a low temperature.
 */
@Singleton
class AgentLoop @Inject constructor(
    private val prefs: AgentPrefs,
    private val json: Json,
) {
    /** The loop's way to the proxy, provided by the transport: a streamed reply to [payload]. */
    fun interface Hop {
        suspend fun stream(payload: JsonObject, onEvent: suspend (GenerationEvent) -> Unit)
    }

    /** Whether a generation in [mode] goes through the loop. */
    fun applies(mode: String?): Boolean = prefs.enabled.value && mode in REPLY_MODES

    suspend fun run(out: FlowCollector<GenerationEvent>, payload: JsonObject, mode: String?, hop: Hop) {
        val effort = prefs.effort.value
        suspend fun say(text: String) = out.emit(GenerationEvent.Reasoning(text))

        // Draft: the model's own reasoning shows as it comes; the text is kept for the panel.
        say(AgentSteps.open(AgentSteps.Kind.Reasoning))
        val draft = StringBuilder()
        hop.stream(payload) { e ->
            when (e) {
                is GenerationEvent.Delta -> draft.append(e.text)
                is GenerationEvent.Reasoning -> out.emit(e)
                is GenerationEvent.Meta -> out.emit(e)
                GenerationEvent.Done -> Unit
            }
        }
        say(AgentSteps.close(AgentSteps.Kind.Reasoning))
        var text = strip(draft.toString()).trim()
        if (text.isEmpty()) throw ApiError.Api(0, janitorCode = "AGENT_EMPTY", serverMessage = "Nothing came back for the draft.", retryable = true)
        say(AgentSteps.open(AgentSteps.Kind.Draft) + text + AgentSteps.close(AgentSteps.Kind.Draft))

        val goals = goals(mode)
        var checks = 0
        for (round in 1..effort.rounds) {
            checks++
            say(AgentSteps.open(AgentSteps.Kind.Check, "$round of ${effort.rounds}"))
            val verdict = runCatching { check(payload, text, goals, hop, out) }
                .onFailure { Log.w(TAG, "check $round failed", it) }
                .getOrNull()
            if (verdict == null) {
                say("\nThe check didn't answer in form; the draft stands." + AgentSteps.close(AgentSteps.Kind.Check))
                break
            }
            if (verdict.pass) {
                say("\nEvery goal met." + AgentSteps.close(AgentSteps.Kind.Check))
                break
            }
            say(buildString {
                append("\n")
                if (verdict.problems.isEmpty()) append("Something to fix.\n")
                verdict.problems.forEach { append("· ").append(it.take(300)).append('\n') }
            } + AgentSteps.close(AgentSteps.Kind.Check))

            val (edited, applied, missed, placed) = apply(text, verdict.edits)
            val rewriteOk = !verdict.rewrite.isNullOrBlank()
            when {
                applied > 0 && (missed == 0 || !rewriteOk) -> {
                    text = edited
                    say(AgentSteps.open(AgentSteps.Kind.Fix) + buildString {
                        append(if (applied == 1) "1 edit placed" else "$applied edits placed")
                        if (missed > 0) append(", $missed couldn't be")
                        append('\n')
                        placed.forEach { (f, r) -> append("− ").append(f.take(160)).append("\n+ ").append(r.take(160)).append('\n') }
                    } + AgentSteps.close(AgentSteps.Kind.Fix))
                }
                rewriteOk -> {
                    text = verdict.rewrite!!.trim()
                    say(AgentSteps.open(AgentSteps.Kind.Rewrite) + "The whole reply, rewritten:\n" + text + AgentSteps.close(AgentSteps.Kind.Rewrite))
                }
                else -> {
                    say(AgentSteps.open(AgentSteps.Kind.Rewrite))
                    val again = rewrite(payload, text, verdict.problems, hop, out)?.trim()?.ifEmpty { null }
                    if (again != null) {
                        text = again
                        say("\nRewritten:\n$text" + AgentSteps.close(AgentSteps.Kind.Rewrite))
                    } else {
                        say("\nNo rewrite came back; the draft stands." + AgentSteps.close(AgentSteps.Kind.Rewrite))
                    }
                }
            }
        }
        say(AgentSteps.open(AgentSteps.Kind.Finish) + "After ${if (checks == 1) "1 check" else "$checks checks"}." + AgentSteps.close(AgentSteps.Kind.Finish))
        out.emit(GenerationEvent.Delta(text))
        out.emit(GenerationEvent.Done)
    }

    private class Verdict(val pass: Boolean, val problems: List<String>, val edits: List<Pair<String, String>>, val rewrite: String?)

    /** The check: the context, the draft as the assistant, the goals as one more user turn; JSON back. */
    private suspend fun check(payload: JsonObject, draft: String, goals: String, hop: Hop, out: FlowCollector<GenerationEvent>): Verdict? {
        val ask = buildString {
            append(CHECK_HEAD).append("\n\nGOALS\n").append(goals)
            append("\n\nDRAFT\n<draft>\n").append(draft).append("\n</draft>\n\n").append(CHECK_TAIL.trimIndent())
        }
        val answer = collect(judging(payload, draft, ask), hop, out)
        val obj = jsonIn(answer, json) ?: return null
        val verdict = obj["verdict"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val problems = obj["problems"]?.let { runCatching { it.jsonArray.mapNotNull { p -> p.jsonPrimitive.contentOrNull } }.getOrNull() }.orEmpty()
        val edits = obj["edits"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().mapNotNull { e ->
            val o = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val find = o["find"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val replace = o["replace"]?.jsonPrimitive?.contentOrNull ?: ""
            if (find.isBlank()) null else find to replace
        }
        val rewrite = obj["rewrite"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }
        val pass = verdict == "pass" || (verdict == null && problems.isEmpty() && edits.isEmpty() && rewrite == null)
        return Verdict(pass, problems, edits, rewrite)
    }

    /** A whole new reply when edits couldn't be placed: the problems named, the reply only. */
    private suspend fun rewrite(payload: JsonObject, draft: String, problems: List<String>, hop: Hop, out: FlowCollector<GenerationEvent>): String? {
        val ask = buildString {
            append("Rewrite the draft reply below so that these problems are gone, changing nothing else that works:\n")
            problems.forEach { append("- ").append(it).append('\n') }
            append("\n<draft>\n").append(draft).append("\n</draft>\n\nAnswer with the corrected reply only: no notes, no tags, no preamble.")
        }
        return runCatching { strip(collect(judging(payload, draft, ask, temperature = 0.6), hop, out)) }.getOrNull()
    }

    /** Streams one judging call: its reasoning into the thought as it comes, its text returned whole. */
    private suspend fun collect(payload: JsonObject, hop: Hop, out: FlowCollector<GenerationEvent>): String {
        val text = StringBuilder()
        hop.stream(payload) { e ->
            when (e) {
                is GenerationEvent.Delta -> text.append(e.text)
                is GenerationEvent.Reasoning -> out.emit(e)
                else -> Unit
            }
        }
        return text.toString()
    }

    /** The payload for a judging call: the same context, the draft as the assistant's turn, [ask] as the user's. */
    private fun judging(payload: JsonObject, draft: String, ask: String, temperature: Double = 0.2): JsonObject {
        val messages = payload["messages"]?.jsonArray.orEmpty().toMutableList()
        messages += buildJsonObject { put("role", "assistant"); put("content", draft) }
        messages += buildJsonObject { put("role", "user"); put("content", ask) }
        return JsonObject(
            payload.toMutableMap().apply {
                put("messages", JsonArray(messages))
                put("stream", JsonPrimitive(true))
                put("temperature", JsonPrimitive(temperature))
                remove("stop")
            },
        )
    }

    private fun goals(mode: String?): String = buildString {
        append(RUBRIC)
        if (mode == "CONTINUE") append("\n- The draft continues the previous reply mid-flow; it must pick up exactly where that left off, not restart.")
        val own = prefs.goals.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (own.isNotEmpty()) {
            append("\nThe user's own goals, which count as much as the rest:\n")
            own.forEach { append("- ").append(it).append('\n') }
        }
    }

    /** What applying edits gave: the text, how many were placed, how many weren't, and the ones placed. */
    data class Applied(val text: String, val applied: Int, val missed: Int, val placed: List<Pair<String, String>>)

    internal companion object {
        const val TAG = "AgentLoop"
        val REPLY_MODES = setOf("NEW", "ALTERNATIVE", "CONTINUE")

        /** Edits applied in order: exact text first, then the same words across any whitespace. */
        internal fun apply(text: String, edits: List<Pair<String, String>>): Applied {
            var out = text
            var applied = 0
            var missed = 0
            val placed = mutableListOf<Pair<String, String>>()
            for ((find, replace) in edits) {
                val at = out.indexOf(find)
                if (at >= 0) {
                    out = out.substring(0, at) + replace + out.substring(at + find.length)
                    applied++
                    placed += find to replace
                    continue
                }
                val loose = Regex(find.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) })
                val m = loose.find(out)
                if (m != null) {
                    out = out.substring(0, m.range.first) + replace + out.substring(m.range.last + 1)
                    applied++
                    placed += find to replace
                } else {
                    missed++
                }
            }
            return Applied(out, applied, missed, placed)
        }

        /** The first JSON object in [text], reasoning and fences aside. */
        internal fun jsonIn(text: String, json: Json): JsonObject? {
            val cleaned = strip(text)
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching { json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject }.getOrNull()
        }

        internal fun strip(text: String): String = text
            .replace(Regex("(?is)<think(?:ing)?>.*?</think(?:ing)?>"), "")
            .replace(Regex("(?s)```(?:json)?\\s*(.*?)```")) { it.groupValues[1] }

        val RUBRIC = """
            - Stays in character as the character defined in the system prompt, consistent with that definition and with everything that has happened so far.
            - Answers what the user's last message actually said and did; nothing in it is ignored or contradicted.
            - Never writes the user's own actions, words, thoughts or feelings; only the character's and the world's.
            - Keeps the roleplay's form: the same use of asterisks for narration and quotes for speech, the same person and tense, paragraphs as before, and a length close to the recent replies.
            - Does not repeat phrases, images or beats from the previous replies; moves the scene forward with something new.
            - No out-of-character notes, summaries, lists, headings or questions to the reader; the reply is the reply.
        """.trimIndent()

        const val CHECK_HEAD = "You are now the strict editor of the reply you just wrote, which is given below as DRAFT. Judge it against every goal, one by one, with no benefit of the doubt."
        const val CHECK_TAIL = """
            Answer with JSON only, nothing before or after it, in exactly this shape:
            {"verdict":"pass" or "fix","problems":["one line per failed goal, saying what is wrong and where"],"edits":[{"find":"text copied exactly from the draft","replace":"its corrected text"}],"rewrite":null}
            Rules: "pass" only when every goal is met. For a "fix", prefer a few exact edits; each "find" must be copied verbatim from the draft so it can be located, and "replace" is the full corrected text for that spot (an empty "replace" deletes it). If the draft needs more than a few edits, leave "edits" empty and put the whole corrected reply in "rewrite". Never mention the goals, the check or yourself in any replacement text.
        """
    }
}
