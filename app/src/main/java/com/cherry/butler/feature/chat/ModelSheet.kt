package com.cherry.butler.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.AiSettings
import com.cherry.butler.core.data.OpenRouterOptions
import com.cherry.butler.core.data.Provider
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.ChoiceRow
import com.cherry.butler.feature.settings.FieldBlock
import com.cherry.butler.feature.settings.LinkRow
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim

/** The colour a context fill reads in: calm, then a warning, then trouble. */
@Composable
private fun gaugeColor(percent: Int): Color = when {
    percent >= 95 -> ButlerTheme.colors.danger
    percent >= 80 -> ButlerTheme.colors.warn
    else -> ButlerTheme.colors.onAccentSoft
}

/**
 * The top bar's model chip: which model answers, and a small ring for how full the
 * context was on the last reply. Tapping it opens [ModelSheet].
 */
@Composable
fun ProviderChip(label: String, percent: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = 150.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (percent != null) 7.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.onAccentSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (percent != null) {
            Spacer(Modifier.width(6.dp))
            ContextRing(percent, Modifier.size(13.dp))
        }
    }
}

@Composable
private fun ContextRing(percent: Int, modifier: Modifier) {
    val track = ButlerTheme.colors.outlineFaint
    val fill by animateColorAsState(gaugeColor(percent), label = "ring")
    Canvas(modifier) {
        val w = 2.dp.toPx()
        val inset = w / 2
        val box = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
        drawArc(fill, -90f, 360f * percent / 100f, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/**
 * Switch what answers without leaving the chat: the proxies, JLLM, and the selected
 * proxy's model, plus how full this chat's context is. Takes effect on the next reply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    settings: AiSettings?,
    percent: Int?,
    presetFor: (String) -> String,
    busy: Boolean,
    error: String?,
    onSelectProxy: (String) -> Unit,
    onUseJllm: () -> Unit,
    onSaveModel: (proxyId: String, model: String) -> Unit,
    onAllSettings: () -> Unit,
    onDismiss: () -> Unit,
    /** (remaining, total) of JLLM's allowance, when Janitor gives the account one. */
    jllmAllowance: Pair<Int, Int>? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Text(
                "Model",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (percent != null) ContextBar(percent)

            val proxyOn = settings?.provider == Provider.Proxy
            settings?.proxies?.forEach { proxy ->
                val preset = presetFor(proxy.id)
                ChoiceRow(
                    title = proxy.name.ifBlank { "Proxy" },
                    subtitle = if (preset.isNotBlank()) "$preset · ${proxy.model}" else proxy.model.ifBlank { proxy.apiUrl },
                    selected = proxyOn && settings.selectedProxyId == proxy.id,
                    onClick = { onSelectProxy(proxy.id) },
                    busy = busy,
                )
            }
            ChoiceRow(
                title = "JLLM",
                subtitle = jllmAllowance?.let { (left, total) -> "Janitor's own model · $left of $total left" } ?: "Janitor's own model",
                selected = settings?.provider == Provider.Janitor,
                onClick = onUseJllm,
                busy = busy,
            )

            val selected = settings?.selectedProxy?.takeIf { proxyOn }
            if (selected != null) {
                var model by remember(selected.id) { mutableStateOf(selected.model) }
                LaunchedEffect(selected.model) { model = selected.model }
                Spacer(Modifier.height(8.dp))
                FieldBlock("Model for ${selected.name}", model, { model = it }, placeholder = "provider/model-name")
                if (OpenRouterOptions.isOpenRouter(selected.apiUrl) && presetFor(selected.id).isNotBlank()) {
                    Text(
                        "A preset is set for this proxy, so OpenRouter uses ${presetFor(selected.id)} instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (model.trim() != selected.model && model.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                        KeyButton(
                            label = if (busy) "Saving…" else "Use this model",
                            onClick = { onSaveModel(selected.id, model.trim()) },
                            enabled = !busy,
                            primary = true,
                        )
                    }
                }
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            LinkRow(title = "All model settings", subtitle = "Samplers, prompts, proxies", onClick = onAllSettings)
        }
    }
}

/** "42% of the context in use", as a line the width of the sheet. */
@Composable
private fun ContextBar(percent: Int) {
    val fill = gaugeColor(percent)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Context",
                style = MaterialTheme.typography.labelMedium,
                color = ButlerTheme.colors.textMed,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$percent% full",
                style = MaterialTheme.typography.labelMedium,
                color = if (percent >= 80) fill else ButlerTheme.colors.textMed,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(Pill).background(ButlerTheme.colors.surfaceHigh)) {
            Box(modifier = Modifier.fillMaxWidth(percent / 100f).height(4.dp).clip(Pill).background(fill))
        }
        if (percent >= 80) {
            Text(
                "Near the limit, older messages may stop reaching the model. A memory summary keeps them in play.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
