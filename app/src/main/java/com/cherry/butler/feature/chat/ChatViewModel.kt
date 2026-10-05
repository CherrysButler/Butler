package com.cherry.butler.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.ChatRepository
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.local.ChatEntity
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.SendJobEntity
import com.cherry.butler.core.data.local.SendJobState
import com.cherry.butler.core.data.LastPlace
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.Provider
import com.cherry.butler.core.data.SettingsRepository
import com.cherry.butler.core.generation.MemoryService
import com.cherry.butler.core.generation.SendPipeline
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cherry.butler.core.background.BackgroundReplies
import com.cherry.butler.core.background.ChatPresence
import com.cherry.butler.core.generation.ContextGauge
import com.cherry.butler.core.data.OpenRouterOptionsStore
import com.cherry.butler.core.data.AiSettings
import com.cherry.butler.core.data.ProxyDraft
import com.cherry.butler.core.generation.ChoicesService
import com.cherry.butler.core.generation.SuggestionService
import com.cherry.butler.core.data.ChatBackground
import com.cherry.butler.core.data.ChatBackgrounds
import com.cherry.butler.core.data.ChatTransfer
import com.cherry.butler.core.data.ChatFormats
import com.cherry.butler.ui.components.ExportFormat
import javax.inject.Inject

/** The inline editor's own state; the words themselves live in saved state. */
data class EditStatus(val saving: Boolean = false, val error: ApiError? = null)

