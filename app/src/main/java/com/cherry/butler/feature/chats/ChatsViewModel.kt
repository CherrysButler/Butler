package com.cherry.butler.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cherry.butler.core.data.ChatRepository
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.model.ChatGroup
import com.cherry.butler.core.model.ChatSummary
import com.cherry.butler.core.network.ApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import com.cherry.butler.core.data.FolderRepository
import com.cherry.butler.core.data.ChatFolder
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.core.data.ChatTransfer
import com.cherry.butler.core.data.ChatFormats
import com.cherry.butler.core.data.TransferChat
import javax.inject.Inject

/** The sheet that opens over a character tile: their chats, and a way to start another. */
data class CharacterChatsSheetState(
    val group: ChatGroup,
    val chats: List<ChatSummary> = emptyList(),
    val refreshing: Boolean = true,
    val refreshError: ApiError? = null,
    val creating: Boolean = false,
    val createError: ApiError? = null,
)

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val profile: ProfileRepository,
    private val personas: PersonaRepository,
    private val folderRepo: FolderRepository,
    private val transfer: ChatTransfer,
) : ViewModel() {

    // ---- import ----

    /** A file read and waiting for the user's yes: what's in it. */
    private val _importing = MutableStateFlow<TransferChat?>(null)
    val importing: StateFlow<TransferChat?> = _importing.asStateFlow()
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress.asStateFlow()

    fun readImport(text: String) {
        runCatching { ChatFormats.parse(text) }
            .onSuccess { _importing.value = it }
            .onFailure { _notice.value = it.message ?: "That file couldn't be read." }
    }

    fun cancelImport() { _importing.value = null }

    /** Into a new chat with the open sheet's character. */
    fun confirmImport(onOpened: (Long) -> Unit) {
        val file = _importing.value ?: return
        val group = _sheet.value?.group ?: return
        _importing.value = null
        viewModelScope.launch {
            _importProgress.value = 0 to file.lines.size
            runCatching {
                transfer.rebuild(group.characterId, personas.current.value?.id, file.lines) { done, total -> _importProgress.value = done to total }
            }.onSuccess { id -> _importProgress.value = null; close(); onOpened(id) }
                .onFailure { e -> _importProgress.value = null; _notice.value = "Import stopped. ${e.userMessage()}" }
        }
    }

    // ---- folders, pins, archive ----

    val folders: StateFlow<List<ChatFolder>> = folderRepo.folders
        .map { all -> all.filterNot(folderRepo::isSpecial) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archivedCount: StateFlow<Int> = folderRepo.archivedCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        // The folders (which name the Pinned and Archive ones) once at start; then the pin and
        // archive marks follow the chats' folder membership for as long as this tab lives,
        // so a change made on the website or another phone shows up after the next sync.
        viewModelScope.launch {
            runCatching { folderRepo.refresh() }
            folderRepo.membership.distinctUntilChanged().collect { runCatching { folderRepo.reconcile() } }
        }
    }

    private val _tab = MutableStateFlow<ChatsTab>(ChatsTab.All)
    val tab: StateFlow<ChatsTab> = _tab.asStateFlow()

    /** A folder's or the archive's rows; the main list stays [groups], built once. */
    val tabGroups: Flow<PagingData<ChatGroup>> = _tab
        .flatMapLatest { t ->
            when (t) {
                ChatsTab.All -> flowOf(PagingData.empty())
                is ChatsTab.Folder -> repository.chatGroups(folder = t.id)
                ChatsTab.Archived -> repository.chatGroups(archived = true)
            }
        }
        .cachedIn(viewModelScope)

    fun selectTab(t: ChatsTab) {
        _tab.value = t
        if (t is ChatsTab.Folder) viewModelScope.launch { runCatching { folderRepo.fill(t.id) } }
    }

    /** The row a long-press is acting on, and what the sheets show for it. */
    private val _acting = MutableStateFlow<ChatGroup?>(null)
    val acting: StateFlow<ChatGroup?> = _acting.asStateFlow()
    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking.asStateFlow()
    private val _inFolders = MutableStateFlow<Set<String>>(emptySet())
    val inFolders: StateFlow<Set<String>> = _inFolders.asStateFlow()
    private val _managing = MutableStateFlow<ChatFolder?>(null)
    val managing: StateFlow<ChatFolder?> = _managing.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val organizeBusy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun actOn(group: ChatGroup) { _acting.value = group }
    fun stopActing() { _acting.value = null; _picking.value = false }
    fun dismissNotice() { _notice.value = null }

    private fun organize(work: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { work() }.onFailure { _notice.value = "Not changed. ${it.userMessage()}" }
            _busy.value = false
        }
    }

    fun togglePin() {
        val g = _acting.value ?: return
        stopActing()
        organize { folderRepo.setPinned(g.characterId, !g.pinned) }
    }

    fun toggleArchive() {
        val g = _acting.value ?: return
        val archiving = _tab.value != ChatsTab.Archived
        stopActing()
        organize {
            folderRepo.setArchived(g.characterId, archiving)
            if (!archiving && archivedCount.value <= 1) _tab.value = ChatsTab.All
        }
    }

    fun pickFolders() {
        val g = _acting.value ?: return
        _picking.value = true
        organize {
            runCatching { folderRepo.refresh() }
            _inFolders.value = folders.value.filter { folderRepo.isIn(g.characterId, it.id) }.map { it.id }.toSet()
        }
    }

    fun toggleFolder(folder: ChatFolder) {
        val g = _acting.value ?: return
        val adding = folder.id !in _inFolders.value
        organize {
            folderRepo.setInFolder(g.characterId, folder.id, adding)
            _inFolders.update { if (adding) it + folder.id else it - folder.id }
        }
    }

    fun createFolder(name: String) {
        val g = _acting.value ?: return
        organize {
            val made = folderRepo.create(name)
            folderRepo.setInFolder(g.characterId, made.id, true)
            _inFolders.update { it + made.id }
        }
    }

    fun manage(folder: ChatFolder) { _managing.value = folder }
    fun stopManaging() { _managing.value = null }

    fun renameFolder(name: String) {
        val f = _managing.value ?: return
        organize { folderRepo.rename(f.id, name); _managing.value = null }
    }

    fun deleteFolder() {
        val f = _managing.value ?: return
        organize {
            folderRepo.delete(f.id)
            _managing.value = null
            if (_tab.value == ChatsTab.Folder(f.id)) _tab.value = ChatsTab.All
        }
    }

    init {
        viewModelScope.launch { runCatching { folderRepo.refresh() } }
    }

    /** The "Enter character name" box. */
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()
    private var filled = false

    /** The whole list. Built once and kept, so clearing a search returns to it untouched. */
    val groups: Flow<PagingData<ChatGroup>> = repository.chatGroups().cachedIn(viewModelScope)

    /** Matches for the search box; empty while the box is. Shown over [groups], never instead of rebuilding it. */
    val searchResults: Flow<PagingData<ChatGroup>> = _search
        .debounce { if (it.isBlank()) 0L else 250L }
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(PagingData.empty()) else repository.chatGroups(q) }
        .cachedIn(viewModelScope)

    fun onSearch(value: String) {
        _search.value = value
        // The first search brings the whole chat list down so it can match every chat.
        if (value.isNotBlank() && !filled) {
            filled = true
            viewModelScope.launch { runCatching { repository.fillChatMirror() }.onFailure { filled = false } }
        }
    }

    /**
     * Fills `{{user}}` in previews whose chat has not been opened on this phone yet: the
     * profile, which is who plays in any chat that names no persona.
     */
    val fallbackPersonaName: StateFlow<String?> = profile.profile
        .map { p -> p?.name?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Who a new chat from the sheet starts as. */
    val persona: StateFlow<PersonaOption?> = personas.current
    val personaOptions: StateFlow<List<PersonaOption>> = personas.options

    fun choosePersona(option: PersonaOption) = personas.select(option.id)

    init {
        viewModelScope.launch { personas.load() }
    }

    private val _sheet = MutableStateFlow<CharacterChatsSheetState?>(null)
    val sheet: StateFlow<CharacterChatsSheetState?> = _sheet.asStateFlow()

    private var sheetJob: Job? = null

    /** Opens the sheet on the mirror immediately and reconciles with the server behind it. */
    fun open(group: ChatGroup) {
        sheetJob?.cancel()
        _sheet.value = CharacterChatsSheetState(group)
        sheetJob = viewModelScope.launch {
            launch {
                repository.observeChatsWith(group.characterId).collect { chats ->
                    _sheet.update { it?.copy(chats = chats) }
                }
            }
            val result = runCatching { repository.refreshChatsWith(group.characterId) }
            _sheet.update {
                it?.copy(refreshing = false, refreshError = result.exceptionOrNull() as? ApiError)
            }
        }
    }

    fun close() {
        sheetJob?.cancel()
        sheetJob = null
        _sheet.value = null
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteChat(chatId) }
                .onFailure { e -> _sheet.update { it?.copy(createError = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    fun newChat(onOpened: (Long) -> Unit) {
        val state = _sheet.value ?: return
        if (state.creating) return
        viewModelScope.launch {
            _sheet.update { it?.copy(creating = true, createError = null) }
            runCatching { repository.createChat(state.group.characterId, personas.current.value) }
                .onSuccess { id ->
                    _sheet.update { it?.copy(creating = false) }
                    onOpened(id)
                }
                .onFailure { e ->
                    _sheet.update { it?.copy(creating = false, createError = e as? ApiError ?: ApiError.Unknown(e)) }
                }
        }
    }
}
