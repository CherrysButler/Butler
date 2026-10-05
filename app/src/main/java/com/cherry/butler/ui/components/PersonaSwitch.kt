package com.cherry.butler.ui.components

import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.design.ButlerTheme

/**
 * "As Rain" with the persona's thumb: the switch that says who a new chat starts as. Tap
 * it to choose someone else. The name is in Butler red, the same red the name takes when
 * it fills `{{user}}` in the story.
 */
@Composable
fun PersonaSwitch(current: PersonaOption?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = current?.avatarUrl, name = current?.name.orEmpty(), size = 32.dp, initialStyle = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(10.dp))
        Text("As ", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
        Text(
            text = current?.name ?: "…",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change persona", tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
    }
}

/**
 * Every persona the user can play as. Under the title, a row of groups (All first) narrows
 * the list to one group; with many personas a search box finds one by name across all of
 * them. The profile always leads "All". The chosen one is checked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaPickerSheet(
    options: List<PersonaOption>,
    selected: PersonaOption?,
    onPick: (PersonaOption) -> Unit,
    onDismiss: () -> Unit,
    groups: List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto> = emptyList(),
) {
    var query by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    // Opens on the chosen persona's group, so it is in view.
    var shown by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(selected?.groupId?.takeIf { id -> groups.any { it.id == id } })
    }
    val list = when {
        query.isNotBlank() -> options.filter { it.name.contains(query.trim(), ignoreCase = true) }
        shown == null -> options
        else -> options.filter { it.id != null && it.groupId == shown }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Play as",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (options.size > SEARCH_FROM) {
                SearchBox(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Find a persona",
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
            if (groups.isNotEmpty() && query.isBlank()) {
                GroupRow(
                    groups = groups,
                    total = options.size,
                    countOf = { id -> options.count { it.id != null && it.groupId == id } },
                    selected = shown,
                    onSelect = { shown = it },
                    modifier = Modifier.padding(bottom = 4.dp),
                    edge = 20.dp,
                )
            }
            LazyColumn(modifier = Modifier.padding(bottom = 8.dp)) {
                items(list, key = { it.id ?: "profile" }) { option -> PersonaPickRow(option, option.id == selected?.id) { onPick(option) } }
                if (list.isEmpty()) item("none") {
                    Text(
                        if (query.isNotBlank()) "No persona called that." else "No one in this group yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

/** Past this many personas the picker offers a search box. */
private const val SEARCH_FROM = 8

/** A persona group's colour from its hex string, or null when it has none or it is malformed. */
fun groupColor(hex: String?): androidx.compose.ui.graphics.Color? = hex?.removePrefix("#")
    ?.takeIf { it.length == 6 }
    ?.toLongOrNull(16)
    ?.let { androidx.compose.ui.graphics.Color(0xFF000000 or it) }

/**
 * A persona group's colour, as a small rounded square: shapes stay blocks (only a radio dot
 * is a circle), and the colour is the user's own, so it marks the group without being an accent.
 */
@Composable
fun GroupMark(color: androidx.compose.ui.graphics.Color?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(10.dp)
            .background(color ?: ButlerTheme.colors.textLow, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
    )
}

/**
 * Persona groups side by side, scrolling sideways: "All" first, then each group with its mark,
 * name and count, then [onAdd] as "New" when given. The lit one fills with the accent tint.
 * With [menu], tapping the lit group again opens that group's menu (rename, move, delete).
 */
@Composable
fun GroupRow(
    groups: List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto>,
    total: Int,
    countOf: (String) -> Int,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    edge: androidx.compose.ui.unit.Dp = 12.dp,
    onAdd: (() -> Unit)? = null,
    menu: (@Composable (group: com.cherry.butler.core.data.remote.dto.PersonaGroupDto, open: Boolean, close: () -> Unit) -> Unit)? = null,
) {
    var menuFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = edge, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        item("all") { GroupChip(label = "All", count = total, mark = null, lit = selected == null, onClick = { onSelect(null) }) }
        items(groups, key = { it.id }) { group ->
            val lit = selected == group.id
            androidx.compose.foundation.layout.Box {
                GroupChip(
                    label = group.name,
                    count = countOf(group.id),
                    mark = groupColor(group.color) ?: ButlerTheme.colors.textLow,
                    lit = lit,
                    more = lit && menu != null,
                    onClick = { if (lit && menu != null) menuFor = group.id else onSelect(group.id) },
                )
                menu?.invoke(group, menuFor == group.id) { menuFor = null }
            }
        }
        if (onAdd != null) item("add") {
            GroupChip(label = "New group", count = null, mark = null, lit = false, add = true, onClick = onAdd)
        }
    }
}

/** One group in [GroupRow]: a small-cornered block, never a pill, 40 high with a 48 touch row. */
@Composable
private fun GroupChip(
    label: String,
    count: Int?,
    mark: androidx.compose.ui.graphics.Color?,
    lit: Boolean,
    onClick: () -> Unit,
    more: Boolean = false,
    add: Boolean = false,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (lit) MaterialTheme.colorScheme.primaryContainer else ButlerTheme.colors.surfaceHigh)
            .border(1.dp, if (lit) MaterialTheme.colorScheme.primary else ButlerTheme.colors.outlineFaint, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (add) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        }
        mark?.let { GroupMark(it, Modifier.padding(end = 8.dp)) }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (lit) FontWeight.Bold else FontWeight.Medium,
            color = when {
                lit -> ButlerTheme.colors.onAccentSoft
                add -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
        count?.let {
            Text(
                " $it",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (lit) ButlerTheme.colors.onAccentSoft.copy(alpha = 0.75f) else ButlerTheme.colors.textLow,
            )
        }
        if (more) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Group menu", tint = ButlerTheme.colors.onAccentSoft, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
    }
}

@Composable
private fun PersonaPickRow(option: PersonaOption, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = option.avatarUrl, name = option.name, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (option.id == null) {
                Text("Your profile", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
        }
        if (chosen) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