/**
 * The chat screen's state: the mirror's transcript, the outbox jobs for this chat, the
 * in-flight reply at token rate, the draft, and the edit in progress. Sends, swipes and
 * continues are writes to the outbox; the pipeline does the network on its own scope, so
 * leaving the screen never abandons one. Edits and deletes ask the server first and say
 * so plainly when it refuses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val profile: ProfileRepository,
    private val pipeline: SendPipeline,
    private val memory: MemoryService,
    private val memoryPrefs: MemoryPrefs,
    private val lastPlace: LastPlace,
    private val personas: PersonaRepository,
    private val aiSettings: SettingsRepository,
    private val background: BackgroundReplies,
    private val presence: ChatPresence,
    private val gauge: ContextGauge,
    private val openRouter: OpenRouterOptionsStore,
    private val choices: ChoicesService,
    private val transfer: ChatTransfer,
    private val suggestions: SuggestionService,
    private val backgrounds: ChatBackgrounds,
) : ViewModel() {

    /** A branch or copy being written: (done, total), or null when none is. */
    private val _copying = MutableStateFlow<Pair<Int, Int>?>(null)
    val copying: StateFlow<Pair<Int, Int>?> = _copying.asStateFlow()
    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()

    fun dismissTransferError() { _transferError.value = null }

    /** The chat as a file's text, in [format]. */
    suspend fun exportText(format: ExportFormat): Pair<String, String> {
        val chat = transfer.export(chatId)
        val now = System.currentTimeMillis()
        val text = when (format) {
            ExportFormat.Butler -> ChatFormats.toButler(chat, now)
            ExportFormat.SillyTavern -> ChatFormats.toSillyTavern(chat, now)
        }
        val name = "${chat.characterName.ifBlank { "chat" }.replace(Regex("[\\/:*?\"<>|]"), "_")} - ${java.time.LocalDate.now()}.${format.extension}"
        return name to text
    }

    fun reportTransfer(message: String) { _transferError.value = message }

    /** A new chat holding the conversation up to [message]: Janitor's fork, or a line-by-line copy. */
    fun branch(message: MessageEntity, onOpened: (Long) -> Unit) {
        if (_copying.value != null) return
        viewModelScope.launch {
            _copying.value = 0 to 0
            runCatching { transfer.branch(chatId, message) { done, total -> _copying.value = done to total } }
                .onSuccess { id -> _copying.value = null; onOpened(id) }
                .onFailure { e -> _copying.value = null; _transferError.value = "Couldn't branch. ${e.userMessage()}" }
        }
    }

    /** Suggested next moves under the last reply, if asked for (see [ChoicesService]). */
    val choicesState: StateFlow<ChoicesService.State?> by lazy {
        choices.states.map { it[chatId] }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** Choices need the proxy path: JLLM never hands back a payload to add the question to. */
    val choicesAvailable: StateFlow<Boolean> = aiSettings.settings
        .map { it?.provider == Provider.Proxy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun requestChoices(forReply: Long) = choices.request(chatId, forReply)

    fun hideChoices() = choices.dismiss(chatId)

    /** A picked choice goes out as the user's line; the draft in the composer is left alone. */
    fun sendChoice(text: String) {
        choices.dismiss(chatId)
        viewModelScope.launch {
            commitIntro()
            val active = activePersona.value
            val persona = if (active != null) active.id else repository.chat(chatId)?.personaId
            pipeline.send(chatId, text, persona)
        }
    }

    /** How full this chat's context was on its last reply, for the chip's ring. */
    val contextPercent: StateFlow<Int?> by lazy {
        gauge.forChat(chatId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** The model sheet: what can answer, and the switch. */
    val aiSettingsState: StateFlow<AiSettings?> = aiSettings.settings

    private val _modelBusy = MutableStateFlow(false)
    val modelBusy: StateFlow<Boolean> = _modelBusy.asStateFlow()
    private val _modelError = MutableStateFlow<String?>(null)
    val modelError: StateFlow<String?> = _modelError.asStateFlow()

    fun presetFor(proxyId: String): String = openRouter.get(proxyId).preset

    private val _jllmAllowance = MutableStateFlow<Pair<Int, Int>?>(null)
    val jllmAllowance: StateFlow<Pair<Int, Int>?> = _jllmAllowance.asStateFlow()

    fun openModels() {
        _modelError.value = null
        viewModelScope.launch { runCatching { aiSettings.load(refresh = true) } }
        viewModelScope.launch { _jllmAllowance.value = aiSettings.jllmAllowance() }
    }

    private fun modelChange(change: suspend () -> Unit) {
        if (_modelBusy.value) return
        viewModelScope.launch {
            _modelBusy.value = true
            _modelError.value = null
            runCatching { change() }
                .onFailure { _modelError.value = "Not changed. ${it.userMessage()}" }
            _modelBusy.value = false
        }
    }

    fun selectProxy(id: String) = modelChange {
        if (aiSettings.settings.value?.provider != Provider.Proxy) aiSettings.setProvider(Provider.Proxy)
        aiSettings.selectProxy(id)
    }

    fun useJllm() = modelChange { aiSettings.setProvider(Provider.Janitor) }

    /** Only the model changes; name, endpoint, key and prompt stay as saved. */
    fun saveModel(proxyId: String, model: String) = modelChange {
        val proxy = aiSettings.settings.value?.proxies?.firstOrNull { it.id == proxyId } ?: return@modelChange
        val saved = aiSettings.saveProxy(ProxyDraft(proxy.id, proxy.name, proxy.apiUrl, model, null, proxy.promptId))
        // Janitor answers 200 to anything; read the result back before calling it done.
        check(saved.proxies.firstOrNull { it.id == proxyId }?.model == model) { "Janitor didn't keep the new model." }
    }

    /** The top bar's chip: which model answers, as Janitor's "using proxy". */
    val providerLabel: StateFlow<String?> = aiSettings.settings
        .map { s ->
            when {
                s == null -> null
                s.provider == Provider.Janitor -> if (s.routerEnabled) "Router" else "JLLM"
                else -> s.selectedProxy?.name?.takeIf { it.isNotBlank() } ?: "Proxy"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _startingChat = MutableStateFlow(false)
    val startingChat: StateFlow<Boolean> = _startingChat.asStateFlow()

    private val ratingJobs = HashMap<Long, kotlinx.coroutines.Job>()

    /**
     * One rating at a time per reply: a second tap waits for the first to finish, so the
     * last star tapped is the one Janitor and the phone keep.
     */
    fun rate(message: MessageEntity, rating: Int) {
        val previous = ratingJobs[message.localId]
        ratingJobs[message.localId] = viewModelScope.launch {
            previous?.join()
            runCatching { repository.rateMessage(chatId, message, rating) }
                .onFailure { _notices.trySend("Rating not saved. ${(it as? ApiError ?: ApiError.Unknown(it)).userMessage()}") }
        }
    }

    fun deleteChat(onDeleted: () -> Unit) {
        viewModelScope.launch {
            // Whatever the outbox was doing for this chat stops first, as for deleteFrom.
            pipeline.abandon(chatId)
            runCatching { repository.deleteChat(chatId) }
                .onSuccess { onDeleted() }
                .onFailure { _notices.trySend("Not deleted. ${(it as? ApiError ?: ApiError.Unknown(it)).userMessage()}") }
        }
    }

    /** The menu's "New chat": same character, the persona this chat is played as. */
    fun startNewChat(onStarted: (Long) -> Unit) {
        val characterId = chat.value?.characterId ?: return
        if (_startingChat.value) return
        _startingChat.value = true
        viewModelScope.launch {
            runCatching { repository.createChat(characterId, activePersona.value) }
                .onSuccess { onStarted(it) }
                .onFailure { _notices.trySend("Couldn't start a new chat. ${(it as? ApiError ?: ApiError.Unknown(it)).userMessage()}") }
            _startingChat.value = false
        }
    }

    val chatId: Long = checkNotNull(savedStateHandle[Routes.ARG_CHAT_ID])

    init {
        // A draft outlives the app being stopped: seed the composer from disk on a fresh start.
        if (savedStateHandle.get<String>(KEY_DRAFT) == null) savedStateHandle[KEY_DRAFT] = lastPlace.draft(chatId)
    }

    val chat: StateFlow<ChatEntity?> = repository.observeChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transcript: StateFlow<List<MessageEntity>> = repository.observeTranscript(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [transcript] as drawn: the opening line shows the intro picked but not yet sent. */
    val shownTranscript: StateFlow<List<MessageEntity>> by lazy {
        combine(transcript, pendingIntro) { rows, intro ->
            val opening = rows.firstOrNull()
            if (intro == null || opening == null || !opening.isBot || rows.any { !it.isBot }) rows
            else listOf(opening.copy(text = intro)) + rows.drop(1)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /**
     * Fills `{{user}}` until the chat's own detail arrives: the profile, which plays in any
     * chat that names no persona.
     */
    val fallbackPersonaName: StateFlow<String?> = profile.profile
        .map { p -> p?.name?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val personaOptions: StateFlow<List<PersonaOption>> = personas.options
    val personaGroups = personas.groups

    /** A persona picked from the composer chip during this visit; null until one is. */
    private data class Pick(val id: String?)
    private val picked = MutableStateFlow<Pick?>(null)

    /**
     * Who the next line is sent as. Janitor records the persona on every message
     * (`metadata.persona_id`), so a chat can change hands: the chip's pick if there is
     * one, else whoever wrote the last line, else the persona the chat was started with.
     */
    val activePersona: StateFlow<PersonaOption?> = combine(picked, chat, transcript, personas.options) { pick, chat, rows, options ->
        val id = when {
            pick != null -> pick.id
            rows.any { !it.isBot } -> rows.last { !it.isBot }.personaId
            else -> chat?.personaId
        }
        options.firstOrNull { it.id == id } ?: options.firstOrNull { it.id == null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun pickPersona(option: PersonaOption) {
        picked.value = Pick(option.id)
    }

    init {
        viewModelScope.launch { runCatching { aiSettings.load() } }
        // The chip's list: a chat can be the first screen after a cold start.
        viewModelScope.launch { runCatching { personas.load() } }
    }

    /** Outbox jobs that still matter to this screen: everything active plus terminal failures. */
    val jobs: StateFlow<List<SendJobEntity>> = pipeline.observeJobs(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The reply being written, per bot row, at token rate. */
    val live: StateFlow<Map<Long, SendPipeline.LiveReply>> = pipeline.live

    /** Ticks once a second only while a retry countdown is on screen. */
    val now: StateFlow<Long> = jobs
        .map { list -> list.any { it.state == SendJobState.WAITING_RETRY } }
        .flatMapLatest { counting ->
            if (!counting) flowOf(System.currentTimeMillis())
            else flow { while (true) { emit(System.currentTimeMillis()); delay(1_000) } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /** The composer's text, kept in saved state so process death cannot lose it. */
    val draft: StateFlow<String> = savedStateHandle.getStateFlow(KEY_DRAFT, "")

    /** The line being edited, or -1; and the words in the editor. Both survive process death. */
    val editingId: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_EDIT_ID, NO_EDIT)
    val editDraft: StateFlow<String> = savedStateHandle.getStateFlow(KEY_EDIT_TEXT, "")

    private val _editStatus = MutableStateFlow(EditStatus())
    val editStatus: StateFlow<EditStatus> = _editStatus.asStateFlow()

    /** This chat's summary being written, if one is. */
    val memoryRun: StateFlow<MemoryService.Run?> = memory.runs
        .map { it[chatId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val replacesHistory: StateFlow<Boolean> = memoryPrefs.replacesHistory
    val autoSummarize: StateFlow<Boolean> = memoryPrefs.autoSummarize

    private val _summaryEdit = MutableStateFlow(EditStatus())
    val summaryEdit: StateFlow<EditStatus> = _summaryEdit.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<ApiError?>(null)
    val refreshError: StateFlow<ApiError?> = _refreshError.asStateFlow()

    private val _notices = Channel<String>(Channel.BUFFERED)
    /** One-line outcomes for a snackbar: a delete that worked, a choice that did not save. */
    val notices: Flow<String> = _notices.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch { runCatching { profile.profile() } }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _refreshError.value = null
            runCatching { repository.refreshChat(chatId) }
                .onFailure { _refreshError.value = it as? ApiError ?: ApiError.Unknown(it) }
            _refreshing.value = false
        }
    }

    fun dismissRefreshError() {
        _refreshError.value = null
    }

    private var draftSave: kotlinx.coroutines.Job? = null

    /**
     * Saved state takes every keystroke (that is what survives process death); the on-disk
     * copy for the next cold start is written once typing settles. A blank draft (a line
     * just sent) is written at once, so a sent line can never come back as a draft.
     */
    fun onDraftChanged(text: String) {
        // Typing over a written line makes it the user's own: Undo no longer applies.
        (suggestion.value as? SuggestionService.State.Ready)?.let { if (it.text != text) suggestions.clear(chatId) }
        savedStateHandle[KEY_DRAFT] = text
        draftSave?.cancel()
        if (text.isBlank()) {
            lastPlace.saveDraft(chatId, text)
        } else {
            draftSave = viewModelScope.launch {
                delay(DRAFT_SAVE_MS)
                lastPlace.saveDraft(chatId, text)
            }
        }
    }

    override fun onCleared() {
        // Whatever is still pending goes to disk before the model goes.
        if (draftSave?.isActive == true) {
            draftSave?.cancel()
            lastPlace.saveDraft(chatId, draft.value)
        }
        super.onCleared()
    }

    /** The chat is on screen: replies landing here need no notification. */
    fun onShown() = presence.enter(chatId)

    fun onHidden() = presence.leave(chatId)

    /** Asked once, on the first send: that's when a reply might land while the user is away. */
    fun shouldAskNotifications(): Boolean = background.shouldAskPermission()

    fun notificationsAsked() = background.markPermissionAsked()

    // ---- background -------------------------------------------------------------------

    val chatBackground: StateFlow<ChatBackground?> by lazy {
        backgrounds.forChat(chatId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    // ---- write for me / enhance my draft ------------------------------------------------

    /** The line being written for the user, or the one just written (Undo / Again under it). */
    val suggestion: StateFlow<SuggestionService.State?> by lazy {
        suggestions.states.map { it[chatId] }.stateIn(viewModelScope, SharingStarted.Eagerly, suggestions.states.value[chatId])
    }

    init {
        // A finished line goes into the composer; a failure says so and leaves the draft be.
        viewModelScope.launch {
            suggestions.states.map { it[chatId] }.distinctUntilChanged().collect { state ->
                when (state) {
                    is SuggestionService.State.Ready -> if (draft.value != state.text) {
                        savedStateHandle[KEY_DRAFT] = state.text
                        lastPlace.saveDraft(chatId, state.text)
                    }
                    is SuggestionService.State.Failed -> {
                        suggestions.clear(chatId)
                        _notices.send("Couldn't write it. ${state.message}")
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Writes the next line from scratch, or rewrites what is in the composer. */
    fun writeForMe() {
        if (suggestion.value is SuggestionService.State.Writing) return
        suggestions.write(chatId, draft.value.trim(), pendingIntro.value)
    }

    fun stopWriting() = suggestions.stop(chatId)

    /** Puts back what was in the composer before it was written over. */
    fun undoWrite() {
        val state = suggestion.value ?: return
        suggestions.clear(chatId)
        onDraftChanged(state.original)
    }

    fun writeAgain() {
        val state = suggestion.value ?: return
        suggestions.write(chatId, state.original, pendingIntro.value)
    }

    /** Persists the draft as a send and clears the composer. The network happens elsewhere. */
    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        suggestions.clear(chatId)
        onDraftChanged("")
        choices.dismiss(chatId)
        viewModelScope.launch {
            commitIntro()
            val active = activePersona.value
            val persona = if (active != null) active.id else repository.chat(chatId)?.personaId
            pipeline.send(chatId, text, persona)
        }
    }

    fun stop() = pipeline.stop(chatId)

    fun retryNow(jobId: Long) {
        viewModelScope.launch { pipeline.retryNow(jobId) }
    }

    fun dismiss(jobId: Long) {
        viewModelScope.launch { pipeline.dismiss(jobId) }
    }

    fun continueReply(botLocalId: Long) {
        viewModelScope.launch { pipeline.continueReply(chatId, botLocalId) }
    }

    /** A fresh reply to the last line: the swipe past the last variant, or "Ask again". */
    fun askAgain() {
        viewModelScope.launch { pipeline.swipe(chatId) }
    }

    /** A new reply that follows [guidance] ("shorter", "more dialogue"); blank is a plain retry. */
    fun askAgainWith(guidance: String) {
        viewModelScope.launch { pipeline.swipe(chatId, guidance) }
    }

    /** Writes a stopped, empty reply again, replacing it. */
    fun regenerate(botLocalId: Long) {
        viewModelScope.launch { pipeline.regenerate(chatId, botLocalId) }
    }

    /** Steps the shown reply of a turn to another of its variants. */
    fun select(variants: List<MessageEntity>, chosen: MessageEntity) {
        // "Already main" is not "already chosen": the server can hold several mains.
        if (variants.count { it.isMain } == 1 && chosen.isMain) return
        viewModelScope.launch {
            runCatching { repository.selectVariant(chatId, variants, chosen) }
                .onFailure { e -> _notices.send("Couldn't keep that reply. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
        }
    }

    /**
     * The intro picked for the opening line, on screen only until the first send: stepping
     * through them costs no network, and a chat left without a word keeps what Janitor has.
     */
    private val pendingIntro: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_INTRO, null)

    fun pickIntro(opening: MessageEntity, text: String) {
        val stored = transcript.value.firstOrNull { it.localId == opening.localId }?.text
        savedStateHandle[KEY_INTRO] = text.takeIf { it != stored }
    }

    /**
     * Before the first line goes out: the picked intro becomes the opening on the phone
     * (so the reply answers it) and is sent to Janitor alongside, not ahead of, the line.
     */
    private suspend fun commitIntro() {
        val text = pendingIntro.value ?: return
        savedStateHandle[KEY_INTRO] = null
        val opening = transcript.value.firstOrNull()?.takeIf { it.isBot } ?: return
        repository.keepIntroLocally(chatId, opening, text)
        viewModelScope.launch {
            runCatching { repository.pushIntro(chatId, opening, text) }
                .onFailure { e -> _notices.send("The intro you picked wasn't saved on Janitor. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
        }
    }

    // ---- editing ----------------------------------------------------------------------

    fun startEdit(message: MessageEntity) {
        // Editing the opening starts from the intro on screen and saves it as the edit.
        savedStateHandle[KEY_INTRO] = null
        savedStateHandle[KEY_EDIT_ID] = message.localId
        savedStateHandle[KEY_EDIT_TEXT] = message.text
        _editStatus.value = EditStatus()
    }

    fun onEditChanged(text: String) {
        savedStateHandle[KEY_EDIT_TEXT] = text
    }

    fun cancelEdit() {
        savedStateHandle[KEY_EDIT_ID] = NO_EDIT
        savedStateHandle[KEY_EDIT_TEXT] = ""
        _editStatus.value = EditStatus()
    }

    fun saveEdit() {
        val id = editingId.value
        val text = editDraft.value.trim()
        val message = transcript.value.firstOrNull { it.localId == id } ?: return cancelEdit()
        if (text.isEmpty() || _editStatus.value.saving) return
        if (text == message.text) return cancelEdit()
        viewModelScope.launch {
            _editStatus.value = EditStatus(saving = true)
            runCatching { repository.editMessage(chatId, message, text) }
                .onSuccess { cancelEdit() }
                .onFailure { e -> _editStatus.update { EditStatus(error = e as? ApiError ?: ApiError.Unknown(e)) } }
        }
    }

    // ---- deleting ---------------------------------------------------------------------

    /** Deletes [from] and everything after it, here and on Janitor. */
    fun deleteFrom(from: MessageEntity) {
        viewModelScope.launch {
            pipeline.abandon(chatId)
            if (editingId.value != NO_EDIT) cancelEdit()
            runCatching { repository.deleteFrom(chatId, from) }
                .onSuccess { n -> _notices.send(if (n == 1) "Deleted 1 line" else "Deleted $n lines") }
                .onFailure { e -> _notices.send("Nothing was deleted. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") }
        }
    }

    // ---- memory -----------------------------------------------------------------------

    fun summarize() = memory.summarize(chatId)

    fun cancelSummary() = memory.cancel(chatId)

    fun closeMemory() {
        // A finished or failed run has been seen; a running one keeps going in the background.
        if (!memory.isRunning(chatId)) memory.dismiss(chatId)
    }

    fun saveSummary(text: String) {
        viewModelScope.launch {
            _summaryEdit.value = EditStatus(saving = true)
            runCatching { memory.saveEdited(chatId, text.trim()) }
                .onSuccess { _summaryEdit.value = EditStatus() }
                .onFailure { e -> _summaryEdit.value = EditStatus(error = e as? ApiError ?: ApiError.Unknown(e)) }
        }
    }

    fun setReplacesHistory(on: Boolean) {
        memoryPrefs.setReplacesHistory(on)
        if (!on) memoryPrefs.setAutoSummarize(false)
    }

    fun setAutoSummarize(on: Boolean) {
        memoryPrefs.setAutoSummarize(on)
        if (on) memoryPrefs.setReplacesHistory(true)
    }

    private companion object {
        const val KEY_DRAFT = "draft"
        /** How long typing must pause before the draft is written to disk. */
        const val DRAFT_SAVE_MS = 400L
        const val KEY_EDIT_ID = "editId"
        const val KEY_EDIT_TEXT = "editText"
        const val KEY_INTRO = "intro"
        const val NO_EDIT = -1L
    }
}
