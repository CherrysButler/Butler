package com.cherry.butler.core.lorebook

import android.util.Log
import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.markdown.fillUser
import com.cherry.butler.core.data.remote.dto.PersonaDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LorebookStore @Inject constructor(database: LorebookDatabase) {
    private val dao = database.lorebooks()
    private val mutex = Mutex()
    private val chats = mutableMapOf<Long, ChatLorebookCache>()

    val contextCharLimit = dao.observeContextLimit().map { it ?: LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT }

    fun books(scope: String? = null, personaScope: String? = null) =
        dao.observeBooks(scope, personaScope).map { books ->
            books.sortedWith(compareByDescending<LorebookSummary> { it.favorite }.thenBy { it.name.lowercase() })
        }.flowOn(Dispatchers.Default)

    suspend fun book(id: String) = dao.book(id)
    suspend fun save(book: Lorebook, isNew: Boolean, enableInScope: String?) =
        change(bindingScope = enableInScope.takeIf { isNew }, savedBook = book) { dao.save(book, isNew, enableInScope) }

    suspend fun delete(id: String) = change(changedId = id) { dao.deleteBook(id) }
    suspend fun toggleFavorite(id: String) = dao.toggleFavorite(id)

    suspend fun setEnabled(scope: String, id: String, enabled: Boolean) =
        change(bindingScope = scope) { dao.setEnabled(scope, id, enabled) }

    suspend fun setPersonaBooks(personaId: String, ids: Set<String>) {
        val scope = LorebookScope.persona(personaId)
        change(bindingScope = scope) { dao.setPersonaBooks(scope, ids) }
    }

    suspend fun clearPersona(personaId: String) {
        val scope = LorebookScope.persona(personaId)
        change(bindingScope = scope) { dao.clearBindings(scope) }
    }

    private suspend fun change(
        bindingScope: String? = null,
        savedBook: Lorebook? = null,
        changedId: String? = savedBook?.id,
        write: suspend () -> Unit,
    ) = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                write()
                for (chat in chats.values) {
                    try {
                        chat.refresh(bindingScope, changedId, savedBook)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        chat.invalidate()
                        Log.w("LorebookStore", "Could not refresh the chat's lorebooks", failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                // Room may already have committed. Re-read any chats we did not finish updating.
                chats.values.forEach { it.invalidate() }
                throw cancelled
            }
        }
    }

    suspend fun setContextCharLimit(limit: Int) {
        require(limit >= 0) { "Lore character limit cannot be negative." }
        dao.setSettings(LoreSettingsRow(limit))
    }

    /** The chat screen keeps its selection warm across messages and persona changes. */
    suspend fun keepChatOpen(chatId: Long, personaScopes: Flow<String?>) = withReader(chatId) { reader ->
        personaScopes.distinctUntilChanged().collect { scope ->
            try {
                select(chatId, reader, scope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("LorebookStore", "Could not preload the chat's lorebooks", failure)
            }
        }
    }

    suspend fun instructionFor(chat: ChatEntity, profile: ProfileDto, persona: PersonaDto?, history: List<MessageEntity>): String? {
        val personaScope = LorebookScope.persona(persona?.id, chat.userId.ifEmpty { profile.id })
        return withSelection(chat.id, personaScope) { selected ->
            if (selected.candidates.isEmpty()) return@withSelection null
            LorebookRequest.instructionFor(
                selection = selected,
                charLimit = dao.contextLimit() ?: LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT,
                userName = persona?.name ?: profile.name,
                history = history,
            )
        }
    }

    /**
     * The entries the scene would bring in with the next reply, for the chat panel's Context
     * page. An entry with a chance below 100 is listed as if its roll passed.
     */
    suspend fun activatedFor(chat: ChatEntity, profile: ProfileDto, persona: PersonaDto?, history: List<MessageEntity>): List<LoreEntry> {
        val personaScope = LorebookScope.persona(persona?.id, chat.userId.ifEmpty { profile.id })
        val userName = persona?.name ?: profile.name
        return withSelection(chat.id, personaScope) { selected ->
            if (selected.candidates.isEmpty()) return@withSelection emptyList()
            LorebookMatcher.activated(
                selection = selected,
                messages = history.filter { it.serverId != null }.map { it.text.fillUser(userName) },
                charLimit = dao.contextLimit() ?: LorebookMatcher.DEFAULT_CONTEXT_CHAR_LIMIT,
                roll = { 0 },
            )
        }
    }

    internal suspend fun <T> withSelection(chatId: Long, personaScope: String?, match: suspend (LorebookSelection) -> T): T =
        withReader(chatId) { reader -> match(select(chatId, reader, personaScope)) }

    private suspend fun select(chatId: Long, reader: Any, personaScope: String?): LorebookSelection = mutex.withLock {
        chats.getOrPut(chatId) { ChatLorebookCache(LorebookScope.chat(chatId), dao) }.select(reader, personaScope)
    }

    private suspend fun <T> withReader(chatId: Long, block: suspend (Any) -> T): T = withContext(Dispatchers.Default) {
        val reader = Any()
        try {
            block(reader)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    val chat = chats[chatId] ?: return@withLock
                    chat.release(reader)
                    if (chat.unused) chats.remove(chatId)
                }
            }
        }
    }
}
