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
 * The goals are Butler's rubric for a roleplay reply plus the user's own lines. Progress
 * goes out as reasoning, so the thought panel shows what the agent did. Proxies only (the
 * loop needs the payload in hand), and meant for reasoning models; the check is asked at a
 * low temperature.
 */
@Singleton
class AgentLoop @Inject constructor(
    private val prefs: AgentPrefs,
    private val json: Json,
) {
    /** The two kinds of call the loop makes to the proxy, provided by the transport. */
    interface Hop {
        /** Streams [payload]'s reply, handing each event over as it comes. */
        suspend fun stream(payload: JsonObject, onEvent: suspend (GenerationEvent) -> Unit)
        /** [payload]'s whole reply text, in one piece. */
        suspend fun complete(payload: JsonObject): String
    }

    /** Whether a generation in [mode] goes through the loop. */
    fun applies(mode: String?): Boolean = prefs.enabled.value && mode in REPLY_MODES

    suspend fun run(out: FlowCollector<GenerationEvent>, payload: JsonObject, mode: String?, hop: Hop) {
        val effort = prefs.effort.value
        out.emit(GenerationEvent.Reasoning("Agent · drafting\n"))
        val draft = StringBuilder()
        hop.stream(payload) { e ->
            when (e) {
                is GenerationEvent.Delta -> draft.append(e.text)
                is GenerationEvent.Reasoning -> out.emit(e)
                is GenerationEvent.Meta -> out.emit(e)
                GenerationEvent.Done -> Unit
            }
        }
        var text = strip(draft.toString()).trim()
        if (text.isEmpty()) throw ApiError.Api(0, janitorCode = "AGENT_EMPTY", serverMessage = "Nothing came back for the draft.", retryable = true)

        val goals = goals(mode)
        for (round in 1..effort.rounds) {
            out.emit(GenerationEvent.Reasoning("\nAgent · check $round of ${effort.rounds}\n"))
            val verdict = runCatching { check(payload, text, goals, hop) }
                .onFailure { Log.w(TAG, "check $round failed", it) }
                .getOrNull()
            if (verdict == null) {
                out.emit(GenerationEvent.Reasoning("The check didn't answer in form; delivering the draft as it is.\n"))
                break
            }
            if (verdict.pass) {
                out.emit(GenerationEvent.Reasoning("Every goal met.\n"))
                break
            }
            if (verdict.problems.isNotEmpty()) {
                out.emit(GenerationEvent.Reasoning(verdict.problems.joinToString("") { "· ${it.take(200)}\n" }))
            }
            val (edited, applied, missed) = apply(text, verdict.edits)
            val rewriteOk = verdict.rewrite != null && verdict.rewrite.isNotBlank()
            text = when {
                applied > 0 && missed == 0 -> edited
                rewriteOk -> verdict.rewrite!!.trim()
                applied > 0 -> edited
                else -> rewrite(payload, text, verdict.problems, hop)?.trim()?.ifEmpty { null } ?: text
            }
            out.emit(
                GenerationEvent.Reasoning(
                    when {
                        applied > 0 && missed == 0 -> "Fixed: $applied edit${if (applied == 1) "" else "s"} placed.\n"
                        applied > 0 -> "Fixed: $applied placed, $missed couldn't be.\n"
                        rewriteOk -> "Rewritten.\n"
                        else -> "Asked for a rewrite.\n"
                    },
                ),
            )
        }
        out.emit(GenerationEvent.Delta(text))
        out.emit(GenerationEvent.Done)
    }

    private class Verdict(val pass: Boolean, val problems: List<String>, val edits: List<Pair<String, String>>, val rewrite: String?)

    /** The check: the context, the draft as the assistant, the goals as one more user turn; JSON back. */
    private suspend fun check(payload: JsonObject, draft: String, goals: String, hop: Hop): Verdict? {
        val ask = buildString {
            append(CHECK_HEAD).append("\n\nGOALS\n").append(goals)
            append("\n\nDRAFT\n<draft>\n").append(draft).append("\n</draft>\n\n").append(CHECK_TAIL)
        }
        val answer = hop.complete(judging(payload, draft, ask))
        val obj = jsonIn(answer, json) ?: return null
        val verdict = obj["verdict"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val problems = obj["problems"]?.let { runCatching { it.jsonArray.mapNotNull { p -> p.jsonPrimitive.contentOrNull } }.getOrNull() }.orEmpty()
        val edits = obj["edits"]?.let { runCatching { it.jsonArray } .getOrNull() }.orEmpty().mapNotNull { e ->
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
    private suspend fun rewrite(payload: JsonObject, draft: String, problems: List<String>, hop: Hop): String? {
        val ask = buildString {
            append("Rewrite the draft reply below so that these problems are gone, changing nothing else that works:\n")
            problems.forEach { append("- ").append(it).append('\n') }
            append("\n<draft>\n").append(draft).append("\n</draft>\n\nAnswer with the corrected reply only: no notes, no tags, no preamble.")
        }
        return runCatching { strip(hop.complete(judging(payload, draft, ask, temperature = 0.6))) }.getOrNull()
    }

    /** The payload for a judging call: the same context, the draft as the assistant's turn, [ask] as the user's. */
    private fun judging(payload: JsonObject, draft: String, ask: String, temperature: Double = 0.2): JsonObject {
        val messages = payload["messages"]?.jsonArray.orEmpty().toMutableList()
        messages += buildJsonObject { put("role", "assistant"); put("content", draft) }
        messages += buildJsonObject { put("role", "user"); put("content", ask) }
        return JsonObject(
            payload.toMutableMap().apply {
                put("messages", JsonArray(messages))
                put("stream", JsonPrimitive(false))
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

    internal companion object {
        const val TAG = "AgentLoop"

    /** Edits applied in order: exact text first, then the same words across any whitespace. */
    internal fun apply(text: String, edits: List<Pair<String, String>>): Triple<String, Int, Int> {
        var out = text
        var applied = 0
        var missed = 0
        for ((find, replace) in edits) {
            val at = out.indexOf(find)
            if (at >= 0) {
                out = out.substring(0, at) + replace + out.substring(at + find.length)
                applied++
                continue
            }
            val loose = Regex(find.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) })
            val m = loose.find(out)
            if (m != null) {
                out = out.substring(0, m.range.first) + replace + out.substring(m.range.last + 1)
                applied++
            } else {
                missed++
            }
        }
        return Triple(out, applied, missed)
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

        val REPLY_MODES = setOf("NEW", "ALTERNATIVE", "CONTINUE")

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
