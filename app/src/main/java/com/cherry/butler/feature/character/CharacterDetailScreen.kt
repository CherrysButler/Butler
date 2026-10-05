package com.cherry.butler.feature.character

import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.design.ButlerTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Code
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.cherry.butler.core.markdown.stripPersonaMarks
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.ui.components.Roster
import com.cherry.butler.ui.components.TagPill
import com.cherry.butler.ui.components.card
import com.cherry.butler.core.model.Lorebook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.DescriptionText
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.core.model.CharacterChat
import com.cherry.butler.core.model.compactCount
import com.cherry.butler.core.util.RelativeTime
import com.cherry.butler.ui.components.CenteredMessage
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.PersonaPickerSheet
import com.cherry.butler.ui.components.PersonaSwitch
import com.cherry.butler.ui.components.ditherFade
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.ui.components.SkeletonBlock
import com.cherry.butler.ui.components.isRetryable
import com.cherry.butler.ui.components.userMessage
import com.cherry.butler.ui.components.userTitle

/**
 * The character page. Paints from the browse mirror the instant it opens and fills in the
 * full object behind it; one primary action — start a chat — pinned where the thumb is.
 */
@Composable
fun CharacterDetailScreen(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenComments: (characterId: String, name: String) -> Unit = { _, _ -> },
    onOpenPublished: (String) -> Unit = {},
    onOpenCharacter: (String) -> Unit = {},
    onSeeAllPublished: (characterId: String, name: String) -> Unit = { _, _ -> },
    viewModel: CharacterDetailViewModel = hiltViewModel(),
) {
    val mirror by viewModel.mirror.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val personaOptions by viewModel.personaOptions.collectAsStateWithLifecycle()
    val personaName = persona?.name
    var pickingPersona by remember { mutableStateOf(false) }

    if (pickingPersona) {
        val personaGroups by viewModel.personaGroups.collectAsStateWithLifecycle()
        PersonaPickerSheet(
            options = personaOptions,
            groups = personaGroups,
            selected = persona,
            onPick = { viewModel.choosePersona(it); pickingPersona = false },
            onDismiss = { pickingPersona = false },
        )
    }

    // Whichever source is richer paints; the mirror covers the first frame.
    val header = state.detail?.let { d ->
        Header(
            name = d.name, chatName = d.chatName, avatarUrl = d.avatarUrl, creatorName = d.creatorName,
            creatorVerified = d.creatorVerified, chatCount = d.chatCount, messageCount = d.messageCount,
            tokens = d.totalTokens, tagNames = d.tagNames, description = d.description,
            isNsfw = d.isNsfw, isImageNsfw = d.isImageNsfw,
        )
    } ?: mirror?.let { m ->
        Header(
            name = m.name, chatName = null, avatarUrl = JanitorConfig.avatarUrl(m.avatar), creatorName = m.creatorName,
            creatorVerified = m.creatorVerified, chatCount = m.chatCount, messageCount = m.messageCount,
            tokens = m.totalTokens, tagNames = m.tagNames, description = m.description,
            isNsfw = m.isNsfw, isImageNsfw = m.isImageNsfw,
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // null: no question; false: block the character; true: block its creator.
    var blocking by remember { mutableStateOf<Boolean?>(null) }
    blocking?.let { creator ->
        com.cherry.butler.feature.chat.DeleteConfirmDialog(
            count = 1,
            title = if (creator) "Block @${header?.creatorName.orEmpty()}" else "Block ${header?.name.orEmpty()}",
            body = (if (creator) "You won't see their characters any more." else "You won't see this character any more.") +
                " You can unblock it in Settings › Blocked.",
            confirmLabel = "Block",
            onConfirm = { blocking = null; viewModel.block(creator, onDone = onBack) },
            onDismiss = { blocking = null },
        )
    }
    // The card under the bar already carries the name; the bar takes it once that scrolls away.
    val titleGone by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
    }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = header?.name.orEmpty(),
                showTitle = titleGone || header == null,
                chatCount = null,
                onBack = onBack,
                favorited = state.favorited,
                favoriteCount = state.favoriteCount,
                onFavorite = viewModel::toggleFavorite,
                creatorName = header?.creatorName,
                onBlock = { creator -> blocking = creator },
            )
            state.socialError?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.danger, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    header == null && state.loading -> HeaderSkeleton()
                    header == null && state.error != null -> CenteredMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = state.error!!.userTitle(),
                        body = state.error!!.userMessage(),
                    ) {
                        if (state.error!!.isRetryable()) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::load, shape = MaterialTheme.shapes.medium) { Text("Try again") }
                        }
                    }
                    header != null -> Content(
                        header = header,
                        personaName = personaName,
                        definition = state.detail?.let { d ->
                            if (d.showDefinition) listOfNotNull(
                                d.personality?.let { "Personality" to it },
                                d.scenario?.let { "Scenario" to it },
                            ) else emptyList()
                        }.orEmpty(),
                        onOpenChat = onOpenChat,
                        bottomInset = 104.dp,
                        lorebooks = state.detail?.lorebooks.orEmpty(),
                        state = state,
                        onOpenComments = { onOpenComments(viewModel.characterId, header.name) },
                        onOpenPublished = onOpenPublished,
                        onOpenCharacter = onOpenCharacter,
                        onSeeAllPublished = { onSeeAllPublished(viewModel.characterId, header.name) },
                        onFollow = viewModel::toggleFollow,
                        listState = listState,
                    )
                }
            }
        }

        if (header != null) {
            StartChatBar(
                starting = state.startingChat,
                error = state.startError?.userMessage(),
                persona = persona,
                onPickPersona = { pickingPersona = true },
                onStart = { viewModel.startChat(onOpenChat) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Back, the name, the chat count: Janitor's bar over a character. */
@Composable
private fun TopBar(
    title: String,
    chatCount: Long?,
    showTitle: Boolean = true,
    onBack: () -> Unit,
    favorited: Boolean? = null,
    favoriteCount: Int? = null,
    onFavorite: () -> Unit = {},
    creatorName: String? = null,
    /** false blocks the character, true its creator. */
    onBlock: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
        }
        val titleAlpha by androidx.compose.animation.core.animateFloatAsState(if (showTitle) 1f else 0f, label = "bar-title")
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .graphicsLayer { alpha = titleAlpha },
        )
        if (chatCount != null) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = chatCount.compactCount(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = ButlerTheme.colors.textMed,
            )
        }
        if (favorited != null) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onFavorite).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.animation.Crossfade(targetState = favorited, label = "heart") { on ->
                    Icon(
                        imageVector = if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (on) "Unfavourite" else "Favourite",
                        tint = if (on) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (favoriteCount != null) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = favoriteCount.toLong().compactCount(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = if (favorited) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                    )
                }
            }
        }
        if (onBlock != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Block character", style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                        onClick = { menu = false; onBlock(false) },
                    )
                    if (!creatorName.isNullOrBlank()) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Block @$creatorName", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.PersonOff, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                            onClick = { menu = false; onBlock(true) },
                        )
                    }
                }
            }
        }
    }
}

