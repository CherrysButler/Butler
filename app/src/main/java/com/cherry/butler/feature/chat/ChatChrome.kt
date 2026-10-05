package com.cherry.butler.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill

/** The chat's ⋮ menu, in Janitor's order: settings for this chat, then the way out. */
@Composable
fun ChatMenu(
    /** Null while the model chip is showing, which leads there itself. */
    onModelSettings: (() -> Unit)?,
    onMemory: () -> Unit,
    onCustomize: () -> Unit,
    onCharacter: () -> Unit,
    onNewChat: () -> Unit,
    hasSummary: Boolean,
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBackground: () -> Unit = {},
    richOn: Boolean = false,
    onRich: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Chat menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            onModelSettings?.let { go -> MenuItem(Icons.Outlined.AutoAwesome, "Model settings") { open = false; go() } }
            MenuItem(Icons.Outlined.AutoStories, if (hasSummary) "Chat memory" else "Chat memory (empty)") { open = false; onMemory() }
            MenuItem(Icons.Outlined.Palette, "Customize text") { open = false; onCustomize() }
            MenuItem(Icons.Outlined.Wallpaper, "Background") { open = false; onBackground() }
            DropdownMenuItem(
                text = { Text("Rich typing", style = MaterialTheme.typography.titleSmall) },
                leadingIcon = { Icon(Icons.Outlined.FormatBold, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    androidx.compose.material3.Switch(
                        checked = richOn,
                        onCheckedChange = { onRich() },
                        modifier = Modifier.height(24.dp),
                    )
                },
                onClick = onRich,
            )
            HorizontalDivider(color = ButlerTheme.colors.outlineFaint)
            MenuItem(Icons.AutoMirrored.Outlined.Chat, "New chat") { open = false; onNewChat() }
            MenuItem(Icons.Outlined.Person, "Character page") { open = false; onCharacter() }
            MenuItem(Icons.Outlined.FileDownload, "Export chat") { open = false; onExport() }
            HorizontalDivider(color = ButlerTheme.colors.outlineFaint)
            DropdownMenuItem(
                text = { Text("Delete chat", style = MaterialTheme.typography.titleSmall, color = ButlerTheme.colors.danger) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ButlerTheme.colors.danger, modifier = Modifier.size(20.dp)) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.titleSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

/**
 * A line's quiet tools beside its name: everything (edit, copy, branch, delete) behind ⋯, and,
 * on a reply with butter in it, the butter key that folds it to just those beats.
 */
@Composable
fun LineTools(onMore: () -> Unit, butter: Boolean? = null, onButter: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (butter != null) ButterKey(on = butter, onClick = onButter)
        IconButton(onClick = onMore, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Butter mode's key on a reply: a small pat of butter, drawn rather than an emoji. Lit (the
 * reply folded to its butter) it sits on an amber wash; tapping it again unfolds the reply.
 */
@Composable
fun ButterKey(on: Boolean, onClick: () -> Unit) {
    val amber = ButlerTheme.colors.warn
    val ink = ButlerTheme.colors.textLow
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (on) Modifier.background(amber.copy(alpha = 0.18f)) else Modifier)
            .clickable(onClickLabel = if (on) "Show the whole reply" else "Show just the butter", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
            // A pat in three-quarter view: a lighter top face over its front.
            val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            val top = size.height * 0.42f
            drawRoundRect(
                color = if (on) amber else ink,
                topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
                cornerRadius = r,
            )
            drawRoundRect(
                color = (if (on) amber else ink).copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.88f, top + r.x),
                cornerRadius = r,
            )
        }
    }
}
