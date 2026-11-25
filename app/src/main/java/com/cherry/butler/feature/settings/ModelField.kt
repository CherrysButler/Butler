package com.cherry.butler.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.CatalogModel
import com.cherry.butler.core.data.ModelCatalog
import com.cherry.butler.core.data.ModelVariant
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.hairlineFrame

/**
 * A proxy's model, picked instead of typed. Closed, it is a row in the Prompt row's shape: the
 * model's name with its context and price, and the chevron of a picker. Opened, it becomes a
 * search over the router's list, in place: no sheet over a sheet. Filters narrow it (free,
 * roleplay, takes pictures, thinks), the arrow keys and Enter work it from a keyboard, and an
 * id the list does not have can still be used as typed. [catalog] null is a proxy with no list
 * to pick from: the plain field, as before.
 */
@Composable
fun ModelField(
    label: String,
    value: String,
    /** A model picked from the list, or an id typed and used as it is. */
    onChoose: (String) -> Unit,
    catalog: ModelCatalog.State?,
    /** Asks for the list: when the picker opens, and again (forced) from its retry. */
    onLoad: (force: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Typing in the plain field ([catalog] null). */
    onType: (String) -> Unit = onChoose,
    /** Opened from the start, on a search: for showing the picker's states (its render check). */
    startOpen: Boolean = false,
    startQuery: String = "",
) {
    if (catalog == null) {
        FieldBlock(label, value, onType, placeholder = "provider/model-name")
        return
    }
    var open by remember { mutableStateOf(startOpen) }
    var query by remember { mutableStateOf(startQuery) }
    var filters by remember { mutableStateOf(emptySet<ModelFilter>()) }
    // The model, and the routing shortcut on its end (":nitro", ":floor"): the list knows the
    // model by its id alone.
    val variant = ModelVariant.of(value)
    val base = ModelVariant.base(value)
    val current = remember(catalog.models, base) { catalog.models.firstOrNull { it.id == base } }
    LaunchedEffect(open) { if (open) onLoad(false) else query = "" }
    // A list on show before the first frame (startOpen) has not animated in: nothing to wait for.

    val found = remember(catalog.models, query, filters, base) {
        val kept = catalog.models.filter { m -> filters.all { it.keeps(m) } }
        val hits = ModelCatalog.search(kept, query)
        // With nothing typed the model in use leads the list, so it is seen to be the one.
        if (query.isBlank()) hits.sortedByDescending { it.id == base } else hits
    }
    // An id typed that the list does not have: offered as it is, at the list's foot.
    val typed = query.trim().takeIf { q -> q.isNotEmpty() && catalog.models.none { it.id.equals(q, ignoreCase = true) } }
    val rows = found.size + if (typed != null) 1 else 0
    var lit by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, filters) { lit = 0 }
    val list = rememberLazyListState()
    LaunchedEffect(lit) { if (open && rows > 0) list.animateScrollToItem(lit.coerceIn(0, rows - 1)) }

    fun choose(id: String) {
        open = false
        // Another model keeps the shortcut that was on the last one, where it can carry one;
        // an id typed with a shortcut of its own is used as typed.
        val full = if (ModelVariant.of(id) == ModelVariant.Standard && ModelVariant.takes(id)) id + variant.suffix else id
        if (full != value) onChoose(full)
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
        Spacer(Modifier.height(6.dp))
        if (!open) {
            ClosedRow(value = value, model = current, onOpen = { open = true })
            // How its provider is picked: OpenRouter's own balance, the fastest, or the cheapest.
            if (ModelVariant.takes(base)) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    ModelVariant.entries.forEach { v ->
                        val on = v == variant
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else ButlerTheme.colors.surfaceHigh)
                                .clickable(role = Role.RadioButton, onClickLabel = v.label) { if (!on) onChoose(ModelVariant.with(value, v)) }
                                .heightIn(min = 36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                v.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textMed,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        } else {
            SearchRow(
                query = query,
                onQuery = { query = it },
                onClose = { open = false },
                onMove = { step -> if (rows > 0) lit = (lit + step).coerceIn(0, rows - 1) },
                onEnter = {
                    when {
                        lit < found.size -> choose(found[lit].id)
                        typed != null -> choose(typed)
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(300, easing = Opening), expandFrom = Alignment.Top) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(200, easing = Opening), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            Column {
                FilterRow(
                    filters = filters,
                    onToggle = { f -> filters = if (f in filters) filters - f else filters + f },
                    count = found.size.takeIf { catalog.models.isNotEmpty() },
                )
                when {
                    catalog.models.isEmpty() && catalog.loading -> Waiting()
                    catalog.models.isEmpty() && catalog.failed -> Failed(onRetry = { onLoad(true) }, typed = typed, onUse = ::choose)
                    else -> LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 392.dp)) {
                        itemsIndexed(found, key = { _, m -> m.id }) { i, m ->
                            ModelRow(model = m, selected = m.id == base, lit = i == lit, onClick = { choose(m.id) })
                        }
                        if (typed != null) item(key = "typed") {
                            UseTyped(id = typed, lit = lit == found.size, onClick = { choose(typed) })
                        }
                        if (found.isEmpty() && typed == null) item(key = "none") { Nothing() }
                    }
                }
            }
        }
    }
}

private val Opening = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** What narrows the list. */
private enum class ModelFilter(val label: String, val keeps: (CatalogModel) -> Boolean) {
    Free("Free", { it.free }),
    Roleplay("Roleplay", { it.roleplay }),
    Pictures("Pictures", { it.images }),
    Thinking("Thinking", { it.reasoning }),
}

/** Prices and sizes in figures that line up down the list. */
private val Figures: TextStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")

/** The model in use, as a row that opens the picker. */
@Composable
private fun ClosedRow(value: String, model: CatalogModel?, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .clickable(role = Role.Button, onClickLabel = "Choose a model", onClick = onOpen)
            .heightIn(min = 50.dp)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = model?.name ?: value.ifBlank { "None" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (model != null) {
                // The id as it is sent, shortcut and all.
                Text(value, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (model != null) {
            Spacer(Modifier.width(10.dp))
            Facts(model, horizontal = Alignment.End)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = ButlerTheme.colors.textLow)
    }
}

/** The search, where the row was: typing narrows the list, the arrows and Enter pick from it. */
@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, onMove: (Int) -> Unit, onEnter: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineFrame(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .heightIn(min = 50.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = { onQuery(it.filterNot { c -> c == '\n' || c == '\r' }) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEnter() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = "Search models" }
                .onPreviewKeyEvent { e ->
                    val down = e.type == KeyEventType.KeyDown
                    when (e.key) {
                        Key.DirectionDown -> { if (down) onMove(1); true }
                        Key.DirectionUp -> { if (down) onMove(-1); true }
                        Key.Enter, Key.NumPadEnter -> { if (down) onEnter(); true }
                        Key.Escape -> { if (down) onClose(); true }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search models", style = MaterialTheme.typography.bodyLarge, color = ButlerTheme.colors.textLow)
                    inner()
                }
            },
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClickLabel = "Close the list", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close the list", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        }
    }
}

/** The filters, and how many models are left. */
@Composable
private fun FilterRow(filters: Set<ModelFilter>, onToggle: (ModelFilter) -> Unit, count: Int?) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModelFilter.entries.forEach { f ->
                val on = f in filters
                Text(
                    text = f.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) ButlerTheme.colors.onAccentSoft else ButlerTheme.colors.textMed,
                    modifier = Modifier
                        .clip(Pill)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else ButlerTheme.colors.surfaceHigh)
                        .clickable(role = Role.Checkbox) { onToggle(f) }
                        .semantics { stateDescription = if (on) "On" else "Off" }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(count.toString(), style = Figures, color = ButlerTheme.colors.textLow)
        }
    }
}