private data class Header(
    val name: String,
    val chatName: String?,
    val avatarUrl: String?,
    val creatorName: String,
    val creatorVerified: Boolean,
    val chatCount: Long,
    val messageCount: Long,
    val tokens: Int,
    val tagNames: List<String>,
    val description: String,
    val isNsfw: Boolean,
    val isImageNsfw: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(
    header: Header,
    personaName: String?,
    definition: List<Pair<String, String>>,
    onOpenChat: (Long) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
    lorebooks: List<Lorebook> = emptyList(),
    state: CharacterDetailUiState? = null,
    onOpenComments: () -> Unit = {},
    onOpenPublished: (String) -> Unit = {},
    onOpenCharacter: (String) -> Unit = {},
    onSeeAllPublished: () -> Unit = {},
    onFollow: () -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset),
    ) {
        item(key = "hero", contentType = "hero") {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .card(shape = MaterialTheme.shapes.large)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)) {
                    Text(
                        text = header.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (header.isNsfw) {
                        Spacer(Modifier.width(10.dp))
                        NsfwBadge(modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Portrait(header)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // Creator and Follow share what the tokens leave, so the name only cuts when it must.
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("by ", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
                        Text(
                            text = "@${header.creatorName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ButlerTheme.colors.onAccentSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (header.creatorVerified) {
                            Spacer(Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verified creator",
                                tint = ButlerTheme.colors.speech,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    state?.following?.let { following -> FollowPill(following, onFollow) }
                    }
                    if (header.tokens > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${header.tokens} tokens",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = ButlerTheme.colors.textLow,
                        )
                    }
                }
                header.chatName?.let {
                    Text(
                        text = "Goes by $it in chat",
                        style = MaterialTheme.typography.labelMedium,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                    )
                }
                if (header.tagNames.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        header.tagNames.forEach { TagPill(it) }
                    }
                }
                // The description sits on its own darker panel inside the card, as on Janitor.
                DescriptionText(
                    text = header.description.fillNames(user = personaName, char = header.name, markUser = true),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .card(color = MaterialTheme.colorScheme.background)
                        .padding(14.dp),
                )
            }
        }

        if (definition.isNotEmpty()) {
            item(key = "definition", contentType = "definition") {
                DefinitionSection(definition.map { (title, body) -> title to body.fillNames(user = personaName, char = header.name, markUser = true) })
            }
        }

        communitySections(
            lorebooks = lorebooks,
            state = state,
            onOpenComments = onOpenComments,
            onOpenPublished = onOpenPublished,
            onOpenCharacter = onOpenCharacter,
            onSeeAllPublished = onSeeAllPublished,
        )
    }
}

