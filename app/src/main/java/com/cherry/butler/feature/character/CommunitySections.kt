package com.cherry.butler.feature.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.Comment
import com.cherry.butler.core.data.PublishedChatCard
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.model.Lorebook
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.BrowseTileFooter
import com.cherry.butler.ui.components.CharacterTile
import com.cherry.butler.ui.components.CountPill
import com.cherry.butler.ui.components.Roster
import com.cherry.butler.ui.components.TileGrid
import com.cherry.butler.ui.components.card

/**
 * Below the character's own page, in Janitor's order: lorebooks, published chats, the
 * comments, and similar characters. Each part appears once it has arrived; a part with
 * nothing in it is left out rather than shown empty.
 */
fun LazyListScope.communitySections(
    lorebooks: List<Lorebook>,
    state: CharacterDetailUiState?,
    onOpenComments: () -> Unit,
    onOpenPublished: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
    onSeeAllPublished: () -> Unit = {},
) {
    if (lorebooks.isNotEmpty()) {
        item(key = "lore-title", contentType = "section") { SectionTitle("Lorebooks") }
        lorebooks.forEach { book ->
            item(key = "lore-${book.id}", contentType = "lorebook") { LorebookRow(book) }
        }
    }

    val published = state?.published.orEmpty()
    if (published.isNotEmpty()) {
        val total = state?.publishedTotal?.takeIf { it > 0 } ?: published.size
        item(key = "pub-title", contentType = "section") {
            // Two on the page, as Janitor shows them; the rest behind "See all".
            SectionTitle("Published chats", count = total, onSeeAll = if (total > PUBLISHED_PREVIEW) onSeeAllPublished else null)
        }
        published.take(PUBLISHED_PREVIEW).forEach { chat ->
            item(key = "pub-${chat.slug}", contentType = "published") { PublishedRow(chat) { onOpenPublished(chat.slug) } }
        }
    }

    if (state?.commentsLoaded == true) {
        item(key = "comments", contentType = "comments") {
            SectionTitle("Comments")
            CommentsCard(state.topComment, onOpenComments)
        }
    }

    val similar = state?.similar.orEmpty()
    if (similar.isNotEmpty()) {
        item(key = "similar-title", contentType = "section") { SectionTitle("Similar characters") }
        similar.chunked(2).forEachIndexed { row, pair ->
            item(key = "similar-$row", contentType = "similar-row") {
                val width = TileGrid.tileWidth()
                Row(
                    modifier = Modifier.padding(horizontal = TileGrid.gutter, vertical = TileGrid.gap / 2),
                    horizontalArrangement = Arrangement.spacedBy(TileGrid.gap),
                ) {
                    pair.forEach { c ->
                        CharacterTile(
                            name = c.name,
                            avatarUrl = c.avatarUrl,
                            width = width,
                            onClick = { onOpenCharacter(c.id) },
                            obscured = c.isImageNsfw,
                            nsfw = c.isNsfw,
                            chatCount = c.chatCount.takeIf { it > 0 }?.compactCount(),
                        ) {
                            BrowseTileFooter(creatorName = c.creatorName, creatorVerified = c.creatorVerified, blurb = c.blurb, tags = c.tagNames, creatorColor = c.creatorColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, count: Int? = null, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Roster.gutter, end = 8.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ButlerTheme.colors.textMed)
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            CountPill(count.toString())
        }
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onSeeAll).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private const val PUBLISHED_PREVIEW = 2

@Composable
private fun LorebookRow(book: Lorebook) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .card()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = ButlerTheme.colors.speech, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildString {
                    append(if (book.isPublic) "Public" else "Private")
                    book.updatedAt?.let { append(" · ").append(RelativeTime.fullDate(it)) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = ButlerTheme.colors.textLow,
            )
        }
        if (book.messageCount > 0) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = ButlerTheme.colors.textLow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(book.messageCount.compactCount(), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
        }
    }
}

@Composable
fun PublishedRow(chat: PublishedChatCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .card()
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = chat.publisherAvatar, name = chat.publisherName, size = 24.dp, initialStyle = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("by @${chat.publisherName}", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = chat.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (chat.description.isNotBlank()) {
            Text(chat.description, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Visibility, contentDescription = "Views", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(chat.views.toLong().compactCount(), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Messages", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(chat.messageCount.toString(), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
            Spacer(Modifier.weight(1f))
            chat.publishedAt?.let { Text(RelativeTime.short(it), style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow) }
        }
    }
}

@Composable
private fun CommentsCard(top: Comment?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .card()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (top == null) {
            Text("No comments yet", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.weight(1f))
        } else {
            Avatar(url = top.author.avatarUrl, name = top.author.name, size = 36.dp, initialStyle = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("@${top.author.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(top.content, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = "All comments", tint = ButlerTheme.colors.textLow)
    }
}
