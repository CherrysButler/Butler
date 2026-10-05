package com.cherry.butler.feature.profile

import com.cherry.butler.feature.settings.RowDivider
import com.cherry.butler.ui.components.dropLastPixel
import com.cherry.butler.ui.components.card
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.PersonaOption
import com.cherry.butler.core.data.remote.dto.ProfileCountsDto
import com.cherry.butler.core.data.remote.dto.ProfileDto
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.DescriptionText
import com.cherry.butler.feature.chat.DeleteConfirmDialog
import com.cherry.butler.feature.chats.ScreenHead
import com.cherry.butler.feature.settings.SettingsSection
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonBlock

/**
 * You, as Janitor knows you: the profile, what you have made, who you play as, and the
 * way out. The persona list is the same switch as on a character page, given a home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    /** A persona's id, "default" for the profile, or "new". */
    onEditPersona: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val check by viewModel.signOutCheck.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val swap by viewModel.swapFlow.collectAsStateWithLifecycle()

    swap?.let { flow ->
        SwapDialog(
            flow = flow,
            onConfirm = viewModel::confirmSwap,
            onRevert = viewModel::revertSwap,
            onContinueWithoutPicture = viewModel::continueWithoutPicture,
            onClose = viewModel::closeSwap,
        )
    }

    check?.let { c ->
        DeleteConfirmDialog(
            count = 1,
            title = "Sign out",
            body = buildString {
                append("Your chats stay on Janitor. This phone forgets them, and your settings, until you sign in again.")
                if (c.unsent > 0) {
                    append("\n\n")
                    append(if (c.unsent == 1) "1 message you wrote hasn't reached Janitor yet and will be lost." else "${c.unsent} messages you wrote haven't reached Janitor yet and will be lost.")
                }
            },
            confirmLabel = "Sign out",
            onConfirm = viewModel::signOut,
            onDismiss = viewModel::cancelSignOut,
        )
    }

    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val groupError by viewModel.groupError.collectAsStateWithLifecycle()
    var moving by remember { mutableStateOf<PersonaOption?>(null) }
    var editingGroup by remember { mutableStateOf<GroupEdit?>(null) }
    var deletingGroup by remember { mutableStateOf<com.cherry.butler.core.data.remote.dto.PersonaGroupDto?>(null) }
    var shownGroup by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    moving?.let { persona ->
        MoveToGroupSheet(
            persona = persona,
            groups = groups,
            onPick = { groupId -> moving = null; viewModel.movePersona(persona.id!!, groupId) },
            onNewGroup = { moving = null; editingGroup = GroupEdit(null, "", GROUP_COLORS.first(), thenMove = persona.id) },
            onDismiss = { moving = null },
        )
    }
    editingGroup?.let { edit ->
        GroupDialog(
            start = edit,
            onSave = { name, color ->
                editingGroup = null
                if (edit.id == null) viewModel.createGroup(name, color, edit.thenMove) else viewModel.updateGroup(edit.id, name, color)
            },
            onDismiss = { editingGroup = null },
        )
    }
    deletingGroup?.let { group ->
        val inIt = options.count { it.groupId == group.id }
        DeleteConfirmDialog(
            count = 1,
            title = "Delete \u201C${group.name}\u201D",
            body = if (inIt == 0) "The group is empty." else "Its ${if (inIt == 1) "persona stays" else "$inIt personas stay"}, just out of any group.",
            confirmLabel = "Delete group",
            onConfirm = { deletingGroup = null; viewModel.deleteGroup(group.id) },
            onDismiss = { deletingGroup = null },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHead(title = "Profile")
        PullToRefreshBox(isRefreshing = refreshing && me != null, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                val p = me
                if (p == null) {
                    if (error != null) {
                        InlineErrorCard(error!!, onRetry = { viewModel.refresh() }, modifier = Modifier.padding(16.dp))
                    } else {
                        HeaderSkeleton()
                    }
                } else {
                    Header(p, counts, avatarUrl = options.firstOrNull { it.id == null }?.avatarUrl ?: p.avatar)
                    p.aboutMe?.takeIf { it.isNotBlank() }?.let { about ->
                        SettingsSection(title = "About") {
                            DescriptionText(
                                text = about,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                }

                // Who you can play as: the groups as a row across the top of the card, the
                // personas of the lit one (or everyone) under it, and a key to add a persona.
                @Composable
                fun row(option: PersonaOption) = PersonaRow(
                    option = option,
                    selected = option.id == current?.id,
                    busy = swap?.stage == SwapFlow.Stage.Running,
                    onMakeDefault = { viewModel.askMakeDefault(option) },
                    onEdit = { onEditPersona(option.id ?: "default") },
                    onMoveToGroup = if (option.id != null) ({ moving = option }) else null,
                ) { viewModel.choose(option) }

                val lit = shownGroup?.takeIf { id -> groups.any { it.id == id } }
                SettingsSection(title = "Play as") {
                    com.cherry.butler.ui.components.GroupRow(
                        groups = groups,
                        total = options.size,
                        countOf = { id -> options.count { it.groupId == id } },
                        selected = lit,
                        onSelect = { shownGroup = it },
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        onAdd = { editingGroup = GroupEdit(null, "", GROUP_COLORS.first()) },
                        menu = { group, open, close ->
                            val index = groups.indexOfFirst { it.id == group.id }
                            GroupMenu(
                                open = open,
                                onClose = close,
                                canMoveLeft = index > 0,
                                canMoveRight = index < groups.lastIndex,
                                onEdit = { editingGroup = GroupEdit(group.id, group.name, group.color ?: GROUP_COLORS.first()) },
                                onMoveLeft = { viewModel.moveGroup(group.id, -1) },
                                onMoveRight = { viewModel.moveGroup(group.id, 1) },
                                onDelete = { deletingGroup = group },
                            )
                        },
                    )
                    RowDivider()
                    val shown = if (lit == null) options else options.filter { it.id != null && it.groupId == lit }
                    shown.forEach { row(it) }
                    if (shown.isEmpty()) {
                        Text(
                            "No one here yet. Use Move to group in a persona\u2019s menu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ButlerTheme.colors.textLow,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        )
                    }
                }
                com.cherry.butler.feature.chats.KeyButton(
                    "New persona",
                    onClick = { onEditPersona("new") },
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
                groupError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.danger, modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 10.dp))
                }

                SettingsSection(title = "Account") {
                    ChoiceRowSignOut(signingOut = signingOut, onClick = viewModel::askSignOut)
                }
            }
        }
    }
}

@Composable
private fun Header(p: ProfileDto, counts: ProfileCountsDto?, avatarUrl: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(url = avatarUrl, name = p.name, size = 96.dp, initialStyle = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${p.userName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (p.isVerified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = ButlerTheme.colors.speech, modifier = Modifier.size(20.dp))
                }
            }
            p.name.takeIf { it.isNotBlank() && it != p.userName }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // Under the header, full width: large text must not squeeze three figures beside a portrait.
    Counts(counts)
}

/** Figures over labels, side by side with a thin divider, as Janitor's follower counts. */
@Composable
private fun Counts(counts: ProfileCountsDto?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            "Characters" to counts?.characterCount,
            "Personas" to counts?.personaCount,
            "Scripts" to counts?.scriptCount,
        ).forEachIndexed { i, (label, value) ->
            if (i > 0) {
                Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(30.dp).background(ButlerTheme.colors.rule))
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = value?.toString() ?: "–",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(label, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * One persona: its picture, its name (the default says so), a tick on the one new chats start
 * as, and making it the default behind its menu.
 */
@Composable
private fun PersonaRow(
    option: PersonaOption,
    selected: Boolean,
    busy: Boolean,
    onMakeDefault: () -> Unit,
    onEdit: () -> Unit,
    onMoveToGroup: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(url = option.avatarUrl, name = option.name, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (option.id == null) {
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.labelSmall,
                        color = ButlerTheme.colors.textMed,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(ButlerTheme.colors.surfaceHigh, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Chosen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp).size(22.dp),
                )
            }
            Box {
                androidx.compose.material3.IconButton(onClick = { menu = true }, enabled = !busy) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${option.name}", tint = ButlerTheme.colors.textLow)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Edit", style = MaterialTheme.typography.titleSmall) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                        onClick = { menu = false; onEdit() },
                    )
                    onMoveToGroup?.let { move ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Move to group", style = MaterialTheme.typography.titleSmall) },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                            onClick = { menu = false; move() },
                        )
                    }
                    if (option.id != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Make default", style = MaterialTheme.typography.titleSmall) },
                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = ButlerTheme.colors.textMed) },
                            onClick = { menu = false; onMakeDefault() },
                        )
                    }
                }
            }
        }
        com.cherry.butler.ui.components.HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(start = 70.dp))
    }
}