/** One model: its name over its maker and id, with what it holds and costs at the end. */
@Composable
private fun ModelRow(model: CatalogModel, selected: Boolean, lit: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (lit) ButlerTheme.colors.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "In use" else "" }
            .heightIn(min = 54.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp)) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${model.maker} · ${model.id}",
                style = MaterialTheme.typography.labelSmall,
                color = ButlerTheme.colors.textLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Facts(model, horizontal = Alignment.End)
    }
}

/** What a model costs to read and to write, a million tokens each, over how much it holds. */
@Composable
private fun Facts(model: CatalogModel, horizontal: Alignment.Horizontal) {
    Column(horizontalAlignment = horizontal) {
        val price = priceOf(model)
        Text(
            text = price,
            style = Figures,
            color = if (model.free) ButlerTheme.colors.success else ButlerTheme.colors.textMed,
            maxLines = 1,
        )
        if (model.context > 0) Text(sizeOf(model.context), style = Figures.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = ButlerTheme.colors.textLow, maxLines = 1)
    }
}

/** An id the list does not have, offered as typed. */
@Composable
private fun UseTyped(id: String, lit: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (lit) ButlerTheme.colors.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(22.dp))
        Text(
            text = "Use “$id”",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The filters leave nothing, and nothing is typed to use instead. */
@Composable
private fun Nothing() {
    Text(
        "No model fits these filters.",
        style = MaterialTheme.typography.bodyMedium,
        color = ButlerTheme.colors.textMed,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
    )
}

/** The list on its way, the first time: rows in the shape of what is coming. */
@Composable
private fun Waiting() {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading models" }) {
        repeat(5) { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(0.42f + 0.09f * (i % 3)).height(11.dp).clip(Pill).background(ButlerTheme.colors.surfaceHigh))
                    Box(Modifier.fillMaxWidth(0.62f).height(8.dp).clip(Pill).background(ButlerTheme.colors.surfaceHigh))
                }
                Box(Modifier.width(54.dp).height(11.dp).clip(Pill).background(ButlerTheme.colors.surfaceHigh))
            }
        }
    }
}

