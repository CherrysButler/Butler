package com.cherry.butler.feature.lessons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import com.cherry.butler.ui.components.card
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cherry.butler.core.data.Lesson
import com.cherry.butler.core.data.LessonChange
import com.cherry.butler.core.data.LessonsStore
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Pill
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.generation.LessonsLearner
import com.cherry.butler.feature.chats.KeyButton
import com.cherry.butler.ui.components.SheetHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LessonsViewModel @Inject constructor(
    private val store: LessonsStore,
    private val learner: LessonsLearner,
) : ViewModel() {
    val lessons = store.lessons
    val on = store.on
    val state = learner.state

    fun setOn(on: Boolean) = store.setOn(on)
    fun learn(focus: String = "") = learner.learn(focus)
    fun keep(accepted: List<LessonChange>) = learner.keep(accepted)
    fun close() = learner.close()
    fun toggle(lesson: Lesson) = store.update(lesson.id) { it.copy(on = !it.on) }
    fun edit(lesson: Lesson, text: String) = store.update(lesson.id) { it.copy(text = text.trim()) }
    fun delete(lesson: Lesson) = store.delete(lesson.id)
    fun add(text: String) { if (text.isNotBlank()) store.save(store.lessons.value + Lesson(text = text.trim())) }
}

/**
 * /learn in the chat, over the message box: one line while it reads and thinks, then what it
 * proposes, opened with a tap and reviewed in place. Keep or dismiss, and it's gone; nothing
 * is kept until Keep.
 */
@Composable
fun LessonsStrip(modifier: Modifier = Modifier, viewModel: LessonsViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    Box(modifier) {
        var open by remember(s is LessonsLearner.State.Review) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                .fillMaxWidth()
                .card(shape = MaterialTheme.shapes.medium, color = ButlerTheme.colors.surfaceHigh),
        ) {
            val review = s as? LessonsLearner.State.Review
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (review != null) Modifier.clickable(onClickLabel = if (open) "Hide changes" else "Review changes") { open = !open } else Modifier)
                    .heightIn(min = 44.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    when (s) {
                        LessonsLearner.State.Reading -> "Reading your OOC notes…"
                        is LessonsLearner.State.Thinking -> if (s.corrections == 0) "Thinking over what you asked…" else "Learning from ${s.corrections} correction${if (s.corrections == 1) "" else "s"}…"
                        is LessonsLearner.State.Review -> "${s.changes.size} change${if (s.changes.size == 1) "" else "s"} to your notes"
                        is LessonsLearner.State.NothingNew -> if (s.corrections == 0) "Nothing new: no OOC notes since last time" else "Your notes already cover it"
                        is LessonsLearner.State.Failed -> s.message
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (review != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (s is LessonsLearner.State.Failed) ButlerTheme.colors.danger else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (open) 4 else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                if (review != null) {
                    Text(if (open) "Hide" else "Review", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
                } else if (s is LessonsLearner.State.NothingNew || s is LessonsLearner.State.Failed) {
                    IconButton(onClick = viewModel::close) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(18.dp)) }
                }
            }
            if (s is LessonsLearner.State.Reading || s is LessonsLearner.State.Thinking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
            if (review != null && open) {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { Review(review, onKeep = viewModel::keep, onCancel = viewModel::close) }
            }
        }
    }
}

@Composable
private fun Working(what: String) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = ButlerTheme.colors.surfaceHigh)
    Text(what, style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed)
}

