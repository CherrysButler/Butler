package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lessons (beta): standing notes for every reply, learned from the player's own corrections.
 * A lesson is everywhere, or for one character ([characterId], with its [characterName] to
 * show), and can be switched off without being lost.
 */
data class Lesson(
    val id: String = UUID.randomUUID().toString().take(8),
    val text: String,
    val characterId: String? = null,
    val characterName: String? = null,
    val on: Boolean = true,
) {
    val everywhere: Boolean get() = characterId == null
}

/**
 * The lessons as a small markdown file, readable and editable by hand:
 *
 * ```
 * # Butler lessons
 *
 * ## Everywhere
 * - [x] Never write my character's actions or words.
 * - [ ] Keep replies under three paragraphs.
 *
 * ## Ash <!-- character:3f0c… -->
 * - [x] Ash never swears.
 * ```
 * `[x]` is on, `[ ]` off.
 */
object LessonsFile {
    private val CHARACTER = Regex("""^##\s+(.+?)\s*<!--\s*character:\s*([^\s>]+)\s*-->\s*$""")
    private val ITEM = Regex("""^\s*[-*]\s*\[( |x|X)]\s+(.+?)\s*$""")

    fun parse(text: String): List<Lesson> {
        val out = ArrayList<Lesson>()
        var characterId: String? = null
        var characterName: String? = null
        for (line in text.lineSequence()) {
            val t = line.trimEnd()
            when {
                t.startsWith("## ") -> {
                    val m = CHARACTER.find(t)
                    characterId = m?.groupValues?.get(2)
                    characterName = m?.groupValues?.get(1)
                }
                else -> ITEM.find(t)?.let { m ->
                    out += Lesson(text = m.groupValues[2], characterId = characterId, characterName = characterName, on = m.groupValues[1] != " ")
                }
            }
        }
        return out
    }

    fun write(lessons: List<Lesson>): String = buildString {
        append("# Butler lessons\n\n")
        append("Standing notes sent with every reply, learned from your OOC notes (Lessons, beta).\n")
        append("[x] is on, [ ] is off. Edit them in Butler, or here.\n")
        val (everywhere, perCharacter) = lessons.partition { it.everywhere }
        append("\n## Everywhere\n")
        everywhere.forEach { append(item(it)) }
        perCharacter.groupBy { it.characterId }.forEach { (id, list) ->
            val name = list.firstNotNullOfOrNull { it.characterName }?.replace(Regex("[\\r\\n<>]"), " ")?.trim().orEmpty().ifEmpty { "Character" }
            append("\n## ").append(name).append(" <!-- character:").append(id).append(" -->\n")
            list.forEach { append(item(it)) }
        }
    }

    private fun item(l: Lesson) = "- [${if (l.on) "x" else " "}] ${l.text.replace(Regex("\\s*\\n\\s*"), " ").trim()}\n"
}

/** One proposed change from a /learn, for the review: keep it ticked to apply it. */
data class LessonChange(val kind: Kind, val old: Lesson?, val new: Lesson?) {
    enum class Kind { Added, Changed, Dropped }
}

object LessonDiff {
    /** What turning [current] into [proposed] means, rule by rule; unchanged ones are left out. */
    fun between(current: List<Lesson>, proposed: List<Lesson>): List<LessonChange> {
        fun key(l: Lesson) = (l.characterId ?: "") + "|" + norm(l.text)
        val same = current.map(::key).toSet() intersect proposed.map(::key).toSet()
        val gone = current.filter { key(it) !in same }.toMutableList()
        val came = proposed.filter { key(it) !in same }.toMutableList()
        val changes = ArrayList<LessonChange>()
        // A new rule close to one that went is that rule rewritten.
        for (n in came.toList()) {
            val match = gone.filter { it.characterId == n.characterId }.maxByOrNull { similarity(it.text, n.text) }
            if (match != null && similarity(match.text, n.text) >= 0.5) {
                changes += LessonChange(LessonChange.Kind.Changed, match, n.copy(id = match.id, on = match.on))
                gone.remove(match)
                came.remove(n)
            }
        }
        came.forEach { changes += LessonChange(LessonChange.Kind.Added, null, it) }
        gone.forEach { changes += LessonChange(LessonChange.Kind.Dropped, it, null) }
        return changes
    }

