package com.cherry.butler.core.lorebook

import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LorebookStore @Inject constructor(database: LorebookDatabase) {
    private val dao = database.lorebooks()

    val contextCharLimit = dao.observeContextLimit().map { it ?: LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT }

    fun books(scope: String? = null, personaScope: String? = null) =
        dao.observeBooks(scope, personaScope).map { books ->
            books.sortedWith(compareByDescending<LorebookSummary> { it.favorite }.thenBy { it.name.lowercase() })
        }.flowOn(Dispatchers.Default)

    suspend fun book(id: String) = dao.book(id)
    suspend fun save(book: Lorebook, isNew: Boolean, enableInScope: String?) = dao.save(book, isNew, enableInScope)
    suspend fun delete(id: String) = dao.deleteBook(id)
    suspend fun toggleFavorite(id: String) = dao.toggleFavorite(id)
    suspend fun setEnabled(scope: String, id: String, enabled: Boolean) = dao.setEnabled(scope, id, enabled)
    suspend fun setPersonaBooks(personaId: String, ids: Set<String>) = dao.setPersonaBooks(LorebookScope.persona(personaId), ids)
    suspend fun clearPersona(personaId: String) = dao.clearBindings(LorebookScope.persona(personaId))

    suspend fun setContextCharLimit(limit: Int) {
        require(limit >= 0) { "Lore character limit cannot be negative." }
        dao.setSettings(LoreSettingsRow(limit))
    }

    suspend fun instructionFor(chat: ChatEntity, profile: ProfileDto, persona: PersonaDto?, history: List<MessageEntity>): String? {
        val personaScope = LorebookScope.persona(persona?.id, chat.userId.ifEmpty { profile.id })
        val entries = dao.activeEntries(LorebookScope.chat(chat.id), personaScope)
        if (entries.isEmpty()) return null
        val byBook = entries.groupBy({ it.book }, { it.row.entry })
        return LorebookRequest.instructionFor(
            books = byBook.map { (book, selected) -> book.toBook(selected) },
            charLimit = dao.contextLimit() ?: LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT,
            userName = persona?.name ?: profile.name,
            history = history,
        )
    }
}
