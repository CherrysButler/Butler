package com.cherry.butler.feature.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.TokenCounts
import com.cherry.butler.core.data.remote.CommentMode
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.markdown.RichHtml
import com.cherry.butler.feature.browse.TagPickerSheet
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.feature.settings.SwitchRow
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.BrowseTileFooter
import com.cherry.butler.ui.components.CharacterTile
import com.cherry.butler.ui.components.MarginFigure
import com.cherry.butler.ui.components.SegmentedChoice
import com.cherry.butler.ui.components.TagPill
import com.cherry.butler.ui.components.hairline
import com.cherry.butler.ui.components.hairlineFrame

/**
 * The check a person passes before a character is saved: Janitor asks for a fresh Cloudflare
 * Turnstile token with every save. The app supplies it; [onToken] gets the token, [onDismiss]
 * closes it without one.
 */
typealias CharacterCheck = @Composable (onToken: (String) -> Unit, onDismiss: () -> Unit) -> Unit

/** The check the website asks for before a save: Turnstile on its character key, passed by the person. */
val CharacterSaveCheck: CharacterCheck = { onToken, onDismiss ->
    com.cherry.butler.feature.auth.TurnstileSheet(onToken = onToken, onDismiss = onDismiss, check = com.cherry.butler.feature.auth.TurnstileCheck.CharacterSave)
}

/** The bot, in the four parts it's written in. */
private enum class Chapter(val title: String) { Look("Look"), Mind("Mind"), Voice("Voice"), Settings("Settings") }

/** From this width the chapters sit in a rail and the card stays beside them (the desktop pane). */
private val WideFrom = 840.dp

/**
 * Writing a character, in four chapters, one at a time: Look (picture, names, description,
 * tags), Mind (personality, scenario), Voice (first messages, example dialogs) and Settings.
 * The Browse card it will make and the token total stay in reach while you write: beside the
 * chapter on a wide window, in the footer and in Look on a phone. Save says why it waits.
 */
