package com.cherry.butler.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.core.lorebook.LorebookScope
import com.cherry.butler.ui.components.PersonaPickerSheet

/**
 * The open chat's pages in the PC chat panel: the very sheets the phone slides up (persona,
 * model, memory, lore, background), laid out in place, on the same state and actions. Picking
 * something keeps the page open; there is nothing to dismiss.
 */
@Composable
internal fun ChatPanelPages(
    tab: ChatPanelTab,
    viewModel: ChatViewModel,
    onAllModelSettings: () -> Unit,
    onCustomize: () -> Unit,
    onEditPersona: (String) -> Unit,
    onEditLorebook: (String?, String?) -> Unit,
    onOpenMemoryThought: () -> Unit,
) {
    when (tab) {
        ChatPanelTab.Character -> Unit
        ChatPanelTab.Persona -> {
            val options by viewModel.personaOptions.collectAsStateWithLifecycle()
            val groups by viewModel.personaGroups.collectAsStateWithLifecycle()
            val active by viewModel.activePersona.collectAsStateWithLifecycle()
            PersonaPickerSheet(
                options = options,
                groups = groups,
                selected = active,
                onPick = { viewModel.pickPersona(it) },
                onDismiss = {},
                onEdit = { option -> onEditPersona(option.id ?: com.cherry.butler.feature.profile.PersonaEditorViewModel.DEFAULT) },
            )
        }
        ChatPanelTab.Model -> {
            LaunchedEffect(Unit) { viewModel.openModels() }
            val ai by viewModel.aiSettingsState.collectAsStateWithLifecycle()
            val percent by viewModel.contextPercent.collectAsStateWithLifecycle()
            val busy by viewModel.modelBusy.collectAsStateWithLifecycle()
            val error by viewModel.modelError.collectAsStateWithLifecycle()
            val allowance by viewModel.jllmAllowance.collectAsStateWithLifecycle()
            ModelSheet(
                settings = ai,
                percent = percent,
                presetFor = viewModel::presetFor,
                busy = busy,
                error = error,
                onSelectProxy = viewModel::selectProxy,
                onUseJllm = viewModel::useJllm,
                onSaveModel = viewModel::saveModel,
                onAllSettings = onAllModelSettings,
                onDismiss = {},
                jllmAllowance = allowance,
                models = viewModel.models.collectAsStateWithLifecycle().value,
                onLoadModels = viewModel::loadModels,
            )
        }
        ChatPanelTab.Memory -> {
            val chat by viewModel.chat.collectAsStateWithLifecycle()
            val run by viewModel.memoryRun.collectAsStateWithLifecycle()
            val edit by viewModel.summaryEdit.collectAsStateWithLifecycle()
            val compact by viewModel.compactMode.collectAsStateWithLifecycle()
            val thought by viewModel.memoryThought.collectAsStateWithLifecycle()
            // Leaving the page is the sheet's closing: an edit left open is let go.
            DisposableEffect(Unit) { onDispose { viewModel.closeMemory() } }
            MemorySheet(
                summary = chat?.summary,
                run = run,
                running = run != null && !run!!.done && run!!.error == null,
                compactMode = compact,
                thought = thought,
                savingEdit = edit.saving,
                editError = edit.error?.let { "Not saved. ${it.userMessage()}" },
                onSummarize = viewModel::summarize,
                onCancel = viewModel::cancelSummary,
                onSave = viewModel::saveSummary,
                onCompact = viewModel::setCompact,
                onOpenThought = onOpenMemoryThought,
                onDismiss = {},
            )
        }
        ChatPanelTab.Lore -> {
            val chat by viewModel.chat.collectAsStateWithLifecycle()
            val active by viewModel.activePersona.collectAsStateWithLifecycle()
            val scope = LorebookScope.chat(viewModel.chatId)
            val personaId = if (active != null) active?.id else chat?.personaId
            com.cherry.butler.feature.lorebook.LorebooksSheet(
                scope = scope,
                inheritedPersonaScope = LorebookScope.persona(personaId, chat?.userId),
                onDismiss = {},
                onEdit = { id -> onEditLorebook(id, scope) },
            )
        }
        ChatPanelTab.Look -> {
            val richOn by viewModel.richOn.collectAsStateWithLifecycle()
            PageLink(icon = Icons.Rounded.TextFields, label = "Customize text", onClick = onCustomize)
            PageSwitch(icon = Icons.Rounded.FormatBold, label = "Rich typing", on = richOn, onChange = { viewModel.setRich(it) })
            com.cherry.butler.feature.settings.BackgroundsSheet(chatId = viewModel.chatId, onDismiss = {})
        }
        ChatPanelTab.Context -> ContextPage(viewModel)
    }
}

/** A switch at the top of a panel page, in the page links' shape. */
@Composable
private fun PageSwitch(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onChange(!on) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange)
    }
}

/** A row that opens a page of its own, at the top of a panel page. */
@Composable
private fun PageLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(20.dp))
    }
}