@Composable
private fun Portrait(header: Header) {
    var revealed by remember(header.avatarUrl) { mutableStateOf(!header.isImageNsfw) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh),
    ) {
        Text(
            text = header.name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.displaySmall,
            color = ButlerTheme.colors.textLow,
            modifier = Modifier.align(Alignment.Center),
        )
        AsyncImage(
            model = header.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (revealed) Modifier else Modifier.blur(28.dp)),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = header.chatCount.compactCount(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${header.messageCount.compactCount()} msgs",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }
        if (!revealed) {
            Surface(
                onClick = { revealed = true },
                shape = RoundedCornerShape(12.dp),
                color = ButlerTheme.colors.surfaceElevated.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
                    Text("Show image", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * The character's definition: the creator's prompt, not their pitch. It is set apart from
 * the description on purpose: a code icon, a "what the model reads" caption, and the text
 * as plain monospace source in an inset panel with a violet margin rule.
 *
 * The open state survives scrolling: a lazy list drops an item that leaves the screen, and
 * with it any plain `remember`, so this one is saveable.
 */
@Composable
private fun DefinitionSection(sections: List<Pair<String, String>>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val violet = ButlerTheme.colors.thought
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .card()
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Code, contentDescription = null, tint = violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Definition", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("The creator's prompt: what the model reads", style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ButlerTheme.colors.textLow,
            )
        }
        if (expanded) {
            sections.forEach { (title, body) ->
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = violet,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .height(IntrinsicSize.Min),
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(violet.copy(alpha = 0.6f)))
                    Text(
                        text = body.stripPersonaMarks(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StartChatBar(
    starting: Boolean,
    error: String?,
    persona: PersonaOption?,
    onPickPersona: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = SheetShape,
    ) {
        Column {
        HairlineRule(color = ButlerTheme.colors.cardOutline)
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaSwitch(current = persona, onClick = onPickPersona, modifier = Modifier.widthIn(max = 190.dp).height(50.dp))
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onStart,
                    enabled = !starting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(if (starting) "Starting…" else "Start chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        }
    }
}

/** The same square 18+ mark the Browse card wears. */
@Composable
private fun NsfwBadge(modifier: Modifier = Modifier) {
    Text(
        text = "18+",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = ButlerTheme.colors.danger,
        modifier = modifier
            .background(ButlerTheme.colors.danger.copy(alpha = 0.16f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun HeaderSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(12.dp).fillMaxWidth().aspectRatio(0.8f).clip(MaterialTheme.shapes.large).background(ButlerTheme.colors.surfaceHigh))
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(width = 180.dp, height = 24.dp)
            SkeletonBlock(width = 120.dp, height = 12.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(width = 300.dp, height = 12.dp)
            SkeletonBlock(width = 260.dp, height = 12.dp)
            SkeletonBlock(width = 280.dp, height = 12.dp)
        }
    }
}

/** Follow the creator: a word beside their name, red until followed, quiet after. */
@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    Text(
        text = if (following) "Following" else "Follow",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (following) ButlerTheme.colors.textLow else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
