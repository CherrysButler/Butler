package com.cherry.butler.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.CommentMode
import com.cherry.butler.core.data.remote.StudioRemoteSource
import com.cherry.butler.core.data.remote.StudioRemoteSource.Privacy
import com.cherry.butler.core.data.remote.StudioRemoteSource.Sort
import com.cherry.butler.core.data.toDomain
import com.cherry.butler.core.data.toEntity
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of your characters, with what only its creator sees. */
data class MyCharacter(val character: Character, val isPublic: Boolean)

/** A character's switches and comments, as its settings sheet shows them. */
data class CharacterSettings(
    val id: String,
    val name: String,
    val loaded: Boolean = false,
    val allowProxy: Boolean = true,
    val showDefinition: Boolean = false,
    val allowPublishedChats: Boolean = true,
    val comments: CommentMode = CommentMode.Open,
    val error: String? = null,
)

/**
 * The characters you made, private ones too (`GET /characters/v2/mine`), paged as browsing is,
 * with the website's filters, and what a creator does with each: publish or hide it, its
 * switches, who may comment, and delete. Writing a character is the editor's.
 */
@HiltViewModel
class MyCharactersViewModel @Inject constructor(
    private val studio: StudioRemoteSource,
    private val characters: CharacterRemoteSource,
    private val profiles: ProfileRepository,
    private val characterDrafts: com.cherry.butler.core.data.CharacterDrafts,
) : ViewModel() {

    /** What's being written on this device and isn't on Janitor yet, newest first. */
    val drafts: StateFlow<List<com.cherry.butler.core.data.StoredDraft>> = characterDrafts.drafts

    fun deleteDraft(id: String) = characterDrafts.delete(id)

    data class State(
        val items: List<MyCharacter> = emptyList(),
        val total: Int? = null,
        val page: Int = 0,
        val end: Boolean = false,
        val loading: Boolean = false,
        val error: ApiError? = null,
        val privacy: Privacy = Privacy.All,
        val sort: Sort = Sort.Latest,
        val search: String = "",
        /** A word after something done, or not done. */
        val notice: String? = null,
        /** Ids with a change on its way to Janitor. */
        val busy: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _settings = MutableStateFlow<CharacterSettings?>(null)
    val settings: StateFlow<CharacterSettings?> = _settings.asStateFlow()

    private var pageJob: Job? = null
    private var searchJob: Job? = null

    init {
        more()
    }

    /** From the top: after a filter changes, or coming back from the editor. */
    fun reload() {
        pageJob?.cancel()
        _state.update { it.copy(items = emptyList(), total = null, page = 0, end = false, loading = false, error = null) }
        more()
    }

    fun more() {
        val s = _state.value
        if (s.loading || s.end) return
        _state.update { it.copy(loading = true, error = null) }
        pageJob = viewModelScope.launch {
            val page = s.page + 1
            runCatching {
                val me = profiles.profile().id
                studio.mine(me, page, s.privacy, s.sort, s.search.takeIf { it.isNotBlank() }, emptyList())
            }.onSuccess { dto ->
                val now = System.currentTimeMillis()
                val more = dto.data.mapIndexed { i, c -> MyCharacter(c.toEntity("mine", s.items.size + i, now).toDomain(), c.isPublic) }
                _state.update {
                    it.copy(
                        items = (it.items + more).distinctBy { m -> m.character.id },
                        total = dto.total,
                        page = page,
                        end = dto.data.size < CharacterRemoteSource.PAGE_SIZE,
                        loading = false,
                    )
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e)) }
            }
        }
    }

    fun setPrivacy(privacy: Privacy) {
        if (privacy == _state.value.privacy) return
        _state.update { it.copy(privacy = privacy) }
        reload()
    }

    fun setSort(sort: Sort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    /** Asks once typing pauses. */
    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_PAUSE_MS)
            reload()
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** Public to private or back: the card changes at once, and back again if Janitor says no. */
    fun togglePublic(item: MyCharacter) {
        val id = item.character.id
        val to = !item.isPublic
        _state.update { it.copy(items = it.items.map { m -> if (m.character.id == id) m.copy(isPublic = to) else m }, busy = it.busy + id) }
        viewModelScope.launch {
            runCatching { studio.setPublic(id, to) }
                .onSuccess { _state.update { it.copy(busy = it.busy - id, notice = if (to) "${item.character.name} is public." else "${item.character.name} is private now.") } }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            items = it.items.map { m -> if (m.character.id == id) m.copy(isPublic = !to) else m },
                            busy = it.busy - id,
                            notice = "Not changed. ${e.userMessage()}",
                        )
                    }
                }
        }
    }

    fun delete(item: MyCharacter) {
        val id = item.character.id
        _state.update { it.copy(busy = it.busy + id) }
        viewModelScope.launch {
            runCatching { studio.delete(id) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { m -> m.character.id == id },
                            total = it.total?.minus(1)?.coerceAtLeast(0),
                            busy = it.busy - id,
                            notice = "${item.character.name} is deleted.",
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = it.busy - id, notice = "Not deleted. ${e.userMessage()}") } }
        }
    }

    // ---- a character's settings ----------------------------------------------------------

    fun openSettings(item: MyCharacter) {
        val id = item.character.id
        _settings.value = CharacterSettings(id = id, name = item.character.name)
        viewModelScope.launch {
            runCatching {
                val d = characters.detail(id)
                val comments = runCatching { studio.commentMode(id) }.getOrDefault(CommentMode.Open)
                CharacterSettings(id, d.name, true, d.allowProxy, d.showdefinition, d.allowPublishedChats, comments)
            }.onSuccess { s -> _settings.update { if (it?.id == id) s else it } }
                .onFailure { e -> _settings.update { if (it?.id == id) it.copy(error = "Couldn't load. ${e.userMessage()}") else it } }
        }
    }

    fun closeSettings() { _settings.value = null }

    /** One switch, sent as it's flipped (as the website does); put back if Janitor says no. */
    fun setSwitch(key: String, on: Boolean) {
        val before = _settings.value ?: return
        fun CharacterSettings.with(value: Boolean) = when (key) {
            SWITCH_PROXY -> copy(allowProxy = value)
            SWITCH_DEFINITION -> copy(showDefinition = value)
            SWITCH_PUBLISHED_CHATS -> copy(allowPublishedChats = value)
            else -> this
        }
        _settings.value = before.with(on).copy(error = null)
        viewModelScope.launch {
            runCatching { studio.setSwitches(before.id, mapOf(key to on)) }
                .onFailure { e -> _settings.update { if (it?.id == before.id) it.with(!on).copy(error = "Not changed. ${e.userMessage()}") else it } }
        }
    }

    fun setComments(mode: CommentMode) {
        val before = _settings.value ?: return
        _settings.value = before.copy(comments = mode, error = null)
        viewModelScope.launch {
            runCatching { studio.setCommentMode(before.id, mode) }
                .onFailure { e -> _settings.update { if (it?.id == before.id) it.copy(comments = before.comments, error = "Not changed. ${e.userMessage()}") else it } }
        }
    }

    companion object {
        const val SWITCH_PROXY = "allow_proxy"
        const val SWITCH_DEFINITION = "showdefinition"
        const val SWITCH_PUBLISHED_CHATS = "allow_published_chats"
        private const val SEARCH_PAUSE_MS = 400L
    }
}
