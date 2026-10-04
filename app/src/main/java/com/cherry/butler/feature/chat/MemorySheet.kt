package com.cherry.butler.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.MemoryPrefs
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.generation.MemoryService
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.FieldBlock
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.feature.settings.rememberSynced
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.userMessage

/**
 * A chat's memory: the summary Janitor folds into every prompt. It can be read and
 * rewritten by hand, or written fresh by the model ("Summarize now"), which streams in
 * here as it is written and is saved to the chat when it finishes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(
    summary: String?,
    run: MemoryService.Run?,
    running: Boolean,
    replacesHistory: Boolean,
    autoSummarize: Boolean,
    savingEdit: Boolean,
    editError: String?,
    onSummarize: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onReplacesHistory: (Boolean) -> Unit,
    onAutoSummarize: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSynced(summary.orEmpty())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlateText(text = "Memory", level = PlateLevel.Name, color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }

            when {
                running -> {
                    Text(
                        "Writing a fresh summary of this chat…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                    StreamingProse(
                        text = run?.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 80.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Spacer(Modifier.weight(1f))
                        KeyButton(label = "Stop", onClick = onCancel)
                    }
                }
                else -> {
                    run?.error?.let { e ->
                        ErrataSlip(
                            title = "Summary not saved",
                            body = e.userMessage(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    FieldBlock(
                        label = if (summary.isNullOrBlank()) "No summary yet" else "Summary",
                        value = draft,
                        onChange = { draft = it },
                        placeholder = "What the character should remember about this chat",
                        singleLine = false,
                        minLines = 6,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        error = editError,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Summarizing uses one generation on your current provider.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.textLow,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        if (draft != summary.orEmpty()) {
                            KeyButton(label = if (savingEdit) "Saving…" else "Save", onClick = { onSave(draft) }, enabled = !savingEdit, primary = true)
                        } else {
                            KeyButton(
                                label = if (summary.isNullOrBlank()) "Summarize now" else "Summarize again",
                                onClick = onSummarize,
                                primary = true,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Summary replaces older messages",
                subtitle = "For every chat",
                checked = replacesHistory,
                onChange = onReplacesHistory,
            )
            SwitchRow(
                title = "Summarize automatically",
                subtitle = "Every ${MemoryPrefs.AUTO_EVERY} messages, for every chat",
                checked = autoSummarize,
                onChange = onAutoSummarize,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