/** A group being made ([id] null) or changed; [thenMove] is a persona to put in a new one. */
private data class GroupEdit(val id: String?, val name: String, val color: String, val thenMove: String? = null)

/** Colours offered for a group, Janitor's grey first; the wheel covers the rest. */
private val GROUP_COLORS = listOf("#6b7280", "#EA5A4F", "#F2A65E", "#E8C55A", "#6FCF97", "#6CC4C4", "#7FA6F5", "#C3A8EE", "#F28FAD")

/** The lit group's menu, opened by tapping its chip again: change it, move it, delete it. */
@Composable
private fun GroupMenu(
    open: Boolean,
    onClose: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onEdit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = open,
        onDismissRequest = onClose,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        androidx.compose.material3.DropdownMenuItem(text = { Text("Rename and colour", style = MaterialTheme.typography.titleSmall) }, onClick = { onClose(); onEdit() })
        if (canMoveLeft) androidx.compose.material3.DropdownMenuItem(text = { Text("Move left", style = MaterialTheme.typography.titleSmall) }, onClick = { onClose(); onMoveLeft() })
        if (canMoveRight) androidx.compose.material3.DropdownMenuItem(text = { Text("Move right", style = MaterialTheme.typography.titleSmall) }, onClick = { onClose(); onMoveRight() })
        androidx.compose.material3.DropdownMenuItem(
            text = { Text("Delete group", style = MaterialTheme.typography.titleSmall, color = ButlerTheme.colors.danger) },
            onClick = { onClose(); onDelete() },
        )
    }
}

