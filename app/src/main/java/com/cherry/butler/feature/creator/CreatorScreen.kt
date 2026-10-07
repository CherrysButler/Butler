package com.cherry.butler.feature.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.remote.LorebookDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.RichText
import com.cherry.butler.core.markdown.CreatorCss
import com.cherry.butler.core.markdown.CreatorCss.Part
import com.cherry.butler.core.markdown.RichHtml
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.BrowseTileFooter
import com.cherry.butler.ui.components.CharacterTile
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.SkeletonTile
import com.cherry.butler.ui.components.TileGrid
import com.cherry.butler.ui.components.card

/**
 * A creator's page, in Butler's own anatomy: the header (avatar, name, followers, Follow),
 * their bio as they wrote it, and their characters and lorebooks.
 *
 * Their look is honoured where it has a place here: the page colour and text colour from their
 * profile style, and the parts of their stylesheet Butler's page has (see [CreatorCss]). On
 * Daylight a creator's dark page colour is left out, so the page stays readable.
 */
@Composable
fun CreatorScreen(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    viewModel: CreatorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile
    val looks = state.looks
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    fun look(part: Part): CreatorCss.Look = looks[part] ?: CreatorCss.Look()
    fun Long?.ink(): Color? = this?.let { Color(it) }

    // The creator's page colour on the dark looks; their text colour with it.
    val pageColor = (look(Part.Page).background ?: profile?.style?.backgroundColor?.let(RichHtml::parseColor))
        .ink()?.takeIf { dark }
    val textColor = (profile?.style?.textColor?.let(RichHtml::parseColor)).ink()?.takeIf { dark && pageColor != null }
        ?: MaterialTheme.colorScheme.onSurface
    val ground = pageColor ?: MaterialTheme.colorScheme.background

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val tileWidth = TileGrid.tileWidth()

    // Load the next page as the end of the list comes into view.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, tab) { if (nearEnd) { if (tab == 0) viewModel.moreCharacters() else viewModel.moreLorebooks() } }

    Column(modifier = Modifier.fillMaxSize().background(ground).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = textColor)
            }
            Text(
                profile?.userName?.let { "@$it" }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (profile == null && state.profileError != null) {
            InlineErrorCard(state.profileError!!, onRetry = viewModel::loadProfile, modifier = Modifier.padding(16.dp))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = TileGrid.gutter, end = TileGrid.gutter, bottom = TileGrid.gutter),
            horizontalArrangement = Arrangement.spacedBy(TileGrid.gap),
            verticalArrangement = Arrangement.spacedBy(TileGrid.gap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                if (profile != null) Header(
                    profile = profile,
                    textColor = textColor,
                    headerLook = look(Part.Header),
                    nameLook = look(Part.Name),
                    bioLook = look(Part.Bio),
                    followersLook = look(Part.Followers),
                    following = state.following,
                    onFollow = viewModel::toggleFollow,
                    dark = dark,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
                val characters = state.characters.total?.let { "Characters ${it.toLong().compactCount()}" } ?: "Characters"
                val lorebooks = state.lorebooks.total?.let { "Lorebooks $it" } ?: "Lorebooks"
                SegmentedChoice(
                    options = listOf(characters, lorebooks),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }
            if (tab == 0) {
                val shelf = state.characters
                val card = look(Part.Card)
                items(shelf.items.size, key = { "c" + shelf.items[it].id }, contentType = { "character" }) { i ->
                    val c = shelf.items[i]
                    CharacterTile(
                        name = c.name,
                        avatarUrl = c.avatarUrl,
                        width = tileWidth,
                        onClick = { onOpenCharacter(c.id) },
                        obscured = c.isImageNsfw,
                        nsfw = c.isNsfw,
                        chatCount = c.chatCount.compactCount(),
                        containerColor = card.background.ink()?.takeIf { dark },
                        edgeColor = card.border.ink()?.takeIf { dark },
                        corner = card.radius?.dp,
                        nameColor = (look(Part.CardName).color ?: card.color).ink()?.takeIf { dark },
                    ) {
                        BrowseTileFooter(
                            creatorName = c.creatorName,
                            creatorVerified = c.creatorVerified,
                            creatorColor = c.creatorColor,
                            blurb = c.blurb,
                            tags = c.tagNames,
                            textColor = (look(Part.CardText).color ?: card.color).ink()?.takeIf { dark },
                        )
                    }
                }
                if (shelf.loading) items(2, contentType = { "skeleton" }) { SkeletonTile(tileWidth) }
                shelf.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { InlineErrorCard(e, onRetry = viewModel::moreCharacters) } }
                if (shelf.end && shelf.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Empty("No public characters", textColor) }
            } else {
                val shelf = state.lorebooks
                items(shelf.items.size, key = { "l" + shelf.items[it].id }, span = { GridItemSpan(maxLineSpan) }) { i ->
                    LorebookRow(shelf.items[i], look(Part.Card), dark)
                }
                shelf.error?.let { e -> item(span = { GridItemSpan(maxLineSpan) }) { InlineErrorCard(e, onRetry = viewModel::moreLorebooks) } }
                if (shelf.end && shelf.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Empty("No public lorebooks", textColor) }
            }
        }
    }
}

@Composable
private fun Header(
    profile: com.cherry.butler.core.data.remote.CreatorProfileDto,
    textColor: Color,
    headerLook: CreatorCss.Look,
    nameLook: CreatorCss.Look,
    bioLook: CreatorCss.Look,
    followersLook: CreatorCss.Look,
    following: Boolean?,
    onFollow: () -> Unit,
    dark: Boolean,
) {
    fun Long?.ink(): Color? = this?.let { Color(it) }?.takeIf { dark }
    val panel = (headerLook.background ?: bioLook.background).ink()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .card(shape = (headerLook.radius ?: bioLook.radius)?.let { RoundedCornerShape(it.dp) }, color = panel)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = JanitorConfig.personaAvatarUrl(profile.avatar), name = profile.userName, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.userName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = nameLook.color.ink() ?: textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.isVerified) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Verified, contentDescription = "Verified creator", tint = ButlerTheme.colors.speech, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    "${profile.followersCount.compactCount()} followers",
                    style = MaterialTheme.typography.labelMedium,
                    color = followersLook.color.ink() ?: textColor.copy(alpha = 0.7f),
                )
            }
            if (following != null) {
                KeyButton(if (following) "Following" else "Follow", onClick = onFollow, primary = !following)
            }
        }
        val bio = profile.aboutMe?.takeIf { RichHtml.toPlainText(it).isNotBlank() || it.contains("<img", ignoreCase = true) }
        if (bio != null) {
            RichText(
                text = bio,
                color = bioLook.color.ink() ?: textColor,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** A lorebook: its picture, title, what it says about itself, and how much it's been used. */
@Composable
private fun LorebookRow(book: LorebookDto, card: CreatorCss.Look, dark: Boolean) {
    fun Long?.ink(): Color? = this?.let { Color(it) }?.takeIf { dark }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card(shape = card.radius?.let { RoundedCornerShape(it.dp) }, color = card.background.ink())
            .then(card.border.ink()?.let { Modifier.border(1.dp, it, card.radius?.let { r -> RoundedCornerShape(r.dp) } ?: MaterialTheme.shapes.medium) } ?: Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = book.imageUrl, name = book.title, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = card.color.ink() ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            book.description?.let { RichHtml.toPlainText(it) }?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${book.messageCount.compactCount()} messages", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Empty(text: String, color: Color) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.6f))
    }
}