@Composable
fun CharacterEditorScreen(
    onBack: () -> Unit,
    /** After a save: the character's id. */
    onSaved: (String) -> Unit,
    check: CharacterCheck = CharacterSaveCheck,
    viewModel: CharacterEditorViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val creator by viewModel.creatorName.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val d = form.draft
    var chapter by rememberSaveable { mutableStateOf(Chapter.Look) }
    var checking by remember { mutableStateOf(false) }
    var pickingTags by remember { mutableStateOf(false) }
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val pictureJob by viewModel.picture.collectAsStateWithLifecycle()
    // Leaving loses nothing: what's written is kept on the device as a draft.
    val back = onBack

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.pickPicture(uri) }
    val pickPicture = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val picture = form.picture
    // What still holds the save back, and in which chapter, so the chapter can say so.
    val missing: Pair<Chapter, String>? = when {
        !form.loaded -> null
        !form.hasPicture -> Chapter.Look to "Add a picture to save"
        d.name.isBlank() -> Chapter.Look to "Name it to save"
        d.intros.isEmpty() -> Chapter.Voice to "Write a first message to save"
        else -> null
    }
    val save = { checking = true }

    if (checking) {
        check(
            { token -> checking = false; viewModel.save(token, onSaved) },
            { checking = false },
        )
    }
    restore?.let { kept ->
        AlertDialog(
            onDismissRequest = viewModel::restoreDraft,
            title = { Text("Continue where you left off?") },
            text = {
                Text(
                    "Changes to this character from ${com.cherry.butler.core.util.RelativeTime.short(kept.updatedAt)} are kept on this device. " +
                        "They aren't on Janitor yet.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::restoreDraft) { Text("Continue them") } },
            dismissButton = { TextButton(onClick = viewModel::discardDraft) { Text("Start from what's saved", color = ButlerTheme.colors.danger) } },
        )
    }

    if (pickingTags) {
        TagPickerSheet(
            tags = tags,
            selected = d.tagIds,
            onToggle = viewModel::toggleTag,
            onClearAll = viewModel::clearTags,
            onDismiss = { pickingTags = false },
            onAddCustom = viewModel::addCustomTag,
        )
    }

    val tagNames = d.tagIds.mapNotNull { id -> tags.firstOrNull { it.id == id }?.name }
    val card: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { width ->
        CharacterTile(
            name = d.name.ifBlank { "Your character" },
            avatarUrl = picture,
            width = width,
            onClick = { chapter = Chapter.Look },
            nsfw = d.nsfw,
        ) {
            BrowseTileFooter(
                creatorName = creator,
                creatorVerified = false,
                blurb = RichHtml.toPlainText(d.description),
                tags = tagNames + d.customTags.map { "#$it" },
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        val wide = maxWidth >= WideFrom
        Column(modifier = Modifier.fillMaxSize()) {
            // The bar: back, the bot as it's called so far, and on a wide window Save.
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        d.name.ifBlank { if (viewModel.isNew) "New character" else "Edit character" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val status = form.error ?: if (form.saving) "Saving…" else if (form.dirty) "Draft kept on this device" else null
                    if (status != null) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (form.error != null) ButlerTheme.colors.danger else if (form.saving) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (wide) {
                    missing?.let { Text(it.second, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.padding(end = 12.dp)) }
                    KeyButton(if (form.saving) "Saving…" else "Save", onClick = save, enabled = form.savable && !form.saving, primary = true)
                }
            }
            if (!wide) {
                SegmentedChoice(
                    options = Chapter.entries.map { c -> if (missing?.first == c) "${c.title} •" else c.title },
                    selected = chapter.ordinal,
                    onSelect = { chapter = Chapter.entries[it] },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(hairline()).background(ButlerTheme.colors.outlineFaint))
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    ChapterRail(
                        selected = chapter,
                        onSelect = { chapter = it },
                        stateOf = { c -> chapterState(c, form, tokens, live, missing) },
                        needs = missing?.first,
                    )
                    Box(Modifier.width(hairline()).fillMaxHeight().background(ButlerTheme.colors.outlineFaint))
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                            ChapterHead(chapter, chapterState(chapter, form, tokens, live, missing))
                            when (chapter) {
                                Chapter.Look -> LookChapter(
                                    viewModel = viewModel,
                                    picture = picture,
                                    tagNames = tagNames,
                                    onPickPicture = pickPicture,
                                    uploading = pictureJob != null && pictureJob?.failure == null,
                                    pictureFailure = pictureJob?.failure,
                                    onPickTags = { pickingTags = true },
                                    // On a phone the card has no column of its own: it's here, under the writing.
                                    card = if (wide) null else ({ card(220.dp) }),
                                )
                                Chapter.Mind -> MindChapter(viewModel, tokens)
                                Chapter.Voice -> VoiceChapter(viewModel, tokens)
                                Chapter.Settings -> SettingsChapter(viewModel, live)
                            }
                        }
                    }
                }
                if (wide) {
                    Box(Modifier.width(hairline()).fillMaxHeight().background(ButlerTheme.colors.outlineFaint))
                    Column(
                        modifier = Modifier.width(276.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("In Browse", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
                        card(244.dp)
                        TokenBudget(tokens)
                    }
                }
            }
            if (!wide) {
                // The footer on a phone: the picture, the size so far, and Save, above the keyboard.
                Box(Modifier.fillMaxWidth().height(hairline()).background(ButlerTheme.colors.outlineFaint))
                Row(
                    modifier = Modifier.fillMaxWidth().background(ButlerTheme.colors.chrome).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        url = picture,
                        name = d.name.ifBlank { "?" },
                        size = 36.dp,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { chapter = Chapter.Look },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tokens?.let { "${"%,d".format(it.total)} tokens" } ?: "Counting…",
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            missing?.second ?: if (viewModel.isNew) "Saved private" else "Ready to save",
                            style = MaterialTheme.typography.labelSmall,
                            color = ButlerTheme.colors.textLow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    KeyButton(if (form.saving) "Saving…" else "Save", onClick = save, enabled = form.savable && !form.saving, primary = true)
                }
            }
        }
    }
}

/** One line on where a chapter stands: what's missing, or what's in it. */
private fun chapterState(
    c: Chapter,
    form: CharacterForm,
    tokens: TokenCounts?,
    live: CharacterEditorViewModel.LiveSettings?,
    missing: Pair<Chapter, String>?,
): String {
    val d = form.draft
    fun t(n: Int) = "${"%,d".format(n)} tokens"
    return when (c) {
        Chapter.Look -> when {
            missing?.first == Chapter.Look -> if (!form.hasPicture) "Needs a picture" else "Needs a name"
            else -> listOfNotNull("picture", "name", "description".takeIf { d.description.isNotBlank() }, (d.tagIds.size + d.customTags.size).takeIf { it > 0 }?.let { "$it tags" })
                .joinToString(", ").replaceFirstChar { it.uppercase() }
        }
        Chapter.Mind -> tokens?.let { (it.personality + it.scenario).takeIf { n -> n > 0 }?.let(::t) } ?: "Not written yet"
        Chapter.Voice -> when {
            d.intros.isEmpty() -> "Needs a first message"
            else -> "${d.intros.size} first message${if (d.intros.size == 1) "" else "s"}" + (tokens?.let { " · ${t(it.exampleDialogs + it.longestIntro)}" } ?: "")
        }
        Chapter.Settings -> listOfNotNull(
            if (live?.isPublic == true) "Public" else "Private",
            "NSFW".takeIf { d.nsfw },
        ).joinToString(" · ")
    }
}

/** The chapters at the left of a wide window: each with where it stands; the open one lit. */
@Composable
private fun ChapterRail(selected: Chapter, onSelect: (Chapter) -> Unit, stateOf: (Chapter) -> String, needs: Chapter?) {
    Column(modifier = Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Chapter.entries.forEach { c ->
            val on = c == selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(c) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) ButlerTheme.colors.onAccentSoft else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (needs == c) Box(Modifier.size(7.dp).clip(Pill).background(ButlerTheme.colors.danger))
                }
                Text(
                    stateOf(c),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (needs == c) ButlerTheme.colors.danger else if (on) ButlerTheme.colors.onAccentSoft.copy(alpha = 0.8f) else ButlerTheme.colors.textLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChapterHead(c: Chapter, state: String) {
    val line = when (c) {
        Chapter.Look -> "What people see before they chat: the picture, the names, the description and the tags."
        Chapter.Mind -> "Who they are, and where the story starts. Both go to the model with every message."
        Chapter.Voice -> "How they open a chat, and how they sound."
        Chapter.Settings -> "Who it's for, and who may use it how."
    }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Text(c.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(top = 4.dp))
        Text(state, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = ButlerTheme.colors.textLow, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookChapter(
    viewModel: CharacterEditorViewModel,
    picture: String?,
    tagNames: List<String>,
    onPickPicture: () -> Unit,
    uploading: Boolean,
    pictureFailure: String?,
    onPickTags: () -> Unit,
    card: (@Composable () -> Unit)?,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val d = form.draft
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // The slot: the picture, or a portrait breathing in Butler's dots until there is one.
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(MaterialTheme.shapes.large)
                .background(ButlerTheme.colors.surfaceHigh)
                .clickable(enabled = !form.saving && !uploading, onClick = onPickPicture),
        ) {
            if (picture != null) {
                Avatar(url = picture, name = d.name.ifBlank { "?" }, size = 104.dp, initialStyle = MaterialTheme.typography.headlineMedium)
            } else {
                PortraitWaiting(ink = ButlerTheme.colors.textLow, modifier = Modifier.matchParentSize())
            }
            if (uploading) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)))
                PortraitWaiting(ink = MaterialTheme.colorScheme.primary, busy = true, modifier = Modifier.matchParentSize())
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (form.hasPicture) "The picture" else "A picture", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                when {
                    uploading -> "Uploading\u2026"
                    pictureFailure != null -> "Not uploaded. $pictureFailure"
                    form.hasPicture -> "On its card, its page and in chats."
                    else -> "Janitor needs one before it saves a character."
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    uploading -> MaterialTheme.colorScheme.primary
                    pictureFailure != null -> ButlerTheme.colors.danger
                    form.hasPicture -> ButlerTheme.colors.textMed
                    else -> ButlerTheme.colors.textLow
                },
            )
            Text(
                if (form.hasPicture) "Change picture" else "Choose a picture",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(enabled = !form.saving && !uploading, onClick = onPickPicture).padding(vertical = 6.dp),
            )
        }
    }
    WritingField(
        label = "Name",
        help = null,
        value = d.name,
        onChange = { v -> viewModel.edit { it.copy(name = v) } },
        singleLine = true,
        prose = false,
    )
    WritingField(
        label = "Name in chats",
        help = "Only if the character goes by another name in the story.",
        value = d.chatName,
        onChange = { v -> viewModel.edit { it.copy(chatName = v) } },
        singleLine = true,
        prose = false,
    )
    Spacer(Modifier.height(8.dp))
    com.cherry.butler.ui.components.RichHtmlEditor(
        label = "Description",
        html = d.description,
        onChange = { v -> viewModel.edit { it.copy(description = v) } },
        enabled = !form.saving,
        placeholder = "What people read on its page, before they chat",
        minLines = 5,
        uploadPicture = viewModel::uploadDescriptionPicture,
    )
    FieldHead("Tags", help = "So people find it in Browse.", figure = null)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tagNames.forEach { TagPill(it, modifier = Modifier.align(Alignment.CenterVertically)) }
        d.customTags.forEach { tag ->
            Row(
                modifier = Modifier.align(Alignment.CenterVertically).clip(Pill).clickable { viewModel.removeCustomTag(tag) }.padding(end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagPill("#$tag")
                Icon(Icons.Rounded.Close, contentDescription = "Remove #$tag", tint = ButlerTheme.colors.textLow, modifier = Modifier.padding(start = 2.dp).size(14.dp))
            }
        }
        Text(
            if (tagNames.isEmpty() && d.customTags.isEmpty()) "Choose tags" else "Change tags",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterVertically).clip(MaterialTheme.shapes.small).clickable(onClick = onPickTags).padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
    if (card != null) {
        FieldHead("In Browse", help = "The card people tap, as it looks now.", figure = null)
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { card() }
    }
}

@Composable
private fun MindChapter(viewModel: CharacterEditorViewModel, tokens: TokenCounts?) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val d = form.draft
    WritingField(
        label = "Personality",
        help = "Who they are: how they think, talk and act.",
        figure = tokens?.personality,
        value = d.personality,
        onChange = { v -> viewModel.edit { it.copy(personality = v) } },
        minLines = 12,
    )
    WritingField(
        label = "Scenario",
        help = "Where the story starts, and what's going on.",
        figure = tokens?.scenario,
        value = d.scenario,
        onChange = { v -> viewModel.edit { it.copy(scenario = v) } },
        minLines = 6,
    )
}