@Composable
private fun Review(s: LessonsLearner.State.Review, onKeep: (List<LessonChange>) -> Unit, onCancel: () -> Unit) {
    var ticked by remember(s) { mutableStateOf(s.changes.indices.toSet()) }
    Text(
        "From ${s.corrections} correction${if (s.corrections == 1) "" else "s"}",
        style = MaterialTheme.typography.bodySmall,
        color = ButlerTheme.colors.textLow,
    )
    s.changes.forEachIndexed { i, c ->
        val on = i in ticked
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { ticked = if (on) ticked - i else ticked + i }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = on, onCheckedChange = { ticked = if (on) ticked - i else ticked + i }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                val (icon, tone, label) = when (c.kind) {
                    LessonChange.Kind.Added -> Triple(Icons.Rounded.Add, ButlerTheme.colors.success, "New")
                    LessonChange.Kind.Changed -> Triple(Icons.Rounded.Edit, ButlerTheme.colors.speech, "Changed")
                    LessonChange.Kind.Dropped -> Triple(Icons.Rounded.Remove, ButlerTheme.colors.danger, "Dropped")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone)
                    val scope = (c.new ?: c.old)?.characterName
                    Text(" · ${scope ?: "everywhere"}", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textLow)
                }
                if (c.kind == LessonChange.Kind.Changed) {
                    Text(c.old?.text.orEmpty(), style = MaterialTheme.typography.bodySmall, color = ButlerTheme.colors.textLow, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                }
                Text(
                    (c.new ?: c.old)?.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (c.kind == LessonChange.Kind.Dropped) ButlerTheme.colors.textMed else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (c.kind == LessonChange.Kind.Dropped) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)) {
        KeyButton("Dismiss", onClick = onCancel, modifier = Modifier.weight(1f))
        KeyButton(
            if (ticked.isEmpty()) "Keep none" else "Keep ${ticked.size}",
            onClick = { onKeep(s.changes.filterIndexed { i, _ -> i in ticked }) },
            primary = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BetaMark() {
    Text(
        "BETA",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}


/**
 * Settings › Chat › Lessons (beta): the switch, Learn now, and the notes themselves, each one
 * switchable off, editable, or removable, everywhere ones first then by character.
 */
@Composable
fun LessonsSection(viewModel: LessonsViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var adding by remember { mutableStateOf(false) }
    com.cherry.butler.feature.settings.SettingsSection(
        title = "Lessons",
        icon = Icons.Rounded.School,
        beta = true,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Learn from my corrections", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (lessons.isEmpty()) "No notes yet" else "${lessons.count { it.on }} of ${lessons.size} notes on",
                    style = MaterialTheme.typography.bodySmall,
                    color = ButlerTheme.colors.textLow,
                )
            }
            Switch(checked = on, onCheckedChange = viewModel::setOn)
        }
        AnimatedVisibility(visible = on) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton("Learn now", onClick = { viewModel.learn() }, primary = true, modifier = Modifier.weight(1f))
                    KeyButton("Add a note", onClick = { adding = true }, modifier = Modifier.weight(1f))
                }
                LessonsStrip(viewModel = viewModel)
                val groups = lessons.groupBy { it.characterName?.takeIf { _ -> !it.everywhere } }
                groups.entries.sortedBy { it.key != null }.forEach { (character, list) ->
                    Text(
                        character ?: "Everywhere",
                        style = MaterialTheme.typography.labelMedium,
                        color = ButlerTheme.colors.textLow,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                    )
                    list.forEach { l -> LessonRow(l, onToggle = { viewModel.toggle(l) }, onEdit = { editing = l }, onDelete = { viewModel.delete(l) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    editing?.let { l ->
        NoteDialog(title = "Edit note", start = l.text, onSave = { viewModel.edit(l, it); editing = null }, onDismiss = { editing = null })
    }
    if (adding) NoteDialog(title = "Add a note", start = "", onSave = { viewModel.add(it); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun LessonRow(l: Lesson, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).heightIn(min = 48.dp).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = l.on, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Text(
            l.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (l.on) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = "Delete note", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NoteDialog(title: String, start: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(start) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            com.cherry.butler.feature.settings.FieldBlock(
                label = "One short instruction for the AI",
                value = text,
                onChange = { text = it },
                placeholder = "Never write my character's actions or words.",
                singleLine = false,
                minLines = 2,
            )
        },
        confirmButton = { KeyButton("Save", onClick = { onSave(text) }, enabled = text.isNotBlank(), primary = true) },
        dismissButton = { TextButton(onClick = onDismiss, shape = Pill) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
    )
}

