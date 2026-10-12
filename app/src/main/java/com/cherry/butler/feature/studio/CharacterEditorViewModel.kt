package com.cherry.butler.feature.studio

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.CharacterDraft
import com.cherry.butler.core.data.PictureUpload
import com.cherry.butler.core.data.TokenCounts
import com.cherry.butler.core.data.remote.CharacterRemoteSource
import com.cherry.butler.core.data.remote.StudioRemoteSource
import com.cherry.butler.core.data.remote.TagRemoteSource
import com.cherry.butler.core.data.remote.dto.TagDto
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CharacterForm(
    val draft: CharacterDraft = CharacterDraft(),
    /** The picture on Janitor, to show; null for none. */
    val pictureUrl: String? = null,
    /** The file just picked, shown at once while (and after) it goes up. */
    val localPicture: String? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** Changed since it was loaded or last saved; kept on the device as a draft meanwhile. */
    val dirty: Boolean = false,
) {
    /** Janitor takes no character without a picture uploaded to it. */
    val hasPicture: Boolean get() = draft.avatar.isNotBlank()

    /** What to show for the picture. */
    val picture: String? get() = localPicture ?: pictureUrl

    val savable: Boolean get() = loaded && draft.savable && hasPicture
}

/** A picture on its way up: how far it has got, and why it stopped if it did. */
data class PictureJob(val stage: PictureUpload.MediaStage, val failure: String? = null)

