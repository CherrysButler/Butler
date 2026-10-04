package com.cherry.butler.ui.components

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

/** Every persona the user can play as, the profile first; the chosen one is checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaPickerSheet(
    options: List<PersonaOption>,
    selected: PersonaOption?,
    onPick: (PersonaOption) -> Unit,
    onDismiss: () -> Unit,
) {
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
            LazyColumn(modifier = Modifier.padding(bottom = 8.dp)) {
                items(options, key = { it.id ?: "profile" }) { option ->
                    val chosen = option.id == selected?.id
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(option) }
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
                }
            }
        }
    }
}