/** Where a persona goes: any group, no group, or a new one made for it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MoveToGroupSheet(
    persona: PersonaOption,
    groups: List<com.cherry.butler.core.data.remote.dto.PersonaGroupDto>,
    onPick: (String?) -> Unit,
    onNewGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.cherry.butler.core.design.SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = com.cherry.butler.ui.components.SheetScrim,
        dragHandle = { com.cherry.butler.ui.components.SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "Move ${persona.name}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            groups.forEach { g ->
                GroupChoice(name = g.name, color = com.cherry.butler.ui.components.groupColor(g.color), chosen = persona.groupId == g.id) { onPick(g.id) }
            }
            GroupChoice(name = "No group", color = null, chosen = persona.groupId == null || groups.none { it.id == persona.groupId }) { onPick(null) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNewGroup).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("New group", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun GroupChoice(name: String, color: androidx.compose.ui.graphics.Color?, chosen: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.cherry.butler.ui.components.GroupMark(color, Modifier.size(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        if (chosen) Icon(Icons.Rounded.Check, contentDescription = "Here now", tint = MaterialTheme.colorScheme.primary)
    }
}

/** A group's name and colour: a few colours to tap, and the wheel for any other. */
@Composable
private fun GroupDialog(start: GroupEdit, onSave: (name: String, color: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var color by remember { mutableStateOf(start.color) }
    var wheel by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(vertical = 16.dp),
        ) {
            Text(
                if (start.id == null) "New group" else "Group",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            com.cherry.butler.feature.settings.FieldBlock(label = "Name", value = name, onChange = { name = it.take(60) }, placeholder = "Fantasy, Modern, Main…")
            Text("Colour", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                (GROUP_COLORS.take(7) + listOf("wheel")).forEach { hex ->
                    val isWheel = hex == "wheel"
                    val own = isWheel && GROUP_COLORS.none { it.equals(color, ignoreCase = true) }
                    val c = if (isWheel) (if (own) com.cherry.butler.ui.components.groupColor(color) else null) else com.cherry.butler.ui.components.groupColor(hex)
                    val chosen = if (isWheel) own else hex.equals(color, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .then(
                                if (c != null) Modifier.background(c) else Modifier.background(
                                    androidx.compose.ui.graphics.Brush.sweepGradient(
                                        listOf(androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.Yellow, androidx.compose.ui.graphics.Color.Green, androidx.compose.ui.graphics.Color.Cyan, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Magenta, androidx.compose.ui.graphics.Color.Red),
                                    ),
                                ),
                            )
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.rule, androidx.compose.foundation.shape.CircleShape)
                            .clickable { if (isWheel) wheel = true else color = hex },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 11.dp),
                )
                Spacer(Modifier.width(8.dp))
                val ok = name.isNotBlank()
                Text(
                    "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ok) MaterialTheme.colorScheme.onPrimary else ButlerTheme.colors.textLow,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(if (ok) MaterialTheme.colorScheme.primary else ButlerTheme.colors.surfaceHigh)
                        .clickable(enabled = ok) { onSave(name.trim(), color) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                )
            }
        }
    }
    if (wheel) {
        com.cherry.butler.ui.components.ColorPickerSheet(
            title = "Group colour",
            initial = com.cherry.butler.ui.components.groupColor(color) ?: androidx.compose.ui.graphics.Color.Gray,
            recent = emptyList(),
            onPick = { argb -> wheel = false; color = "#%06X".format(argb and 0xFFFFFF) },
            onDismiss = { wheel = false },
        )
    }
}

@Composable
private fun ChoiceRowSignOut(signingOut: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !signingOut, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = ButlerTheme.colors.danger, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = if (signingOut) "Signing out…" else "Sign out",
            style = MaterialTheme.typography.titleMedium,
            color = ButlerTheme.colors.danger,
        )
    }
}

@Composable
private fun HeaderSkeleton() {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBlock(width = 96.dp, height = 96.dp, radius = 18.dp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(width = 160.dp, height = 20.dp)
            SkeletonBlock(width = 100.dp, height = 12.dp)
        }
    }
}
