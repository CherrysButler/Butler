package com.cherry.butler.feature.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.CharacterDetailRepository
import com.cherry.butler.core.data.ChatRepository
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.local.CharacterEntity
import com.cherry.butler.core.model.CharacterChat
import com.cherry.butler.core.model.CharacterDetail
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cherry.butler.core.data.Comment
import com.cherry.butler.core.data.CommunityRepository
import com.cherry.butler.core.data.PublishedChatCard
import com.cherry.butler.core.model.Character
import com.cherry.butler.ui.components.userMessage
import javax.inject.Inject

data class CharacterDetailUiState(
    val detail: CharacterDetail? = null,
    val chats: List<CharacterChat> = emptyList(),
    val loading: Boolean = true,
    val error: ApiError? = null,
    val startingChat: Boolean = false,
    val startError: ApiError? = null,
    /** The community below the description; each part fills in on its own. */
    val similar: List<Character> = emptyList(),
    val published: List<PublishedChatCard> = emptyList(),
    val publishedTotal: Int = 0,
    val topComment: Comment? = null,
    val commentsLoaded: Boolean = false,
    /** Null until known. */
    val favorited: Boolean? = null,
    val favoriteCount: Int? = null,
    val following: Boolean? = null,
    val socialError: String? = null,
)

@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    private val blocks: com.cherry.butler.core.data.BlockRepository,
    savedStateHandle: SavedStateHandle,
    private val repository: CharacterDetailRepository,
    private val chatRepository: ChatRepository,
    private val personas: PersonaRepository,
    private val community: CommunityRepository,
    private val profiles: com.cherry.butler.core.data.ProfileRepository,
    private val cards: com.cherry.butler.core.data.CardExport,
) : ViewModel() {

    private val _cardExport = kotlinx.coroutines.flow.MutableStateFlow<CardExportState?>(null)
    /** Export card's dialog; null while it's closed. */
    val cardExport: StateFlow<CardExportState?> = _cardExport
    private var cardJob: kotlinx.coroutines.Job? = null

    /**
     * Opens Export card and reads the definition: from the page when it's open or yours, else
     * through Janitor's proxy prompt when the creator allows proxies.
     */
    fun openCardExport(mine: Boolean) {
        val detail = state.value.detail ?: return
        val source = cards.source(detail, mine)
        if (source == com.cherry.butler.core.data.CardExport.Source.None) { _cardExport.value = CardExportState.Hidden; return }
        val viaProxy = source == com.cherry.butler.core.data.CardExport.Source.Proxy
        _cardExport.value = CardExportState.Reading(viaProxy)
        cardJob?.cancel()
        cardJob = viewModelScope.launch {
            // A hidden definition is read from the prompt for a message in your chat with it, the
            // latest one, asked for again here: a chat started since the page opened counts.
            val chatId = if (!viaProxy) null else {
                val fresh = runCatching { repository.chatsWith(characterId) }.getOrNull()
                if (fresh != null) _state.update { it.copy(chats = fresh) }
                (fresh ?: state.value.chats).maxByOrNull { it.updatedAt ?: 0L }?.id
                    ?: run { _cardExport.value = CardExportState.NeedsChat; return@launch }
            }
            runCatching { cards.card(detail, source, chatId) }
                .onSuccess { _cardExport.value = CardExportState.Ready(it, viaProxy) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val why = (e as? com.cherry.butler.core.network.ApiError)?.let { a -> (a as? com.cherry.butler.core.network.ApiError.Api)?.serverMessage ?: a.userMessage() } ?: e.message.orEmpty()
                    _cardExport.value = CardExportState.Failed("Couldn't read the definition. $why".trim())
                }
        }
    }

    /** Your chats with the character asked for again: back on the page after starting one. */
    fun refreshChats() {
        viewModelScope.launch {
            runCatching { repository.chatsWith(characterId) }.onSuccess { list -> _state.update { it.copy(chats = list) } }
        }
    }

    fun closeCardExport() {
        cardJob?.cancel()
        _cardExport.value = null
    }

    /** [card] written into the character's picture: the file name and bytes. */
    suspend fun cardFile(card: com.cherry.butler.core.data.CharacterCard): Pair<String, ByteArray> =
        cards.png(state.value.detail ?: error("The character hasn't loaded yet."), card)

    /** Your own id, to know a character of yours: its page offers Edit instead of Block. */
    val myId: StateFlow<String?> = profiles.profile.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), profiles.profile.value?.id)

    /** Who a chat started here plays as; its name fills `{{user}}` on this page too. */
    val persona: StateFlow<PersonaOption?> = personas.current
    val personaOptions: StateFlow<List<PersonaOption>> = personas.options
    val personaGroups = personas.groups

    fun choosePersona(option: PersonaOption) = personas.select(option.id)

    val characterId: String = checkNotNull(savedStateHandle[Routes.ARG_CHARACTER_ID])

    /** Paints immediately from whatever the browse mirror already holds. */
    val mirror: StateFlow<CharacterEntity?> = repository.observeMirror(characterId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(CharacterDetailUiState(detail = repository.cached(characterId)))
    val state: StateFlow<CharacterDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { personas.load() }
        viewModelScope.launch { runCatching { profiles.profile() } }
        loadCommunity()
        viewModelScope.launch {
            runCatching { community.isFavorite(characterId) }.onSuccess { on -> _state.update { it.copy(favorited = on) } }
            runCatching { community.favoriteCount(characterId) }.onSuccess { n -> _state.update { it.copy(favoriteCount = n) } }
        }
    }

    private var followChecked = false

    /** The creator is known once the detail is; ask whether the user follows them, once. */
    private fun checkFollow(creatorId: String) {
        if (followChecked || creatorId.isBlank()) return
        followChecked = true
        viewModelScope.launch {
            runCatching { community.isFollowing(creatorId) }.onSuccess { on -> _state.update { it.copy(following = on) } }
        }
    }

    /** The heart: changes at once, puts it back if Janitor says no. */
    fun toggleFavorite() {
        val now = _state.value.favorited ?: return
        _state.update { it.copy(favorited = !now, favoriteCount = it.favoriteCount?.let { c -> (c + if (now) -1 else 1).coerceAtLeast(0) }, socialError = null) }
        viewModelScope.launch {
            runCatching { community.setFavorite(characterId, !now) }.onFailure { e ->
                _state.update { it.copy(favorited = now, favoriteCount = it.favoriteCount?.let { c -> (c + if (now) 1 else -1).coerceAtLeast(0) }, socialError = "Not changed. ${e.userMessage()}") }
            }
        }
    }

    /** Blocks the character, or its creator, on Janitor; [onDone] leaves the page. */
    fun block(creator: Boolean, onDone: () -> Unit) {
        val creatorId = _state.value.detail?.creatorId?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            runCatching {
                if (creator) blocks.setCreator(creatorId ?: error("No creator"), true) else blocks.setCharacter(characterId, true)
            }.onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(socialError = "Not blocked. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    fun toggleFollow() {
        val creatorId = _state.value.detail?.creatorId?.takeIf { it.isNotBlank() } ?: return
        val now = _state.value.following ?: return
        _state.update { it.copy(following = !now, socialError = null) }
        viewModelScope.launch {
            runCatching { community.setFollowing(creatorId, !now) }.onFailure { e ->
                _state.update { it.copy(following = now, socialError = "Not changed. ${e.userMessage()}") }
            }
        }
    }

    /** Similar characters, published chats and the first comment, each independent of the others. */
    private fun loadCommunity() {
        viewModelScope.launch {
            runCatching { community.similar(characterId) }.onSuccess { list -> _state.update { it.copy(similar = list.filter { c -> c.id != characterId }) } }
        }
        viewModelScope.launch {
            runCatching { community.publishedChats(characterId) }.onSuccess { (cards, total) -> _state.update { it.copy(published = cards, publishedTotal = total) } }
        }
        viewModelScope.launch {
            runCatching { community.comments(characterId, 1) }.onSuccess { page ->
                _state.update { it.copy(topComment = page.firstOrNull { c -> c.pinned } ?: page.firstOrNull(), commentsLoaded = true) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val detail = runCatching { repository.detail(characterId) }
            val chats = runCatching { repository.chatsWith(characterId) }
            detail.getOrNull()?.creatorId?.let(::checkFollow)
            _state.update {
                it.copy(
                    detail = detail.getOrNull() ?: it.detail,
                    chats = chats.getOrDefault(it.chats),
                    loading = false,
                    error = (detail.exceptionOrNull() as? ApiError)?.takeIf { _ -> it.detail == null && detail.isFailure },
                )
            }
        }
    }

    fun startChat(onStarted: (Long) -> Unit) {
        if (_state.value.startingChat) return
        viewModelScope.launch {
            _state.update { it.copy(startingChat = true, startError = null) }
            runCatching { chatRepository.createChat(characterId, personas.current.value) }
                .onSuccess { id ->
                    _state.update { it.copy(startingChat = false) }
                    onStarted(id)
                }
                .onFailure { e ->
                    _state.update { it.copy(startingChat = false, startError = e as? ApiError ?: ApiError.Unknown(e)) }
                }
        }
    }

    fun dismissStartError() {
        _state.update { it.copy(startError = null) }
    }
}
