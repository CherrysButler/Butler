package com.cherry.butler.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A character being written, kept on this device until it's saved to Janitor: a new one, or
 * changes to one of yours. Nothing here leaves the device.
 */
@Serializable
data class StoredDraft(
    /** "new-…" for a character not on Janitor yet; "edit-{characterId}" for changes to one that is. */
    val id: String,
    /** The character it changes; null for a new one. */
    val characterId: String? = null,
    val draft: CharacterDraft = CharacterDraft(),
    /** The picture to show (Janitor's, once uploaded). */
    val pictureUrl: String? = null,
    val updatedAt: Long = 0,
) {
    val isNew: Boolean get() = characterId == null

    companion object {
        fun idFor(characterId: String) = "edit-$characterId"
        fun newId() = "new-${System.currentTimeMillis()}"
    }
}

/**
 * The drafts, as one small JSON file in the app's own storage, newest first. Writes are
 * serialized and go to disk off the main thread; the list in memory answers at once.
 */
@Singleton
class CharacterDrafts @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "character-drafts.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredDraft.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Mutex()

    private val _drafts = MutableStateFlow(read())
    val drafts: StateFlow<List<StoredDraft>> = _drafts.asStateFlow()

    fun get(id: String): StoredDraft? = _drafts.value.firstOrNull { it.id == id }

    /** Keeps [draft] (replacing an earlier one with its id), stamped now. */
    fun put(draft: StoredDraft) {
        _drafts.update { list -> (listOf(draft.copy(updatedAt = System.currentTimeMillis())) + list.filterNot { it.id == draft.id }) }
        persist()
    }

    fun delete(id: String) {
        _drafts.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun read(): List<StoredDraft> =
        runCatching { if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyList() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }

    private fun persist() {
        val snapshot = _drafts.value
        scope.launch {
            writes.withLock {
                // Written whole to a side file, then moved over: a crash mid-write never leaves half a file.
                val tmp = File(file.parentFile, file.name + ".tmp")
                runCatching {
                    tmp.writeText(json.encodeToString(serializer, snapshot))
                    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                }
            }
        }
    }
}