/** The list did not come: what happened, the way to try again, and the typed id still usable. */
@Composable
private fun Failed(onRetry: () -> Unit, typed: String?, onUse: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
        Text("OpenRouter's list didn't load.", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(label = "Try again", onClick = onRetry, primary = true)
            if (typed != null) KeyButton(label = "Use “$typed”", onClick = { onUse(typed) })
        }
    }
}

/** "$3 / $15" to read and to write a million tokens; "Free"; nothing when the router does not say. */
internal fun priceOf(model: CatalogModel): String {
    val i = model.priceIn
    val o = model.priceOut
    return when {
        model.free -> "Free"
        i == null || o == null -> ""
        else -> "${dollars(i)} / ${dollars(o)}"
    }
}

/** $0.075, $0.15, $3, $12.50: as short as says it. */
internal fun dollars(amount: Double): String {
    val text = when {
        amount == 0.0 -> "0"
        amount < 0.1 -> String.format(java.util.Locale.US, "%.3f", amount)
        else -> String.format(java.util.Locale.US, "%.2f", amount)
    }
    val trimmed = if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    // A price with cents reads as money with both of them: 12.50, 0.30.
    val money = if (trimmed.contains('.') && trimmed.substringAfter('.').length == 1) trimmed + "0" else trimmed
    return "$$money"
}

/** 8k, 128k, 1M, 1.05M: how many tokens a model holds. */
internal fun sizeOf(tokens: Int): String = when {
    tokens >= 1_000_000 -> {
        val m = tokens / 1_000_000.0
        val text = String.format(java.util.Locale.US, "%.2f", m).trimEnd('0').trimEnd('.')
        "${text}M"
    }
    tokens >= 1_000 -> "${Math.round(tokens / 1_000.0)}k"
    else -> tokens.toString()
}