/**
 * Writing a character, new or one of yours: every field the website's form has, the tokens
 * counted as Janitor counts them, and a save that goes as the website's does. A new one is
 * saved private; publishing is a step of its own, in Your characters.
 *
 * Each save needs a fresh Cloudflare Turnstile token, earned by the person on the device
 * (see [StudioRemoteSource]); the screen asks for it and hands it to [save].
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class CharacterEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studio: StudioRemoteSource,
    private val characters: CharacterRemoteSource,
    private val tagSource: TagRemoteSource,
    private val pictures: PictureUpload,
    private val profiles: com.cherry.butler.core.data.ProfileRepository,
    private val drafts: com.cherry.butler.core.data.CharacterDrafts,
) : ViewModel() {

    /** Your name, for the card's `@creator` line as Browse will show it. */
    val creatorName: StateFlow<String> = MutableStateFlow(profiles.profile.value?.userName.orEmpty()).also { flow ->
        viewModelScope.launch { runCatching { profiles.profile() }.onSuccess { flow.value = it.userName } }
    }

    /**
     * A saved character's switches, sent the moment they change (as the website does, with no
     * check); null for a new one, which has none until its first save.
     */
    private val _live = MutableStateFlow<LiveSettings?>(null)
    val live: StateFlow<LiveSettings?> = _live.asStateFlow()

    data class LiveSettings(
        val isPublic: Boolean,
        val allowProxy: Boolean,
        val showDefinition: Boolean,
        val allowPublishedChats: Boolean,
        val comments: com.cherry.butler.core.data.remote.CommentMode,
        val error: String? = null,
    )

    private val arg: String = savedStateHandle.get<String>(ARG) ?: NEW

    /** A draft being picked up again, when the editor was opened on one. */
    private val resumed: com.cherry.butler.core.data.StoredDraft? =
        arg.removePrefix(DRAFT_PREFIX).takeIf { arg.startsWith(DRAFT_PREFIX) }?.let(drafts::get)

    /** The character this writes over; null while it's new (until its first save). */
    private val characterId: String? = resumed?.characterId ?: arg.takeUnless { it == NEW || it.startsWith(DRAFT_PREFIX) }
    val isNew: Boolean get() = characterId == null

    /** Where this writing is kept on the device; the same across a rotation or a process death. */
    private val draftId: String = resumed?.id
        ?: characterId?.let(com.cherry.butler.core.data.StoredDraft::idFor)
        ?: savedStateHandle.get<String>(DRAFT_ID) ?: com.cherry.butler.core.data.StoredDraft.newId().also { savedStateHandle[DRAFT_ID] = it }

    private val _form = MutableStateFlow(
        resumed?.let { CharacterForm(draft = it.draft, pictureUrl = it.pictureUrl, loaded = true, dirty = true) }
            ?: CharacterForm(loaded = characterId == null),
    )
    val form: StateFlow<CharacterForm> = _form.asStateFlow()

    /** Changes to this character kept on the device from before, offered when it opens. */
    private val _restore = MutableStateFlow<com.cherry.butler.core.data.StoredDraft?>(null)
    val restore: StateFlow<com.cherry.butler.core.data.StoredDraft?> = _restore.asStateFlow()

    private val _picture = MutableStateFlow<PictureJob?>(null)
    val picture: StateFlow<PictureJob?> = _picture.asStateFlow()

    /** Counted off the main thread once typing pauses; the first count waits for the table. */
    private val _tokens = MutableStateFlow<TokenCounts?>(null)
    val tokens: StateFlow<TokenCounts?> = _tokens.asStateFlow()

    private val _tags = MutableStateFlow<List<TagDto>>(emptyList())
    val tags: StateFlow<List<TagDto>> = _tags.asStateFlow()

    init {
        viewModelScope.launch { runCatching { tagSource.tags() }.onSuccess { list -> _tags.value = list.sortedBy { it.name.lowercase() } } }
        characterId?.let { id -> load(id, offerDraft = resumed == null) }
        viewModelScope.launch {
            _form.map { it.draft }.distinctUntilChanged().debounce(COUNT_PAUSE_MS).collect { d ->
                _tokens.value = withContext(Dispatchers.Default) { TokenCounts.of(d) }
            }
        }
        // The writing, kept on the device as it's written: leaving never loses it.
        viewModelScope.launch {
            _form.debounce(DRAFT_PAUSE_MS).collect { f ->
                if (f.loaded && f.dirty && !f.saving) {
                    drafts.put(com.cherry.butler.core.data.StoredDraft(draftId, characterId, f.draft, f.pictureUrl))
                }
            }
        }
    }

    /** Picks up the changes kept from before, in place of what's saved on Janitor. */
    fun restoreDraft() {
        val kept = _restore.value ?: return
        _restore.value = null
        _form.update { it.copy(draft = kept.draft, pictureUrl = kept.pictureUrl ?: it.pictureUrl, dirty = true) }
    }

    /** Starts from what's saved on Janitor; the kept changes go. */
    fun discardDraft() {
        _restore.value = null
        drafts.delete(draftId)
    }

    private fun load(id: String, offerDraft: Boolean) {
        viewModelScope.launch {
            runCatching { characters.detail(id) }
                .onSuccess { c ->
                    val saved = CharacterDraft.of(c)
                    // A draft picked up again keeps its writing; only the switches come from Janitor.
                    if (resumed == null) _form.value = CharacterForm(draft = saved, pictureUrl = JanitorConfig.avatarUrl(c.avatar), loaded = true)
                    if (offerDraft) drafts.get(draftId)?.takeIf { it.draft != saved }?.let { _restore.value = it }
                    val comments = runCatching { studio.commentMode(id) }.getOrDefault(com.cherry.butler.core.data.remote.CommentMode.Open)
                    _live.value = LiveSettings(c.isPublic, c.allowProxy, c.showdefinition, c.allowPublishedChats, comments)
                }
                .onFailure { e -> _form.update { it.copy(error = "Couldn't load it. ${e.userMessage()}") } }
        }
    }

    fun edit(change: (CharacterDraft) -> CharacterDraft) = _form.update { it.copy(draft = change(it.draft), dirty = true, error = null) }

    /**
     * The picture goes up the moment it's picked (as the website does), and shows at once from
     * the file meanwhile. Janitor files it as a bot's; there's no moderation to wait on for it.
     */
    fun pickPicture(uri: Uri) {
        val job = _picture.value
        if (job != null && job.failure == null) return
        _form.update { it.copy(localPicture = uri.toString(), error = null) }
        _picture.value = PictureJob(PictureUpload.MediaStage.Uploading)
        viewModelScope.launch {
            runCatching { pictures.upload(uri, "bot") }
                .onSuccess { file ->
                    _form.update { it.copy(draft = it.draft.copy(avatar = file), pictureUrl = JanitorConfig.avatarUrl(file), dirty = true) }
                    _picture.value = null
                }
                .onFailure { e ->
                    // Back to the picture it had: the one picked never reached Janitor.
                    _form.update { it.copy(localPicture = null) }
                    _picture.value = PictureJob(PictureUpload.MediaStage.Uploading, failure = e.userMessage())
                }
        }
    }


    fun setIntro(index: Int, text: String) = edit { d -> d.copy(firstMessages = d.firstMessages.toMutableList().also { it[index] = text }) }

    fun addIntro() = edit { d -> d.copy(firstMessages = d.firstMessages + "") }

    /** One place up ([by] -1) or down (+1); the first is the one a chat opens with. */
    fun moveIntro(index: Int, by: Int) = edit { d ->
        val to = index + by
        if (to !in d.firstMessages.indices) d
        else d.copy(firstMessages = d.firstMessages.toMutableList().also { it.add(to, it.removeAt(index)) })
    }

    fun removeIntro(index: Int) = edit { d ->
        val left = d.firstMessages.filterIndexed { i, _ -> i != index }
        d.copy(firstMessages = left.ifEmpty { listOf("") })
    }

    fun toggleTag(id: Int) = edit { d -> d.copy(tagIds = if (id in d.tagIds) d.tagIds - id else d.tagIds + id) }

    fun clearTags() = edit { d -> d.copy(tagIds = emptyList()) }

    fun addCustomTag(raw: String) {
        val tag = com.cherry.butler.feature.browse.BrowseViewModel.normalizeCustomTag(raw) ?: return
        edit { d -> if (tag in d.customTags) d else d.copy(customTags = d.customTags + tag) }
    }

    fun removeCustomTag(tag: String) = edit { d -> d.copy(customTags = d.customTags - tag) }

    /** Public or private, at once; put back if Janitor says no. */
    fun setPublic(on: Boolean) = changeLive({ it.copy(isPublic = on) }, { it.copy(isPublic = !on) }) { id -> studio.setPublic(id, on) }

    /** One of the switches (`allow_proxy`, `showdefinition`, `allow_published_chats`), at once. */
    fun setSwitch(key: String, on: Boolean) = changeLive(
        { it.withSwitch(key, on) },
        { it.withSwitch(key, !on) },
    ) { id -> studio.setSwitches(id, mapOf(key to on)) }

    fun setComments(mode: com.cherry.butler.core.data.remote.CommentMode) {
        val before = _live.value?.comments ?: return
        changeLive({ it.copy(comments = mode) }, { it.copy(comments = before) }) { id -> studio.setCommentMode(id, mode) }
    }

    private fun LiveSettings.withSwitch(key: String, on: Boolean) = when (key) {
        MyCharactersViewModel.SWITCH_PROXY -> copy(allowProxy = on)
        MyCharactersViewModel.SWITCH_DEFINITION -> copy(showDefinition = on)
        MyCharactersViewModel.SWITCH_PUBLISHED_CHATS -> copy(allowPublishedChats = on)
        else -> this
    }

    private fun changeLive(apply: (LiveSettings) -> LiveSettings, undo: (LiveSettings) -> LiveSettings, send: suspend (String) -> Unit) {
        val id = characterId ?: return
        _live.update { it?.let(apply)?.copy(error = null) }
        viewModelScope.launch {
            runCatching { send(id) }.onFailure { e -> _live.update { it?.let(undo)?.copy(error = "Not changed. ${e.userMessage()}") } }
        }
    }

    /** A picture for the description, from the media library once Janitor approves it. */
    suspend fun uploadDescriptionPicture(uri: Uri, onStage: (PictureUpload.MediaStage) -> Unit) = pictures.uploadMedia(uri, onStage)

    fun failCheck(message: String) = _form.update { it.copy(error = message) }

    /**
     * Saves with [turnstileToken], the person's fresh check: the picture first (Janitor's
     * upload, as for a persona, filed as a bot's), then the whole definition. [onDone] gets the
     * character's id.
     */
    fun save(turnstileToken: String, onDone: (String) -> Unit) {
        val f = _form.value
        if (f.saving || !f.savable) return
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching {
                val draft = f.draft
                val tokens = withContext(Dispatchers.Default) { TokenCounts.of(draft) }
                val id = characterId
                if (id == null) {
                    studio.create(draft.toBody(tokens, creating = true), turnstileToken)
                } else {
                    studio.update(id, draft.toBody(tokens, creating = false), turnstileToken)
                    id
                }
            }.onSuccess { id ->
                // On Janitor now: the device's copy has done its job.
                drafts.delete(draftId)
                _form.update { it.copy(saving = false, dirty = false) }
                onDone(id)
            }.onFailure { e ->
                _form.update { it.copy(saving = false, error = "Not saved. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
            }
        }
    }

    companion object {
        const val ARG = "characterId"
        const val NEW = "new"
        /** The route argument for picking a kept draft up again: "draft:{draft id}". */
        const val DRAFT_PREFIX = "draft:"
        private const val DRAFT_ID = "draftId"
        private const val COUNT_PAUSE_MS = 300L
        private const val DRAFT_PAUSE_MS = 600L
    }
}