    /** [current] with the [accepted] changes made. */
    fun applied(current: List<Lesson>, accepted: List<LessonChange>): List<Lesson> {
        val out = current.toMutableList()
        for (c in accepted) when (c.kind) {
            LessonChange.Kind.Dropped -> out.removeAll { it.id == c.old?.id }
            LessonChange.Kind.Changed -> {
                val i = out.indexOfFirst { it.id == c.old?.id }
                if (i >= 0 && c.new != null) out[i] = c.new
            }
            LessonChange.Kind.Added -> c.new?.let { out += it }
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()

    private fun words(s: String) = norm(s).split(' ').filter { it.length > 2 }.toSet()

    fun similarity(a: String, b: String): Double {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return (x intersect y).size.toDouble() / (x union y).size
    }
}

/** The player's out-of-character words in a line they wrote: what /learn reads. */
object OocNotes {
    private val PATTERNS = listOf(
        Regex("""\(\(\s*(.+?)\s*\)\)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""\(\s*OOC\s*[:\-–—]?\s*(.+?)\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\[\s*OOC\s*[:\-–—]?\s*(.+?)]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        // The closing brace is escaped: Android's regex engine (ICU) refuses a bare one, where
        // a PC's Java lets it through. Unescaped, this whole object failed to load on every
        // phone and /learn never ran there; Android then names only the class that failed,
        // which a release build had shortened to "L2.K2" (reported on 0.5.2).
        Regex("""\{\s*OOC\s*[:\-–—]?\s*(.+?)\}""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""(?m)^\s*OOC\s*[:\-–—]\s*(.+)$""", RegexOption.IGNORE_CASE),
    )

    /** Every OOC note in [line], in order, trimmed; none for an in-character line. */
    fun from(line: String): List<String> {
        val found = ArrayList<Pair<Int, String>>()
        val taken = ArrayList<IntRange>()
        for (p in PATTERNS) for (m in p.findAll(line)) {
            if (taken.any { it.first <= m.range.first && m.range.last <= it.last }) continue
            taken += m.range
            m.groupValues[1].trim().takeIf { it.length >= 3 }?.let { found += m.range.first to it }
        }
        return found.sortedBy { it.first }.map { it.second }
    }
}

/**
 * Where the lessons live: `files/lessons.md` on this device, nothing on Janitor. Also whether
 * the beta is on, and when /learn last read the chats (it reads only what came after).
 */
@Singleton
class LessonsStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "lessons.md")
    private val prefs = context.getSharedPreferences("butler_prefs", Context.MODE_PRIVATE)

    private val _lessons = MutableStateFlow(runCatching { if (file.isFile) LessonsFile.parse(file.readText()) else emptyList() }.getOrDefault(emptyList()))
    val lessons: StateFlow<List<Lesson>> = _lessons.asStateFlow()

    private val _on = MutableStateFlow(prefs.getBoolean(KEY_ON, false))
    /** Lessons is a beta, off until switched on. */
    val on: StateFlow<Boolean> = _on.asStateFlow()

    fun setOn(on: Boolean) {
        _on.value = on
        prefs.edit().putBoolean(KEY_ON, on).apply()
    }

    /** When /learn last read the chats (0: never); only what came after is read next time. */
    var learnedUpTo: Long
        get() = prefs.getLong(KEY_UP_TO, 0L)
        set(value) { prefs.edit().putLong(KEY_UP_TO, value).apply() }

    fun save(lessons: List<Lesson>) {
        _lessons.value = lessons
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(LessonsFile.write(lessons))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    fun update(id: String, change: (Lesson) -> Lesson) = save(_lessons.value.map { if (it.id == id) change(it) else it })

    fun delete(id: String) = save(_lessons.value.filterNot { it.id == id })

    /** The notes a reply to [characterId] follows: every lesson that's on, everywhere and for it. Null with none, or the beta off. */
    fun instructionFor(characterId: String): String? {
        if (!_on.value) return null
        val rules = _lessons.value.filter { it.on && (it.everywhere || it.characterId == characterId) }
        if (rules.isEmpty()) return null
        return "Standing notes from the player, learned from their past corrections. Follow them in every reply:\n" +
            rules.joinToString("\n") { "- ${it.text}" }
    }

    private companion object {
        const val KEY_ON = "lessons_on"
        const val KEY_UP_TO = "lessons_learned_up_to"
    }
}
