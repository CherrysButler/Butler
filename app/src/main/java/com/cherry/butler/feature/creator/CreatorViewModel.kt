package com.cherry.butler.feature.creator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.remote.CommunityRemoteSource
import com.cherry.butler.core.data.remote.CreatorProfileDto
import com.cherry.butler.core.data.remote.CreatorRemoteSource
import com.cherry.butler.core.data.remote.LorebookDto
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.toDomain
import com.cherry.butler.core.data.toEntity
import com.cherry.butler.core.markdown.CreatorCss
import com.cherry.butler.core.model.Character
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A creator's page: their profile and look, their characters (newest first, paged as the
 * website pages them) and their public lorebooks. Asked for on Reddit (0.2 thread).
 */
@HiltViewModel
class CreatorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remote: CreatorRemoteSource,
    private val community: CommunityRemoteSource,
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle[Routes.ARG_USER_ID])

    data class Shelf<T>(
        val items: List<T> = emptyList(),
        val total: Int? = null,
        val page: Int = 0,
        val end: Boolean = false,
        val loading: Boolean = false,
        val error: ApiError? = null,
    )

    data class State(
        val profile: CreatorProfileDto? = null,
        val profileError: ApiError? = null,
        /** The creator's stylesheet, as far as Butler's page has the parts it names. */
        val looks: Map<CreatorCss.Part, CreatorCss.Look> = emptyMap(),
        val following: Boolean? = null,
        val characters: Shelf<Character> = Shelf(),
        val lorebooks: Shelf<LorebookDto> = Shelf(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        loadProfile()
        moreCharacters()
        moreLorebooks()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(profileError = null) }
            runCatching { remote.profile(userId) }
                .onSuccess { p -> _state.update { it.copy(profile = p, looks = CreatorCss.parse(p.style?.customStyle)) } }
                .onFailure { e -> _state.update { it.copy(profileError = e as? ApiError ?: ApiError.Unknown(e)) } }
            runCatching { community.isFollowing(userId) }.onSuccess { on -> _state.update { it.copy(following = on) } }
        }
    }

    fun moreCharacters() {
        val shelf = _state.value.characters
        if (shelf.loading || shelf.end) return
        _state.update { it.copy(characters = shelf.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val page = shelf.page + 1
            runCatching { remote.characters(userId, page) }
                .onSuccess { dto ->
                    val now = System.currentTimeMillis()
                    val more = dto.data.mapIndexed { i, c -> c.toEntity("creator:$userId", shelf.items.size + i, now).toDomain() }
                    _state.update {
                        it.copy(
                            characters = it.characters.copy(
                                items = (it.characters.items + more).distinctBy { c -> c.id },
                                total = dto.total.takeIf { t -> t > 0 } ?: it.characters.total,
                                page = page,
                                end = dto.data.size < CharacterRemoteSource.PAGE_SIZE,
                                loading = false,
                            ),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(characters = it.characters.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e))) } }
        }
    }

    fun moreLorebooks() {
        val shelf = _state.value.lorebooks
        if (shelf.loading || shelf.end) return
        _state.update { it.copy(lorebooks = shelf.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val page = shelf.page + 1
            runCatching { remote.lorebooks(userId, page) }
                .onSuccess { dto ->
                    _state.update {
                        val items = (it.lorebooks.items + dto.scripts).distinctBy { l -> l.id }
                        it.copy(
                            lorebooks = it.lorebooks.copy(
                                items = items,
                                total = dto.total,
                                page = page,
                                end = dto.scripts.isEmpty() || items.size >= dto.total,
                                loading = false,
                            ),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(lorebooks = it.lorebooks.copy(loading = false, error = e as? ApiError ?: ApiError.Unknown(e))) } }
        }
    }

    fun toggleFollow() {
        val now = _state.value.following ?: return
        _state.update { it.copy(following = !now) }
        viewModelScope.launch {
            runCatching { community.setFollowing(userId, !now) }.onFailure { _state.update { it.copy(following = now) } }
        }
    }
}
