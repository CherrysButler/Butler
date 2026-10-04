package com.cherry.butler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.cherry.butler.core.design.Pill
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme

/** Gutters every list shares, so rules line up from tab to tab. */
object Roster {
    val gutter = 16.dp
    val thumb = 60.dp
    val marginWidth = 28.dp
}

/**
 * One line of a roster or a save-slot list: a number in the margin, a framed thumb, a
 * name plate, and whatever the list has to say beneath it. A hairline closes the row.
 *
 * Browse and Chats are both made of these; what differs is the footer and the margin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RosterRow(
    name: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    margin: String? = null,
    obscured: Boolean = false,
    nsfw: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pinned: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(
            modifier = Modifier.padding(horizontal = Roster.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (margin != null) {
                Text(
                    text = margin,
                    style = MaterialTheme.typography.labelSmall,
                    color = ButlerTheme.colors.textLow,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(Roster.marginWidth).padding(top = 2.dp, end = 10.dp),
                )
            }
            Avatar(url = avatarUrl, name = name, size = Roster.thumb, obscured = obscured)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (pinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = ButlerTheme.colors.textLow,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    if (nsfw) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "18+",
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.danger,
                        )
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(12.dp))
                        trailing()
                    }
                }
                Spacer(Modifier.height(4.dp))
                footer()
            }
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(horizontal = Roster.gutter))
    }
}

/** A count or a time that sits tight against the right margin, in tabular figures. */
@Composable
fun MarginFigure(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = ButlerTheme.colors.textLow,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

/** A roster row's shape, breathing, until the list lands. */
@Composable
fun SkeletonRosterRow(withMargin: Boolean) {
    val alpha = rememberSkeletonAlpha()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = Roster.gutter, vertical = 12.dp)) {
            if (withMargin) Spacer(Modifier.width(Roster.marginWidth))
            SkeletonBlock(width = Roster.thumb, height = Roster.thumb, radius = 12.dp, alpha = alpha)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                SkeletonBlock(width = 150.dp, height = 14.dp, alpha = alpha)
                SkeletonBlock(width = 240.dp, height = 11.dp, alpha = alpha)
                SkeletonBlock(width = 120.dp, height = 10.dp, alpha = alpha)
            }
        }
        HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(horizontal = Roster.gutter))
    }
}

/** "4 chats" in a red-tinted pill, as Janitor marks a character's chat count. Tabular, small. */
@Composable
fun CountPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, Pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ButlerTheme.colors.onAccentSoft, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = ButlerTheme.colors.onAccentSoft,
            maxLines = 1,
        )
    }
}