@Composable
private fun VoiceChapter(viewModel: CharacterEditorViewModel, tokens: TokenCounts?) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val d = form.draft
    FieldHead("First messages", help = "A chat opens with the first; people can swipe to the others.", figure = null)
    d.firstMessages.forEachIndexed { i, text ->
        // The counts are for the intros that will be saved, the blank ones left out.
        val count = if (text.isBlank()) null else tokens?.intros?.getOrNull(d.firstMessages.take(i).count { it.isNotBlank() })
        WritingField(
            label = if (d.firstMessages.size > 1) "First message ${i + 1}" else "The first message",
            help = null,
            figure = count,
            value = text,
            onChange = { v -> viewModel.setIntro(i, v) },
            minLines = 6,
            actions = if (d.firstMessages.size > 1) ({
                IntroKey(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = i > 0) { viewModel.moveIntro(i, -1) }
                IntroKey(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = i < d.firstMessages.lastIndex) { viewModel.moveIntro(i, 1) }
                IntroKey(Icons.Rounded.Close, "Remove", enabled = true) { viewModel.removeIntro(i) }
            }) else null,
        )
    }
    KeyButton("Add a first message", onClick = viewModel::addIntro, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Spacer(Modifier.height(12.dp))
    WritingField(
        label = "Example dialogs",
        help = "Short exchanges that show how they speak. {{user}} and {{char}} stand for you and them.",
        figure = tokens?.exampleDialogs,
        value = d.exampleDialogs,
        onChange = { v -> viewModel.edit { it.copy(exampleDialogs = v) } },
        minLines = 8,
    )
}

