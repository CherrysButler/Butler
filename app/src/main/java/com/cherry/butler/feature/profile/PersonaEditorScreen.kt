package com.cherry.butler.feature.profile

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.PersonaDraft
import com.cherry.butler.core.data.PersonaEditing
import com.cherry.butler.core.data.PersonaRepository
import com.cherry.butler.core.data.ProfileRepository
import com.cherry.butler.core.data.PronounSet
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.network.ApiError
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.settings.EditorMenuItem
import com.cherry.butler.feature.settings.EditorScaffold
import com.cherry.butler.feature.settings.FieldBlock
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaForm(
    val name: String = "",
    val appearance: String = "",
    val aboutMe: String = "",
    /** Null: a saved set Butler has no button for, kept as it is unless another is picked. */
    val pronouns: PronounSet? = PronounSet.None,
    /** What is saved now. */
    val pictureUrl: String? = null,
    /** Chosen on the phone, not yet uploaded. */
    val newPicture: Uri? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * One persona, or the profile (the default persona), or a new one. The route argument is
 * the persona id, [DEFAULT] for the profile, or [NEW].
 */
@HiltViewModel
class PersonaEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editing: PersonaEditing,
    private val profiles: ProfileRepository,
    private val personas: PersonaRepository,
    private val writer: com.cherry.butler.core.generation.DescriptionWriter,
) : ViewModel() {

    /** The description writer (the ✦): which presets can write, and the run under way. */
    data class Enhance(
        val presets: List<com.cherry.butler.core.data.ProxyConfig>? = null,
        val running: Boolean = false,
        /** What the user had, for Undo; null when there's nothing to undo. */
        val original: String? = null,
        val error: String? = null,
    )

    private val _enhance = MutableStateFlow(Enhance())
    val enhance: StateFlow<Enhance> = _enhance.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null

    val lastPreset: String? get() = writer.lastPreset

    fun loadPresets() {
        viewModelScope.launch {
            val list = runCatching { writer.presets() }.getOrDefault(emptyList())
            _enhance.update { it.copy(presets = list, error = null) }
        }
    }

    fun startEnhance(preset: com.cherry.butler.core.data.ProxyConfig) {
        val f = _form.value
        if (_enhance.value.running) return
        writer.lastPreset = preset.id
        val original = f.appearance
        _enhance.update { it.copy(running = true, original = original, error = null) }
        job = viewModelScope.launch {
            val text = StringBuilder()
            val result = runCatching {
                writer.enhance(original, f.name, f.pronouns?.takeIf { it != PronounSet.None }?.label, preset).collect { piece ->
                    text.append(piece)
                    _form.update { it.copy(appearance = text.toString().trimStart()) }
                }
            }
            val written = text.toString().trim()
            _form.update { it.copy(appearance = written.ifEmpty { original }) }
            _enhance.update {
                it.copy(
                    running = false,
                    original = original.takeIf { written.isNotEmpty() },
                    error = when {
                        result.exceptionOrNull() is kotlinx.coroutines.CancellationException -> null
                        result.isFailure -> (result.exceptionOrNull() as? ApiError ?: ApiError.Unknown(result.exceptionOrNull()!!)).userMessage()
                        written.isEmpty() -> "Nothing came back. Try again."
                        else -> null
                    },
                )
            }
        }
    }

    fun stopEnhance() { job?.cancel() }

    fun undoEnhance() {
        val original = _enhance.value.original ?: return
        _form.update { it.copy(appearance = original) }
        _enhance.update { it.copy(original = null) }
    }

    /** Typing in the box after an enhance makes the result the user's own: no more Undo. */
    fun keepEnhance() = _enhance.update { it.copy(original = null, error = null) }
    private val arg: String = savedStateHandle.get<String>(ARG) ?: NEW
    val isNew: Boolean = arg == NEW
    val isDefault: Boolean = arg == DEFAULT
    private val personaId: String? = arg.takeUnless { isNew || isDefault }

    private val _form = MutableStateFlow(PersonaForm(loaded = isNew))
    val form: StateFlow<PersonaForm> = _form.asStateFlow()

    // What Butler already holds, at once: a network read here would land after the user has
    // started typing and wipe it. Saving re-reads before it writes.
    init {
        if (!isNew) viewModelScope.launch {
            runCatching {
                if (isDefault) {
                    val p = profiles.profile()
                    PersonaForm(
                        name = p.name,
                        appearance = p.appearance.orEmpty(),
                        aboutMe = p.aboutMe.orEmpty(),
                        pictureUrl = personas.options.value.firstOrNull { it.id == null }?.avatarUrl ?: p.avatar,
                        loaded = true,
                    )
                } else {
                    val p = profiles.personas().firstOrNull { it.id == personaId } ?: profiles.personas(refresh = true).first { it.id == personaId }
                    PersonaForm(
                        name = p.name,
                        appearance = p.appearance.orEmpty(),
                        pronouns = PronounSet.of(p.pronouns),
                        pictureUrl = JanitorConfig.personaAvatarUrl(p.avatar),
                        loaded = true,
                    )
                }
            }.onSuccess { f -> _form.value = f }
                .onFailure { e -> _form.update { it.copy(error = (e as? ApiError ?: ApiError.Unknown(e)).userMessage()) } }
        }
    }

    fun update(change: (PersonaForm) -> PersonaForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.saving || f.name.isBlank()) return
        val draft = PersonaDraft(
            name = f.name,
            appearance = f.appearance,
            pronouns = f.pronouns,
            picture = f.newPicture,
            aboutMe = f.aboutMe.takeIf { isDefault },
        )
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching {
                when {
                    isNew -> editing.create(draft)
                    isDefault -> editing.updateProfile(draft)
                    else -> editing.update(personaId!!, draft)
                }
            }.onSuccess { onDone() }
                .onFailure { e ->
                    val message = (e as? ApiError ?: ApiError.Unknown(e)).userMessage()
                    _form.update { it.copy(saving = false, error = if (isDefault && f.newPicture != null) "Janitor didn't take the picture. $message" else "Not saved. $message") }
                }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = personaId ?: return
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            runCatching { editing.delete(id) }
                .onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = "Not deleted. ${(e as? ApiError ?: ApiError.Unknown(e)).userMessage()}") } }
        }
    }

    companion object {
        const val ARG = "personaId"
        const val NEW = "new"
        const val DEFAULT = "default"
    }
}

