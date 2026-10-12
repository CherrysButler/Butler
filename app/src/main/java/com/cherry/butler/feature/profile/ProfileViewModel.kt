package com.cherry.butler.feature.profile

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.PersonaSwap
import com.cherry.butler.core.data.PersonaFormats
import com.cherry.butler.core.data.PersonaTransfer
import com.cherry.butler.core.data.TransferPersona
import com.cherry.butler.ui.components.PersonaExportFormat
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.remote.dto.ProfileCountsDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import com.cherry.butler.core.network.ApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Personas as a file: its name and its text in [format]. One persona is named after itself,
 * several after the day they were exported.
 */
internal fun personaFile(personas: List<TransferPersona>, format: PersonaExportFormat): Pair<String, String> {
    val text = when (format) {
        PersonaExportFormat.Butler -> PersonaFormats.toButler(personas, System.currentTimeMillis())
        PersonaExportFormat.Text -> PersonaFormats.toText(personas)
        PersonaExportFormat.Markdown -> PersonaFormats.toMarkdown(personas)
    }
    val stem = personas.singleOrNull()?.name?.ifBlank { null }?.replace(Regex("[\\/:*?\"<>|]"), "_")?.let { "$it - persona" }
        ?: "Personas - ${java.time.LocalDate.now()}"
    return "$stem.${format.extension}" to text
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val personas: PersonaRepository,
    private val swap: PersonaSwap,
    private val transfer: PersonaTransfer,
) : ViewModel() {

    // ---- export and import -------------------------------------------------------------

    /** A file read and waiting for the user's yes: the personas in it. */
    private val _importing = MutableStateFlow<List<TransferPersona>?>(null)
    val importing: StateFlow<List<TransferPersona>?> = _importing.asStateFlow()
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress.asStateFlow()
    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()

    fun reportTransfer(message: String) { _transferError.value = message }
    fun dismissTransferError() { _transferError.value = null }

    /** [option] as a file's name and text in [format]; null exports everyone, the profile first. */
    suspend fun exportText(option: PersonaOption?, format: PersonaExportFormat): Pair<String, String> =
        personaFile(if (option == null) transfer.exportAll() else listOf(transfer.export(option.id)), format)

    fun readImport(text: String) {
        runCatching { PersonaFormats.parse(text) }
            .onSuccess { _importing.value = it }
            .onFailure { _transferError.value = it.message ?: "That file couldn't be read." }
    }

    fun cancelImport() { _importing.value = null }

    fun confirmImport() {
        val file = _importing.value ?: return
        _importing.value = null
        viewModelScope.launch {
            _importProgress.value = 0 to file.size
            runCatching { transfer.import(file) { done, total -> _importProgress.value = done to total } }
                .onFailure { e ->
                    val made = _importProgress.value?.first ?: 0
                    _transferError.value = "Import stopped after $made of ${file.size}. ${e.userMessage()}"
                }
            _importProgress.value = null
        }
    }

    /** A "Make default" swap, from the ask to the end; null when no pop-up is open. */
    private val _swapFlow = MutableStateFlow<SwapFlow?>(null)
    val swapFlow: StateFlow<SwapFlow?> = _swapFlow.asStateFlow()

    private var swapTarget: String? = null
    private var swapPlan: PersonaSwap.Plan? = null

    fun askMakeDefault(option: PersonaOption) {
        val id = option.id ?: return
        val old = personas.options.value.firstOrNull { it.id == null }
        swapTarget = id
        swapPlan = null
        _swapFlow.value = SwapFlow(newName = option.name, oldName = old?.name ?: "your default")
    }

    fun closeSwap() {
        if (_swapFlow.value?.stage == SwapFlow.Stage.Running) return
        _swapFlow.value = null
        swapPlan = null
    }

    private fun step(i: Int, state: SwapFlow.StepState) {
        _swapFlow.value = _swapFlow.value?.let { f -> f.copy(steps = f.steps.toMutableList().also { it[i] = state }) }
    }

    private fun stage(stage: SwapFlow.Stage, reason: String? = null) {
        _swapFlow.value = _swapFlow.value?.copy(stage = stage, reason = reason)
    }

    fun confirmSwap() {
        val id = swapTarget ?: return
        stage(SwapFlow.Stage.Running)
        viewModelScope.launch {
            try {
                step(0, SwapFlow.StepState.Working)
                val plan = swap.prepare(id).also { swapPlan = it }
                when (val picture = swap.commitPicture(plan)) {
                    PersonaSwap.PictureResult.Done -> step(0, SwapFlow.StepState.Done)
                    PersonaSwap.PictureResult.NoPicture -> step(0, SwapFlow.StepState.Skipped)
                    is PersonaSwap.PictureResult.Refused -> {
                        // Nothing has changed yet: the user decides.
                        step(0, SwapFlow.StepState.Failed)
                        stage(SwapFlow.Stage.PictureRefused, picture.reason)
                        return@launch
                    }
                }
            } catch (e: Throwable) {
                step(0, SwapFlow.StepState.Failed)
                stage(SwapFlow.Stage.Failed, "Nothing was changed. ${e.userMessage()}")
                swapPlan = null
                return@launch
            }
            finish(swapPlan ?: return@launch, withoutPicture = false)
        }
    }

    /** After a refused picture nothing has changed, so reverting is just letting go. */
    fun revertSwap() {
        _swapFlow.value = null
        swapPlan = null
    }

    fun continueWithoutPicture() {
        val plan = swapPlan ?: return
        step(0, SwapFlow.StepState.Skipped)
        stage(SwapFlow.Stage.Running)
        viewModelScope.launch { finish(plan, withoutPicture = true) }
    }

    private suspend fun finish(plan: PersonaSwap.Plan, withoutPicture: Boolean) {
        try {
            step(1, SwapFlow.StepState.Working)
            try {
                swap.swapNames(plan)
            } catch (e: Throwable) {
                // PersonaSwap already put back what it had changed.
                step(1, SwapFlow.StepState.Failed)
                stage(SwapFlow.Stage.Failed, "Everything was put back. ${e.userMessage()}")
                return
            }
            step(1, SwapFlow.StepState.Done)
            step(2, SwapFlow.StepState.Working)
            swap.settle(plan, withoutPicture)
            step(2, SwapFlow.StepState.Done)
            stage(SwapFlow.Stage.Done)
        } finally {
            swapPlan = null
        }
    }

    /** Never exposes `config`: the screen reads name, avatar and about only. */
    val me: StateFlow<ProfileDto?> = profile.profile
    val options: StateFlow<List<PersonaOption>> = personas.options
    val current: StateFlow<PersonaOption?> = personas.current

    // ---- persona groups ----------------------------------------------------------------

    val groups: StateFlow<List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto>> = personas.groups

    private val _groupError = MutableStateFlow<String?>(null)

    /** The last group change that didn't go through, in a line. */
    val groupError: StateFlow<String?> = _groupError.asStateFlow()

    private fun groupWork(failure: String, work: suspend () -> Unit) {
        viewModelScope.launch {
            _groupError.value = null
            runCatching { work() }.onFailure { e ->
                _groupError.value = "$failure ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}"
            }
        }
    }

    /** A new group; with [thenMove], that persona goes straight into it. */
    fun createGroup(name: String, color: String, thenMove: String? = null) = groupWork("Couldn't make the group.") {
        val group = profile.createGroup(name, color)
        thenMove?.let { profile.movePersona(it, group.id) }
    }

    fun updateGroup(id: String, name: String, color: String) = groupWork("Couldn't save the group.") { profile.updateGroup(id, name, color) }

    fun deleteGroup(id: String) = groupWork("Couldn't delete the group.") { profile.deleteGroup(id) }

    fun moveGroup(id: String, by: Int) = groupWork("Couldn't move the group.") { profile.moveGroup(id, by) }

    fun movePersona(personaId: String, groupId: String?) = groupWork("Couldn't move the persona.") { profile.movePersona(personaId, groupId) }

    private val _counts = MutableStateFlow<ProfileCountsDto?>(null)
    val counts: StateFlow<ProfileCountsDto?> = _counts.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    init {
        refresh(force = false)
        // A persona made or deleted in the editor changes the figure over the list.
        viewModelScope.launch {
            profile.personas.map { it.size }.distinctUntilChanged().drop(1).collect {
                runCatching { profile.counts() }.onSuccess { _counts.value = it }
            }
        }
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            val p = runCatching { profile.profile(refresh = force) }
            runCatching { profile.personas(refresh = force) }
            runCatching { profile.groups(refresh = force) }
            runCatching { profile.counts() }.onSuccess { _counts.value = it }
            p.exceptionOrNull()?.let { _error.value = it as? ApiError ?: ApiError.Unknown(it) }
            _refreshing.value = false
        }
    }

    fun choose(option: PersonaOption) = personas.select(option.id)
}