@Composable
private fun SettingsChapter(viewModel: CharacterEditorViewModel, live: CharacterEditorViewModel.LiveSettings?) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    SwitchRow(
        "NSFW",
        "For adults only: kept from people who don't see NSFW. Saved with the rest.",
        form.draft.nsfw,
        { on -> viewModel.edit { it.copy(nsfw = on) } },
    )
    if (live == null) return
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(hairline()).background(ButlerTheme.colors.outlineFaint))
    FieldHead("These change at once", help = "Save is only for the writing.", figure = null)
    SwitchRow("Public", "On Browse and your profile. Off keeps it to you.", live.isPublic, viewModel::setPublic)
    SwitchRow("Proxies allowed", "People can chat with it on their own proxy, not only JLLM.", live.allowProxy, { viewModel.setSwitch(MyCharactersViewModel.SWITCH_PROXY, it) })
    SwitchRow("Definition shown", "Its personality, scenario and examples are on its page for everyone.", live.showDefinition, { viewModel.setSwitch(MyCharactersViewModel.SWITCH_DEFINITION, it) })
    SwitchRow("Published chats", "People can publish their chats with it.", live.allowPublishedChats, { viewModel.setSwitch(MyCharactersViewModel.SWITCH_PUBLISHED_CHATS, it) })
    FieldHead("Comments", help = null, figure = null)
    SegmentedChoice(
        options = CommentMode.entries.map { it.label },
        selected = live.comments.ordinal,
        onSelect = { viewModel.setComments(CommentMode.entries[it]) },
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
    live.error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.danger, modifier = Modifier.padding(horizontal = 16.dp)) }
}

