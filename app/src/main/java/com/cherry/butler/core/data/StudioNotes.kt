package com.cherry.butler.core.data

import android.content.Context
import com.cherry.butler.core.model.Character
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What a character's card shows, as Butler last wrote it. */
@Serializable
data class NoteCard(
    val name: String,
    val avatar: String,
    val blurb: String = "",
    val isNsfw: Boolean = false,
    val tagNames: List<String> = emptyList(),
    val customTags: List<String> = emptyList(),
    val totalTokens: Int = 0,
    val creatorName: String = "",
) {
    /** Whether Janitor's copy shows this already (the parts a save changes that a card shows). */
    fun matches(c: Character): Boolean =
        name == c.name && avatar == c.avatar.orEmpty() && isNsfw == c.isNsfw && totalTokens == c.totalTokens

    fun toCharacter(id: String) = Character(
        id = id,
        name = name,
        avatar = avatar.ifBlank { null },
        creatorName = creatorName,
        creatorVerified = false,
        creatorPlusBadge = false,
        chatCount = 0,
        messageCount = 0,
        isNsfw = isNsfw,
        isImageNsfw = false,
        tagNames = tagNames,
        customTags = customTags,
        blurb = blurb,
        totalTokens = totalTokens,
    )
}

/**
 * What Butler just did to one of your characters, kept until Janitor's own list shows it.
 *
 * "Your characters" comes from Janitor's search index, which catches up with a save, a publish
 * or a delete some seconds to minutes later (a delete on the website still listed the bot
 * 15 seconds on, 2026-10-12). Until it does, these notes are laid over its list so your own
 * changes show at once; each retires when the list agrees, or after [TTL_MS] at the latest.
 */
@Serializable
data class StudioNote(
    val characterId: String,
    /** New: not in Janitor's list yet. */
    val created: Boolean = false,
    val deleted: Boolean = false,
    /** The card as saved, for a new or changed character. */
    val card: NoteCard? = null,
    /** Public or private, as last set; null when untouched. */
    val isPublic: Boolean? = null,
    val at: Long = 0,
) {
    fun live(now: Long = System.currentTimeMillis()): Boolean = now - at < TTL_MS

    companion object {
        /** How long a note waits for Janitor's list before it gives up and lets the list be. */
        const val TTL_MS = 10 * 60 * 1000L
    }
}

@Singleton
class StudioNotes @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "studio-notes.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StudioNote.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()

    private val _notes = MutableStateFlow(read().filter { it.live() })
    val notes: StateFlow<List<StudioNote>> = _notes.asStateFlow()

    /** The notes still waiting, the expired ones dropped. */
    fun live(): List<StudioNote> = _notes.value.filter { it.live() }.also { if (it.size != _notes.value.size) set(it) }

    fun created(id: String, card: NoteCard) = merge(id) { StudioNote(id, created = true, card = card, isPublic = false) }

    fun changed(id: String, card: NoteCard) = merge(id) { (it ?: StudioNote(id)).copy(card = card, deleted = false) }

    fun publicSet(id: String, on: Boolean) = merge(id) { (it ?: StudioNote(id)).copy(isPublic = on) }

    fun deleted(id: String) = merge(id) { StudioNote(id, deleted = true) }

    /** Janitor's list shows what the note said: the note's work is done. */
    fun retire(ids: Collection<String>) {
        if (ids.isEmpty()) return
        set(_notes.value.filterNot { it.characterId in ids })
    }

    private fun merge(id: String, change: (StudioNote?) -> StudioNote) {
        val now = System.currentTimeMillis()
        val before = _notes.value.firstOrNull { it.characterId == id }?.takeIf { it.live(now) }
        val next = change(before).let { it.copy(created = it.created || (before?.created == true && !it.deleted), at = now) }
        set(listOf(next) + _notes.value.filterNot { it.characterId == id })
    }

    private fun set(list: List<StudioNote>) {
        _notes.value = list
        val snapshot = list
        scope.launch {
            writes.withLock {
                val tmp = File(file.parentFile, file.name + ".tmp")
                runCatching {
                    tmp.writeText(json.encodeToString(serializer, snapshot))
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }

    private fun read(): List<StudioNote> =
        runCatching { if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyList() }.getOrDefault(emptyList())
}
