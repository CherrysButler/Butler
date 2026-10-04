package com.cherry.butler.feature.profile

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

                SettingsSection(title = "Play as") {
                    options.forEach { option ->
                        PersonaRow(
                            option = option,
                            selected = option.id == current?.id,
                            busy = swap?.stage == SwapFlow.Stage.Running,
                            onMakeDefault = { viewModel.askMakeDefault(option) },
                            onEdit = { onEditPersona(option.id ?: "default") },
                        ) { viewModel.choose(option) }
                    }
                    NewPersonaRow { onEditPersona("new") }
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

/** The last row of the persona card: a new one, starting blank. */
@Composable
private fun NewPersonaRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(ButlerTheme.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text("New persona", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