/** The size so far, as Janitor will count and show it: the total, and what makes it up. */
@Composable
private fun TokenBudget(tokens: TokenCounts?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tokens?.let { "${"%,d".format(it.total)} tokens" } ?: "Counting…",
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("What a chat starts with, as Janitor counts it.", style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow)
        if (tokens != null) {
            Spacer(Modifier.height(2.dp))
            listOf(
                "Personality" to tokens.personality,
                "Scenario" to tokens.scenario,
                "Example dialogs" to tokens.exampleDialogs,
                "Longest first message" to tokens.longestIntro,
            ).forEach { (name, n) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, modifier = Modifier.weight(1f))
                    MarginFigure("%,d".format(n))
                }
            }
        }
    }
}

/** A field's head: its name, the count beside it, and one line on what it's for. */
@Composable
private fun FieldHead(label: String, help: String?, figure: Int?, actions: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 24.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            actions?.invoke()
            if (figure != null && figure > 0) MarginFigure("${"%,d".format(figure)} tokens", modifier = Modifier.padding(start = 8.dp))
        }
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * A place to write: its head, then Butler's raised field (the red edge while it's being written
 * in). What the creator writes is set in the prose face; names in the app's own.
 */
@Composable
private fun WritingField(
    label: String,
    help: String?,
    value: String,
    onChange: (String) -> Unit,
    figure: Int? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    prose: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    FieldHead(label, help, figure, actions)
    val style: TextStyle = if (prose) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .hairlineFrame(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        // An empty place to write breathes as unwritten lines, until it's focused or written in.
        val unwritten by androidx.compose.animation.core.animateFloatAsState(
            if (value.isEmpty() && !focused && !singleLine) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(
                if (value.isEmpty() && !focused) com.cherry.butler.core.design.Motion.MEDIUM else com.cherry.butler.core.design.Motion.SHORT,
                easing = com.cherry.butler.core.design.Motion.EaseOut,
            ),
            label = "unwritten",
        )
        if (unwritten > 0f) {
            UnwrittenLines(
                ink = ButlerTheme.colors.textLow.copy(alpha = 0.7f),
                lineHeight = style.lineHeight.takeIf { it.isSpecified } ?: 24.sp,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = unwritten },
                seed = label.hashCode(),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { if (singleLine) onChange(it.filterNot { c -> c == '\n' || c == '\r' }) else onChange(it) },
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
private fun IntroKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) ButlerTheme.colors.textMed else ButlerTheme.colors.outlineFaint, modifier = Modifier.size(20.dp))
    }
}
