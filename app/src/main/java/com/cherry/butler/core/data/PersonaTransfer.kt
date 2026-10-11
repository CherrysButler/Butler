package com.cherry.butler.core.data

import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.dto.PersonaGroupDto
import com.cherry.butler.core.data.remote.dto.PronounsDto
import com.cherry.butler.core.util.IsoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** A persona group as a file holds it: by name, since ids mean nothing on another account. */
data class TransferGroup(val name: String, val color: String? = null)

/** One persona on its way out of, or into, Janitor. */
data class TransferPersona(
    val name: String,
    val appearance: String,
    val pronouns: PronounsDto? = null,
    /** Where its picture is on the web, if it has one. */
    val avatarUrl: String? = null,
    val group: TransferGroup? = null,
    /** It was the profile, the default persona, when it was exported. */
    val isDefault: Boolean = false,
)

/**
 * The shapes a persona export takes: Butler's own JSON, the only one that imports back, and
 * plain text and Markdown for reading or pasting somewhere else. One persona is written as
 * `persona`, several as `personas`; either reads back.
 */
object PersonaFormats {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    const val BUTLER = "butler.persona"

    fun toButler(personas: List<TransferPersona>, exportedAt: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", BUTLER)
            put("version", 1)
            put("exported_at", IsoTime.format(exportedAt))
            if (personas.size == 1) {
                put("persona", personaJson(personas.single()))
            } else {
                put("personas", buildJsonArray { personas.forEach { add(personaJson(it)) } })
            }
        },
    )

    private fun personaJson(p: TransferPersona) = buildJsonObject {
        put("name", p.name)
        put("appearance", p.appearance)
        p.pronouns?.let { put("pronouns", json.encodeToJsonElement(PronounsDto.serializer(), it)) }
        p.avatarUrl?.let { put("avatar", it) }
        p.group?.let { g ->
            put("group", buildJsonObject {
                put("name", g.name)
                g.color?.let { put("color", it) }
            })
        }
        if (p.isDefault) put("default", true)
    }

    /** Each persona as a short card: its name, what goes with it, then its description. */
    fun toText(personas: List<TransferPersona>): String = personas.joinToString("\n\n* * *\n\n") { p ->
        buildString {
            append(p.name)
            if (p.isDefault) append(" (default)")
            append('\n')
            pronounLine(p.pronouns)?.let { append("Pronouns: ").append(it).append('\n') }
            p.group?.let { append("Group: ").append(it.name).append('\n') }
            p.avatarUrl?.let { append("Picture: ").append(it).append('\n') }
            if (p.appearance.isNotBlank()) append('\n').append(p.appearance.trim()).append('\n')
        }.trimEnd()
    } + "\n"

    /** One persona under its name as the heading; several under "Personas", a heading each. */
    fun toMarkdown(personas: List<TransferPersona>): String {
        val one = personas.size == 1
        val blocks = personas.map { p ->
            val facts = buildList {
                if (p.isDefault) add("**Default persona**")
                pronounLine(p.pronouns)?.let { add("**Pronouns:** $it") }
                p.group?.let { add("**Group:** ${it.name}") }
            }
            listOfNotNull(
                (if (one) "# " else "## ") + p.name,
                facts.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- $it" },
                p.avatarUrl?.let { "![${p.name}]($it)" },
                p.appearance.trim().takeIf { it.isNotEmpty() },
            ).joinToString("\n\n")
        }
        return (if (one) blocks else listOf("# Personas") + blocks).joinToString("\n\n") + "\n"
    }

    /** "she/her", or null when the persona has none. */
    private fun pronounLine(p: PronounsDto?): String? =
        listOfNotNull(p?.subjective, p?.objective).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("/")

    /**
     * A Butler persona file, one persona or a list. Text and Markdown exports are for reading
     * only, so anything but the JSON throws [IllegalArgumentException] with a reason to show.
     */
    fun parse(text: String): List<TransferPersona> {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        val o = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            ?: throw IllegalArgumentException("That isn't a Butler persona file. Only the JSON export imports back.")
        require(o["format"]?.jsonPrimitive?.contentOrNull == BUTLER) { "That isn't a Butler persona file." }
        val raw = (o["personas"] as? JsonArray)?.toList() ?: listOfNotNull(o["persona"])
        val personas = raw.mapNotNull { (it as? JsonObject)?.let(::parsePersona) }
        require(personas.isNotEmpty()) { "The file has no personas." }
        return personas
    }

    private fun parsePersona(o: JsonObject): TransferPersona? {
        val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val group = (o["group"] as? JsonObject)?.let { g ->
            g["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { TransferGroup(it, g["color"]?.jsonPrimitive?.contentOrNull?.takeIf { c -> c.isNotBlank() }) }
        }
        return TransferPersona(
            name = name,
            appearance = o["appearance"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pronouns = (o["pronouns"] as? JsonObject)
                ?.let { runCatching { json.decodeFromJsonElement(PronounsDto.serializer(), it) }.getOrNull() }
                ?.takeUnless { it.subjective.isNullOrBlank() },
            avatarUrl = o["avatar"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            group = group,
            isDefault = o["default"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }
}

/**
 * Personas in and out of files. An export reads what Butler already holds; an import makes
 * every persona in the file new on Janitor, beside the ones there, and never changes one that
 * exists. A group the file names is found by name, or made when the account has none by it.
 */
@Singleton
class PersonaTransfer @Inject constructor(
    private val profiles: ProfileRepository,
    private val personas: PersonaRepository,
    private val editing: PersonaEditing,
    private val pictures: PictureUpload,
    private val client: okhttp3.OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    /** One persona by id, or the profile for null. */
    suspend fun export(id: String?): TransferPersona {
        runCatching { profiles.groups() }
        val groups = profiles.groups.value
        return if (id == null) profileAsPersona() else {
            val p = profiles.personas().firstOrNull { it.id == id } ?: profiles.personas(refresh = true).first { it.id == id }
            p.toTransfer(groups)
        }
    }

    /** The profile first, then every persona in the user's order. */
    suspend fun exportAll(): List<TransferPersona> {
        runCatching { profiles.groups() }
        val groups = profiles.groups.value
        return listOf(profileAsPersona()) + profiles.personas().map { it.toTransfer(groups) }
    }

    private suspend fun profileAsPersona(): TransferPersona {
        val p = profiles.profile()
        // The default as Butler shows it: a picture or pronouns "Make default" kept aside count.
        val shown = personas.options.value.firstOrNull { it.id == null }
        return TransferPersona(
            name = shown?.name ?: p.name.ifBlank { p.userName },
            appearance = p.appearance.orEmpty(),
            pronouns = shown?.pronouns,
            avatarUrl = JanitorConfig.personaAvatarUrl(shown?.avatarUrl ?: p.avatar),
            isDefault = true,
        )
    }

    private fun com.cherry.butler.core.data.remote.dto.PersonaDto.toTransfer(groups: List<PersonaGroupDto>) = TransferPersona(
        name = name,
        appearance = appearance.orEmpty(),
        pronouns = pronouns?.takeUnless { it.subjective.isNullOrBlank() },
        avatarUrl = JanitorConfig.personaAvatarUrl(avatar),
        group = groups.firstOrNull { it.id == groupId }?.let { TransferGroup(it.name, it.color) },
    )

    /**
     * Each persona in [file] made new on Janitor, in order. [progress] gets (made, total).
     * A failure stops the import; the ones already made stay.
     */
    suspend fun import(file: List<TransferPersona>, progress: (Int, Int) -> Unit = { _, _ -> }) {
        runCatching { profiles.groups(refresh = true) }
        val groups = profiles.groups.value.associateBy { it.name.trim().lowercase() }.toMutableMap()
        progress(0, file.size)
        try {
            file.forEachIndexed { i, p ->
                val id = editing.create(
                    name = p.name,
                    appearance = p.appearance,
                    avatarFile = p.avatarUrl?.let { pictureFor(it) }.orEmpty(),
                    pronouns = p.pronouns,
                    refresh = false,
                )
                p.group?.let { g ->
                    val key = g.name.lowercase()
                    val group = groups[key] ?: profiles.createGroup(g.name, g.color ?: DEFAULT_GROUP_COLOR).also { groups[key] = it }
                    profiles.movePersona(id, group.id)
                }
                progress(i + 1, file.size)
            }
        } finally {
            // Once at the end rather than per persona: the list shows everyone made, even after a stop.
            runCatching { profiles.personas(refresh = true) }
        }
    }

    /**
     * The file's picture as a picture of the new persona's own: fetched from its address and
     * uploaded the way a picked picture is. Janitor doesn't take another persona's file name
     * for a new one (it came out with no picture), so the file is never reused. A picture
     * that can't be fetched or sent leaves the persona without one rather than stopping.
     */
    private suspend fun pictureFor(url: String): String? = runCatching {
        val temp = java.io.File(context.cacheDir, "import-picture-${System.nanoTime()}")
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "picture ${response.code}" }
                    temp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                }
            }
            pictures.upload(android.net.Uri.fromFile(temp), "avatar")
        } finally {
            temp.delete()
        }
    }.getOrNull()

    private companion object {
        /** Janitor's grey, for a group the file names without a colour. */
        const val DEFAULT_GROUP_COLOR = "#6b7280"
    }
}