@Composable
fun PersonaEditorScreen(onBack: () -> Unit, viewModel: PersonaEditorViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.update { it.copy(newPicture = uri) }
    }

    if (confirmDelete) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete persona",
            body = "\"${form.name}\" will be removed from your Janitor account. This can't be undone.",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }

    EditorScaffold(
        title = when {
            viewModel.isNew -> "New persona"
            viewModel.isDefault -> "Your profile"
            else -> "Edit persona"
        },
        onBack = onBack,
        saving = form.saving,
        canSave = form.loaded && form.name.isNotBlank(),
        onSave = { viewModel.save(onBack) },
        error = form.error,
        menu = if (viewModel.isNew || viewModel.isDefault) null else { close ->
            EditorMenuItem("Delete", Icons.Outlined.Delete, danger = true, enabled = !form.saving) { close(); confirmDelete = true }
        },
    ) {
        // The picture, and a word that says it can be changed.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = form.newPicture?.toString() ?: form.pictureUrl,
                name = form.name.ifBlank { "?" },
                size = 88.dp,
                initialStyle = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(enabled = !form.saving) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (form.pictureUrl == null && form.newPicture == null) "Add a picture" else "Change picture",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(Pill)
                    .clickable(enabled = !form.saving) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        FieldBlock("Name", form.name, { v -> viewModel.update { it.copy(name = v) } })
        if (!viewModel.isDefault) {
            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Pronouns",
                    style = MaterialTheme.typography.labelMedium,
                    color = ButlerTheme.colors.textMed,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val sets = PronounSet.entries
                SegmentedChoice(
                    options = sets.map { it.label },
                    selected = form.pronouns?.let(sets::indexOf) ?: -1,
                    onSelect = { i -> viewModel.update { it.copy(pronouns = sets[i]) } },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        val enhance by viewModel.enhance.collectAsStateWithLifecycle()
        FieldBlock(
            label = "Who you are in chats",
            value = form.appearance,
            onChange = { v ->
                if (enhance.running) return@FieldBlock
                viewModel.update { it.copy(appearance = v) }
                if (enhance.original != null) viewModel.keepEnhance()
            },
            singleLine = false,
            minLines = 6,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        EnhanceBar(
            enhance = enhance,
            words = com.cherry.butler.core.generation.DescriptionWriter.words(form.appearance),
            lastPreset = viewModel.lastPreset,
            onOpen = viewModel::loadPresets,
            onPick = viewModel::startEnhance,
            onStop = viewModel::stopEnhance,
            onUndo = viewModel::undoEnhance,
        )
        if (viewModel.isDefault) {
            FieldBlock(
                label = "About me, on your public profile",
                value = form.aboutMe,
                onChange = { v -> viewModel.update { it.copy(aboutMe = v) } },
                singleLine = false,
                minLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The description writer, under the persona's description (a Butler special): once there are
 * enough words to work from, ✦ Enhance asks which preset writes, then streams the richer
 * version into the box. Stop while it writes; Undo after, until the user types again.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EnhanceBar(
    enhance: PersonaEditorViewModel.Enhance,
    words: Int,
    lastPreset: String?,
    onOpen: () -> Unit,
    onPick: (com.cherry.butler.core.data.ProxyConfig) -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val enough = words >= com.cherry.butler.core.generation.DescriptionWriter.MIN_WORDS
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = enhance.error ?: if (enhance.running) "Writing\u2026" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (enhance.error != null) ButlerTheme.colors.danger else ButlerTheme.colors.textLow,
            modifier = Modifier.weight(1f),
        )
        when {
            enhance.running -> TextKey("Stop", onClick = onStop)
            enhance.original != null -> TextKey("Undo", onClick = onUndo)
            enough -> TextKey("Enhance", icon = Icons.Rounded.AutoAwesome, onClick = { onOpen(); picking = true })
        }
    }
    if (picking) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { picking = false },
            shape = com.cherry.butler.core.design.SheetShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrimColor = com.cherry.butler.ui.components.SheetScrim,
            dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
        ) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Enhance with",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                val presets = enhance.presets
                when {
                    presets == null -> Text("\u2026", color = ButlerTheme.colors.textLow, modifier = Modifier.padding(20.dp))
                    presets.isEmpty() -> Text(
                        "Add a proxy preset in Settings \u203A Model first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    else -> presets.sortedByDescending { it.id == lastPreset }.forEach { p ->
                        com.cherry.butler.feature.settings.ChoiceRow(
                            title = p.name.ifBlank { p.model },
                            subtitle = p.model.takeIf { p.name.isNotBlank() },
                            selected = p.id == lastPreset,
                            onClick = { picking = false; onPick(p) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextKey(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
