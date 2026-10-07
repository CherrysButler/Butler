package com.cherry.butler.core.generation

import android.content.Context
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.markdown.SceneTags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Butler's specials: short instructions added to the user's own model on every reply, asking
 * it to wrap parts of its answer in tags Butler draws (Butter mode). They cost the user tokens
 * on their model, which Settings says before they are switched on.
 *
 * On a proxy the instruction joins the system prompt of the payload Janitor assembled; on
 * JLLM, where Janitor keeps the prompt, it rides at the end of the latest user line, in that
 * request only (never saved). Not for Continue, which extends a reply already tagged or not.
 *
 * With [stripTags] the tags are taken off before a reply reaches Janitor and kept only on
 * the phone (MessageEntity.markup), so Janitor's site never shows them; clearing Butler's
 * storage loses them, the reply itself stays.
 */
@Singleton
class PromptAddons @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _butter = MutableStateFlow(prefs.getBoolean(KEY_BUTTER, false))
    val butter: StateFlow<Boolean> = _butter.asStateFlow()

    private val _stripTags = MutableStateFlow(prefs.getBoolean(KEY_STRIP, true))
    val stripTags: StateFlow<Boolean> = _stripTags.asStateFlow()

    fun setButter(on: Boolean) {
        _butter.value = on
        prefs.edit().putBoolean(KEY_BUTTER, on).apply()
    }

    private val _butterTint = MutableStateFlow(prefs.getBoolean(KEY_BUTTER_TINT, true))

    /** Whether butter is tinted in a full reply; off, it only shows when a reply is folded. */
    val butterTint: StateFlow<Boolean> = _butterTint.asStateFlow()

    fun setButterTint(on: Boolean) {
        _butterTint.value = on
        prefs.edit().putBoolean(KEY_BUTTER_TINT, on).apply()
    }

    private val _highlights = MutableStateFlow(prefs.getBoolean(KEY_HIGHLIGHTS, false))

    /** Highlights: the model tags sentences by mood, drawn as faint washes. */
    val highlights: StateFlow<Boolean> = _highlights.asStateFlow()

    fun setHighlights(on: Boolean) {
        _highlights.value = on
        prefs.edit().putBoolean(KEY_HIGHLIGHTS, on).apply()
    }

    private val _moods = MutableStateFlow(
        prefs.getStringSet(KEY_MOODS, null)?.mapNotNull { Mood.of(it) }?.toSet() ?: Mood.entries.toSet(),
    )

    private val _highlightsPrompt = MutableStateFlow(prefs.getString(KEY_HIGHLIGHTS_PROMPT, null))

    /**
     * The user's own wording for what Highlights looks for, or null for Butler's. Only the
     * guidance: the moods and the answer format are always added after it (see MoodTagger), so
     * an edit can't break the marking. Asked for on Reddit when picks felt hit or miss.
     */
    val highlightsPrompt: StateFlow<String?> = _highlightsPrompt.asStateFlow()

    fun setHighlightsPrompt(text: String?) {
        val kept = text?.trim()?.takeIf { it.isNotEmpty() && it != MoodTagger.DEFAULT_GUIDANCE }
        _highlightsPrompt.value = kept
        prefs.edit().apply { if (kept == null) remove(KEY_HIGHLIGHTS_PROMPT) else putString(KEY_HIGHLIGHTS_PROMPT, kept) }.apply()
    }

    /** Which moods Highlights asks for; only these go into the instruction. */
    val moods: StateFlow<Set<Mood>> = _moods.asStateFlow()

    fun setMood(mood: Mood, on: Boolean) {
        val next = if (on) _moods.value + mood else _moods.value - mood
        _moods.value = next
        prefs.edit().putStringSet(KEY_MOODS, next.map { it.tag }.toSet()).apply()
    }

    fun setStripTags(strip: Boolean) {
        _stripTags.value = strip
        prefs.edit().putBoolean(KEY_STRIP, strip).apply()
    }

    /** The instruction for this request, or null when nothing is on or [mode] doesn't take one. */
    fun instruction(mode: GenerateMode): String? {
        if (mode != GenerateMode.New && mode != GenerateMode.Alternative) return null
        // Highlights is not here: it asks after the reply is written (MoodTagger), so the reply
        // itself is never bent to fit a mood.
        val parts = buildList { if (_butter.value) add(BUTTER) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    /** A proxy payload with [instruction] added to its system prompt (a new one first if none). */
    fun intoPayload(payload: JsonObject, instruction: String): JsonObject {
        val messages = payload["messages"]?.jsonArray?.toMutableList() ?: return payload
        val i = messages.indexOfFirst { (it as? JsonObject)?.get("role")?.jsonPrimitive?.contentOrNull == "system" }
        if (i >= 0) {
            val m = messages[i].jsonObject
            val content = m["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            messages[i] = JsonObject(m + ("content" to JsonPrimitive("$content\n\n$instruction")))
        } else {
            messages.add(0, buildJsonObject { put("role", JsonPrimitive("system")); put("content", JsonPrimitive(instruction)) })
        }
        return JsonObject(payload + ("messages" to JsonArray(messages)))
    }

    /** JLLM: [instruction] after the latest user line, in this request only. */
    fun intoHistory(history: List<MessageEntity>, instruction: String): List<MessageEntity> {
        val last = history.indexOfLast { !it.isBot }
        if (last < 0) return history
        return history.toMutableList().also { it[last] = it[last].copy(text = it[last].text + "\n\n(OOC: $instruction)") }
    }

    /** What a finished reply is saved as: (text for Janitor, the tagged copy kept here or null). */
    fun finish(raw: String): Pair<String, String?> =
        if (_stripTags.value && SceneTags.hasAny(raw)) SceneTags.strip(raw) to raw else raw to null

    companion object {
        /**
         * Kept short on purpose: it rides on every reply. The example is what makes JLLM follow
         * it, and showing two parts (one of them speech) is what keeps it from buttering a
         * single line: with this wording it marked four parts, speech included, in each of two
         * tries (2026-10-06).
         */
        const val BUTTER =
            "Formatting rule for this reply: wrap every important part of the scene in <butter></butter> tags, " +
                "usually several per reply: each key action, decision or reveal, and always the lines of speech that " +
                "matter. Example: The rain keeps falling. <butter>She grabs the key.</butter> Thunder rolls. " +
                "<butter>\"We leave now,\" she says.</butter> Wrap whole sentences and change no words. Read alone, the " +
                "butter parts should retell the reply in short. A reply of only a few lines needs none."

        /**
         * Highlights for the chosen [moods], in one short rule with an example (an example is
         * what gets JLLM to use a format at all; see [BUTTER]).
         */
        fun highlights(moods: Set<Mood>): String {
            val list = Mood.entries.filter { it in moods }
            val which = list.joinToString(", ") { "<${it.tag}> for ${it.meaning}" }
            val sample = list.first()
            return "Formatting rule for this reply: lightly tag sentences by mood with $which. Example: She sets " +
                "the cup down. <${sample.tag}>${sample.example}</${sample.tag}> Tag only sentences that clearly carry " +
                "the mood, usually a few per reply; most stay untagged. Whole sentences, words unchanged."
        }

        /** Roughly, at four characters a token. */
        fun tokensOf(text: String): Int = (text.length + 3) / 4

        private const val KEY_BUTTER = "addon_butter"
        private const val KEY_STRIP = "addon_strip_tags"
        private const val KEY_BUTTER_TINT = "addon_butter_tint"
        private const val KEY_HIGHLIGHTS = "addon_highlights"
        private const val KEY_MOODS = "addon_moods"
        private const val KEY_HIGHLIGHTS_PROMPT = "addon_highlights_prompt"
    }
}

/** A Highlights mood: its tag, what it is for (in the instruction), and the example line. */
enum class Mood(val tag: String, val label: String, val meaning: String, val example: String) {
    Romantic("romantic", "Romantic", "tender or loving moments", "He brushes the hair from her face."),
    Erotic("erotic", "Erotic", "sexual or sensual ones", "Her breath catches at his touch."),
    Dangerous("dangerous", "Dangerous", "threat or violence", "The blade presses to his throat."),
    Sad("sad", "Sad", "grief or pain", "She hides the tears behind her hand."),
    Funny("funny", "Funny", "humour", "The cat knocks the vase over, again."),
    ;

    companion object {
        fun of(tag: String) = entries.firstOrNull { it.tag == tag }
    }
}
