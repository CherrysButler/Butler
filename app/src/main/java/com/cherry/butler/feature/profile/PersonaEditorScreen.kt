package com.cherry.butler.feature.profile

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
) : ViewModel() {
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
        FieldBlock(
            label = "Who you are in chats",
            value = form.appearance,
            onChange = { v -> viewModel.update { it.copy(appearance = v) } },
            singleLine = false,
            minLines = 6,
            textStyle = MaterialTheme.typography.bodyMedium,
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
