package com.cherry.butler.feature.chat

import com.cherry.butler.core.data.remote.dto.PronounsDto
import com.cherry.butler.core.markdown.SceneTag
import com.cherry.butler.core.markdown.SceneTags
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.cherry.butler.ui.components.ImageViewer
import com.cherry.butler.ui.components.ExportDialog
import com.cherry.butler.ui.components.ExportFormat
import com.cherry.butler.ui.components.TransferProgressDialog
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import com.cherry.butler.core.design.proseStyle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.cherry.butler.core.config.JanitorConfig
import com.cherry.butler.core.data.local.MessageEntity
import com.cherry.butler.core.data.local.MessageStreamState
import com.cherry.butler.core.data.local.SendJobEntity
import com.cherry.butler.core.data.local.SendJobState
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.PlateLevel
import com.cherry.butler.core.design.PlateText
import com.cherry.butler.core.design.RpText
import com.cherry.butler.core.generation.ChoicesService
import com.cherry.butler.core.generation.SendPipeline
import com.cherry.butler.core.markdown.fillNames
import com.cherry.butler.ui.components.Avatar
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.InlineErrorCard
import com.cherry.butler.ui.components.SkeletonBlock
import com.cherry.butler.ui.components.ditherFade
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.PersonaPickerSheet
import com.cherry.butler.ui.components.hairlineFrame
import com.cherry.butler.core.design.LocalChatStyle
import com.cherry.butler.core.design.Motion
import com.cherry.butler.core.design.ChatStyle
import com.cherry.butler.core.data.PersonaOption
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cherry.butler.ui.components.rememberFlingListState
import com.cherry.butler.ui.components.userMessage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * The backlog. The scene portrait at the top behind a dithered fade; beneath it the log:
 * the character's plate on a rule, then prose; the user's lines right-set in a thin frame;
 * the text window at the bottom. A reply being written grows in place behind a block
 * caret. The last reply can be swiped between its variants or asked for again; any line
 * can be copied, rewritten in place, or cut from with a long press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenModelSettings: () -> Unit = {},
    onOpenCustomize: () -> Unit = {},
    onOpenCharacter: (String) -> Unit = {},
    onOpenChat: (Long) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val providerLabel by viewModel.providerLabel.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val richOn by viewModel.richOn.collectAsStateWithLifecycle()
    val butterTint by viewModel.butterTint.collectAsStateWithLifecycle()
    val richDefault by viewModel.richDefault.collectAsStateWithLifecycle()
    val background by viewModel.chatBackground.collectAsStateWithLifecycle()
    var backgroundOpen by remember { mutableStateOf(false) }
    val transcript by viewModel.shownTranscript.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val fallbackPersona by viewModel.fallbackPersonaName.collectAsStateWithLifecycle()
    val activePersona by viewModel.activePersona.collectAsStateWithLifecycle()
    val personaOptions by viewModel.personaOptions.collectAsStateWithLifecycle()
    var pickingPersona by remember { mutableStateOf(false) }
    val editingId by viewModel.editingId.collectAsStateWithLifecycle()
    val editDraft by viewModel.editDraft.collectAsStateWithLifecycle()
    val editStatus by viewModel.editStatus.collectAsStateWithLifecycle()
    val memoryRun by viewModel.memoryRun.collectAsStateWithLifecycle()
    val replacesHistory by viewModel.replacesHistory.collectAsStateWithLifecycle()
    val autoSummarize by viewModel.autoSummarize.collectAsStateWithLifecycle()
    val summaryEdit by viewModel.summaryEdit.collectAsStateWithLifecycle()
    var memoryOpen by remember { mutableStateOf(false) }
    val summarizing = memoryRun != null && !memoryRun!!.done && memoryRun!!.error == null

    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose { viewModel.onHidden() }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val send: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && viewModel.shouldAskNotifications()) {
            viewModel.notificationsAsked()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.send()
    }

    // Export: pick a format, then where the file goes (the system's own save screen).
    var exporting by remember { mutableStateOf(false) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    if (confirmDeleteChat) {
        DeleteConfirmDialog(
            count = 1,
            title = "Delete chat",
            body = "This chat and all its messages will be deleted from Janitor. This can't be undone.",
            onConfirm = { confirmDeleteChat = false; viewModel.deleteChat(onDeleted = onBack) },
            onDismiss = { confirmDeleteChat = false },
        )
    }
    var exportFormat by remember { mutableStateOf(ExportFormat.Butler) }
    var exportText by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val exportScope = rememberCoroutineScope()
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) {
            exportScope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Couldn't open the file.")
                    }
                }.onFailure { viewModel.reportTransfer("Couldn't save the file. ${it.message.orEmpty()}") }
            }
        }
    }
    if (exporting) {
        ExportDialog(
            onPick = { format ->
                exporting = false
                exportFormat = format
                exportScope.launch {
                    runCatching { viewModel.exportText(format) }
                        .onSuccess { (name, text) -> exportText = text; saveFile.launch(name) }
                        .onFailure { viewModel.reportTransfer("Couldn't export. ${it.userMessage()}") }
                }
            },
            onDismiss = { exporting = false },
        )
    }
    val copying by viewModel.copying.collectAsStateWithLifecycle()
    copying?.let { (done, total) -> TransferProgressDialog("Branching…", done, total) }
    val transferError by viewModel.transferError.collectAsStateWithLifecycle()
    transferError?.let { msg ->
        DeleteConfirmDialog(count = 1, title = "Not done", body = msg, confirmLabel = "OK", onConfirm = viewModel::dismissTransferError, onDismiss = viewModel::dismissTransferError)
    }

    val contextPercent by viewModel.contextPercent.collectAsStateWithLifecycle()
    val choicesState by viewModel.choicesState.collectAsStateWithLifecycle()
    val choicesAvailable by viewModel.choicesAvailable.collectAsStateWithLifecycle()
    var modelsOpen by remember { mutableStateOf(false) }
    // The character's picture, full size, when their face is tapped anywhere on the screen.
    var viewing by remember { mutableStateOf<String?>(null) }
    if (modelsOpen) {
        val ai by viewModel.aiSettingsState.collectAsStateWithLifecycle()
        val modelBusy by viewModel.modelBusy.collectAsStateWithLifecycle()
        val modelError by viewModel.modelError.collectAsStateWithLifecycle()
        ModelSheet(
            settings = ai,
            percent = contextPercent,
            presetFor = viewModel::presetFor,
            busy = modelBusy,
            error = modelError,
            onSelectProxy = viewModel::selectProxy,
            onUseJllm = viewModel::useJllm,
            onSaveModel = viewModel::saveModel,
            onAllSettings = { modelsOpen = false; onOpenModelSettings() },
            onDismiss = { modelsOpen = false },
            jllmAllowance = viewModel.jllmAllowance.collectAsStateWithLifecycle().value,
        )
    }

    val name = chat?.shownName?.ifBlank { null } ?: "Chat"
    val avatarUrl = JanitorConfig.avatarUrl(chat?.characterAvatar)
    viewing?.let { url -> ImageViewer(url = url, name = name, onDismiss = { viewing = null }) }
    val openCharacter = { chat?.characterId?.let(onOpenCharacter); Unit }
    val showPortrait = { avatarUrl?.let { viewing = it }; Unit }
    val personaName = activePersona?.name ?: chat?.personaName ?: fallbackPersona
    val personaPronouns = activePersona?.pronouns

    if (pickingPersona) {
        val personaGroups by viewModel.personaGroups.collectAsStateWithLifecycle()
        PersonaPickerSheet(
            options = personaOptions,
            groups = personaGroups,
            selected = activePersona,
            onPick = { viewModel.pickPersona(it); pickingPersona = false },
            onDismiss = { pickingPersona = false },
        )
    }
    val activeJob = jobs.firstOrNull { it.state in SendJobState.active }
    val streaming = activeJob?.botMessageLocalId?.let { live[it] }
    val busy = activeJob != null && activeJob.state != SendJobState.WAITING_RETRY
    var openThought by remember { mutableStateOf<Long?>(null) }
    var retrying by remember { mutableStateOf(false) }
    if (retrying) {
        RetrySheet(
            onRetry = { guidance -> retrying = false; viewModel.askAgainWith(guidance) },
            onDismiss = { retrying = false },
        )
    }
    var actionsFor by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<MessageEntity?>(null) }
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }

    openThought?.let { id ->
        val row = transcript.firstOrNull { it.localId == id }
        val liveRow = live[id]
        val text = (liveRow?.thinking?.takeIf { it.isNotEmpty() } ?: row?.thinking).orEmpty()
        ThinkingSheet(
            text = text.fillNames(user = personaName, char = name, markUser = true, pronouns = personaPronouns),
            streaming = liveRow != null && liveRow.text.isEmpty(),
            word = ThinkingWords.forSeed(id),
            onDismiss = { openThought = null },
        )
    }

    actionsFor?.let { message ->
        val confirmed = message.serverId != null
        LineActionsSheet(
            preview = message.text.fillNames(user = personaName, char = name, pronouns = personaPronouns),
            canEdit = confirmed && !busy,
            canDelete = confirmed || activeJob == null,
            onCopy = {
                clipboard.setText(AnnotatedString(message.text.fillNames(user = personaName, char = name, pronouns = personaPronouns)))
                actionsFor = null
            },
            onEdit = {
                viewModel.startEdit(message)
                actionsFor = null
            },
            onDelete = {
                confirmDelete = message
                actionsFor = null
            },
            onDismiss = { actionsFor = null },
            rating = message.rating?.toIntOrNull(),
            onRate = if (message.isBot && confirmed) ({ n -> viewModel.rate(message, n) }) else null,
            onBranch = if (confirmed && !busy) {
                { actionsFor = null; viewModel.branch(message, onOpenChat) }
            } else {
                null
            },
        )
    }

    if (memoryOpen) {
        MemorySheet(
            summary = chat?.summary,
            run = memoryRun,
            running = summarizing,
            replacesHistory = replacesHistory,
            autoSummarize = autoSummarize,
            savingEdit = summaryEdit.saving,
            editError = summaryEdit.error?.let { "Not saved. ${it.userMessage()}" },
            onSummarize = viewModel::summarize,
            onCancel = viewModel::cancelSummary,
            onSave = viewModel::saveSummary,
            onReplacesHistory = viewModel::setReplacesHistory,
            onAutoSummarize = viewModel::setAutoSummarize,
            onDismiss = { memoryOpen = false; viewModel.closeMemory() },
        )
    }

    confirmDelete?.let { message ->
        DeleteConfirmDialog(
            count = linesFrom(transcript, message),
            onConfirm = {
                viewModel.deleteFrom(message)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    if (backgroundOpen) {
        com.cherry.butler.feature.settings.BackgroundsSheet(chatId = viewModel.chatId, onDismiss = { backgroundOpen = false })
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    background?.let { com.cherry.butler.ui.components.Backdrop(dim = it.dim, parallax = it.parallax, file = it.file, fileKey = it.key) }
    Scaffold(
        containerColor = if (background != null) Color.Transparent else MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                // One ruled slip, like every other notice in the log.
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .card(color = ButlerTheme.colors.surfaceHigh)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        },
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Avatar(
                        url = avatarUrl, name = name, size = 36.dp, initialStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Show picture", onClick = showPortrait),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "Open character page", onClick = openCharacter)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (summarizing) "Summarizing" else statusCaption(activeJob, streaming, refreshing, transcript.size, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (busy || summarizing) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                        )
                    }
                    providerLabel?.let { label ->
                        ProviderChip(label = label, percent = contextPercent, onClick = { viewModel.openModels(); modelsOpen = true })
                    }
                    ChatMenu(
                        onModelSettings = if (providerLabel == null) onOpenModelSettings else null,
                        onMemory = { memoryOpen = true },
                        onCustomize = onOpenCustomize,
                        onCharacter = openCharacter,
                        onNewChat = { viewModel.startNewChat(onOpenChat) },
                        hasSummary = !chat?.summary.isNullOrBlank(),
                        onExport = { exporting = true },
                        onDelete = { confirmDeleteChat = true },
                        onBackground = { backgroundOpen = true },
                        richOn = richOn,
                        onRich = { viewModel.setRich(!richOn) },
                    )
                }
                // No progress bar: the caption says what is happening, the reply shows it.
                HairlineRule(color = ButlerTheme.colors.outlineFaint)
            }
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraftChanged = viewModel::onDraftChanged,
                busy = busy,
                onSend = send,
                onStop = viewModel::stop,
                persona = activePersona,
                onPickPersona = { pickingPersona = true },
                suggestion = suggestion,
                onWrite = viewModel::writeForMe,
                onStopWriting = viewModel::stopWriting,
                onUndoWrite = viewModel::undoWrite,
                onWriteAgain = viewModel::writeAgain,
                rich = richOn,
                richDefault = Mark.of(richDefault),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                transcript.isEmpty() && refreshing -> TranscriptSkeleton()
                transcript.isEmpty() && refreshError != null -> InlineErrorCard(
                    error = refreshError!!,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.padding(16.dp),
                )
                else -> androidx.compose.runtime.CompositionLocalProvider(com.cherry.butler.core.design.LocalButterTint provides butterTint) { Transcript(
                    messages = transcript,
                    jobs = jobs,
                    live = live,
                    now = now,
                    busy = busy,
                    characterName = name,
                    personaName = personaName,
                    personaPronouns = personaPronouns,
                    characterAvatarUrl = avatarUrl,
                    onAvatarClick = showPortrait,
                    personas = personaOptions,
                    editingId = editingId,
                    editDraft = editDraft,
                    editStatus = editStatus,
                    actions = TranscriptActions(
                        onRetry = viewModel::retryNow,
                        onDismiss = viewModel::dismiss,
                        onContinue = viewModel::continueReply,
                        onAskAgain = viewModel::askAgain,
                        onGuidedRetry = { if (!busy) retrying = true },
                        onRegenerate = viewModel::regenerate,
                        onSelect = viewModel::select,
                        onOpenThought = { openThought = it },
                        onLongPress = { actionsFor = it },
                        onEdit = { if (!busy) viewModel.startEdit(it) },
                        onEditChanged = viewModel::onEditChanged,
                        onEditSave = viewModel::saveEdit,
                        onEditCancel = viewModel::cancelEdit,
                        choices = choicesState,
                        choicesAvailable = choicesAvailable,
                        onChoices = viewModel::requestChoices,
                        onPickChoice = viewModel::sendChoice,
                        onHideChoices = viewModel::hideChoices,
                        onPickIntro = viewModel::pickIntro,
                    ),
                    intros = chat?.intros.orEmpty(),
                    unanswered = activeJob == null && transcript.lastOrNull()?.isBot == false,
                    showScene = background == null,
                ) }
            }
        }
    }
    }
}

/** How many lines a delete from [from] removes: its whole turn and everything after. */
private fun linesFrom(transcript: List<MessageEntity>, from: MessageEntity): Int {
    var start = transcript.indexOfFirst { it.localId == from.localId }
    if (start < 0) return 1
    if (from.isBot) while (start > 0 && transcript[start - 1].isBot) start--
    return transcript.size - start
}

private fun statusCaption(job: SendJobEntity?, live: SendPipeline.LiveReply?, refreshing: Boolean, count: Int, now: Long): String = when {
    // The same playful verb the thinking line shows for this reply.
    job == null -> when {
        refreshing -> "Syncing"
        count == 0 -> "Empty"
        else -> "$count ${if (count == 1) "message" else "messages"}"
    }
    job.state == SendJobState.WAITING_RETRY -> "Retrying in ${((job.nextAttemptAt - now) / 1000).coerceAtLeast(0) + 1}s"
    job.state == SendJobState.QUEUED || job.state == SendJobState.POSTING_USER -> "Sending"
    job.state == SendJobState.GENERATING -> when {
        live == null || (live.text.isEmpty() && live.thinking.isEmpty()) -> "Waiting for the model"
        live.text.isEmpty() -> "${job.botMessageLocalId?.let { ThinkingWords.forSeed(it) } ?: "Thinking"}…"
        else -> "Writing"
    }
    else -> "Saving"
}

/** Every callback the transcript raises, gathered so the composables stay readable. */
private class TranscriptActions(
    val onRetry: (Long) -> Unit,
    val onDismiss: (Long) -> Unit,
    val onContinue: (Long) -> Unit,
    val onAskAgain: () -> Unit,
    val onGuidedRetry: () -> Unit,
    val onRegenerate: (Long) -> Unit,
    val onSelect: (List<MessageEntity>, MessageEntity) -> Unit,
    val onOpenThought: (Long) -> Unit,
    val onLongPress: (MessageEntity) -> Unit,
    val onEdit: (MessageEntity) -> Unit,
    val onEditChanged: (String) -> Unit,
    val onEditSave: () -> Unit,
    val onEditCancel: () -> Unit,
    val choices: ChoicesService.State?,
    val choicesAvailable: Boolean,
    val onChoices: (Long) -> Unit,
    val onPickChoice: (String) -> Unit,
    val onHideChoices: () -> Unit,
    val onPickIntro: (MessageEntity, String) -> Unit,
)

@Composable
private fun Transcript(
    messages: List<MessageEntity>,
    jobs: List<SendJobEntity>,
    live: Map<Long, SendPipeline.LiveReply>,
    now: Long,
    busy: Boolean,
    characterName: String,
    personaName: String?,
    personaPronouns: PronounsDto? = null,
    characterAvatarUrl: String?,
    onAvatarClick: () -> Unit = {},
    editingId: Long,
    editDraft: String,
    editStatus: EditStatus,
    actions: TranscriptActions,
    unanswered: Boolean,
    personas: List<PersonaOption> = emptyList(),
    /** The character's openings; offered on the opening line until the user first answers it. */
    intros: List<String> = emptyList(),
    /** The portrait at the top of the log; off when the chat has a background picture. */
    showScene: Boolean = true,
) {
    val listState = rememberFlingListState(ahead = 2)
    val jobByUser = remember(jobs) { jobs.filter { it.userMessageLocalId != null }.associateBy { it.userMessageLocalId!! } }
    val turns = remember(messages, jobs, live.keys) { foldTurns(messages, jobs, live.keys) }

    // Follow the log: when a new line lands and the reader was at (or within a turn of)
    // the bottom, go to it. A reader who has scrolled up to re-read is left alone.
    // Your own new line always brings you down; a reply starting only does if you were
    // already at the bottom. Someone re-reading is never pulled away.
    val newest = messages.lastOrNull()
    val beforeNewest = messages.getOrNull(messages.lastIndex - 1)
    // A chat never opened here has no rows until its first read lands; meanwhile the list
    // shows only the scene, and keeps it anchored when the rows arrive. So the first rows
    // always put the reader at the newest line, whatever the list thinks it is showing.
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(newest?.localId) {
        if (newest == null) return@LaunchedEffect
        if (!placed) {
            placed = true
            listState.scrollToItem(0)
            return@LaunchedEffect
        }
        val atBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48
        // The reply to a line you just sent is part of the same act: still follow it.
        val replyToYourLine = newest.isBot && newest.serverId == null && beforeNewest?.isBot == false
        if (!newest.isBot || replyToYourLine || atBottom) listState.scrollToItem(0)
    }

    val absorber = remember(listState) { GrowthAbsorber(listState) }
    // A reply grows at its bottom edge, and a reversed list keeps that edge pinned, so the
    // words above would jump up a line at a time. Instead, every frame while a reply is
    // live: growth is absorbed into the scroll offset (nothing on screen moves), and then,
    // only if the reader is following the live edge, the view glides down toward it. The
    // glide stops once the reply's first line reaches the top of the screen, so a long
    // reply holds still and grows below, as ChatGPT does. A reader who scrolled away is
    // never moved; scrolling back to the edge picks the follow up again.
    LaunchedEffect(listState, busy) {
        if (!busy) {
            // Let the last of a reply settle (its final reveal, the switch to plain text).
            withTimeoutOrNull(1_500) { followFrames(listState, absorber) }
            return@LaunchedEffect
        }
        followFrames(listState, absorber)
    }

    // Newest at the bottom, first frame already scrolled there: reverseLayout with the
    // list reversed, which is also what keeps a growing reply from jumping the viewport.
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier
            .fillMaxSize()
            // Drawn with this frame's layout: growth the scroll offset hasn't taken in yet is
            // offset here, so the words don't rise a line and drop back.
            .graphicsLayer { translationY = absorber.unabsorbed().toFloat() },
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        for (i in turns.indices.reversed()) {
            val turn = turns[i]
            val isLast = i == turns.lastIndex
            item(key = turn.key, contentType = if (turn is Turn.Bot) "bot" else "user") {
                when (turn) {
                    is Turn.Bot -> BotTurn(
                        turn = turn,
                        live = live,
                        now = now,
                        isLast = isLast,
                        busy = busy,
                        characterName = characterName,
                        characterAvatarUrl = characterAvatarUrl,
                        onAvatarClick = onAvatarClick,
                        personaName = personaName,
                        personaPronouns = personaPronouns,
                        editing = turn.shown.localId == editingId,
                        editDraft = editDraft,
                        editStatus = editStatus,
                        actions = actions,
                        intros = if (i == 0 && isLast && !turn.answersUser) intros else emptyList(),
                    )
                    is Turn.User -> UserTurn(
                        message = turn.message,
                        persona = personas.firstOrNull { it.id == turn.message.personaId } ?: personas.firstOrNull { it.id == null },
                        job = jobByUser[turn.message.localId],
                        now = now,
                        characterName = characterName,
                        personaName = personaName,
                        personaPronouns = personaPronouns,
                        editing = turn.message.localId == editingId,
                        editDraft = editDraft,
                        editStatus = editStatus,
                        actions = actions,
                        showAskAgain = unanswered && isLast,
                    )
                }
            }
        }
        // The scene: the last item in a reversed list is the top of the log.
        if (showScene) item(key = "scene", contentType = "scene") {
            Scene(url = characterAvatarUrl, name = characterName, onClick = onAvatarClick)
        }
    }
}

/** The character's portrait, fading into the black of the log through a Bayer dither. */
@Composable
private fun Scene(url: String?, name: String, onClick: () -> Unit = {}) {
    if (url == null) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val heightPx = with(density) { SCENE_HEIGHT.roundToPx() }
    val request = remember(url, widthPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(Size(widthPx, heightPx))
            .memoryCacheKey("$url@scene$widthPx")
            .build()
    }
    Box(modifier = Modifier.fillMaxWidth().height(SCENE_HEIGHT).clickable(onClickLabel = "Show picture", onClick = onClick)) {
        AsyncImage(
            model = request,
            contentDescription = "$name's portrait",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .ditherFade(color = MaterialTheme.colorScheme.background, coverage = 0.55f),
        )
    }
}

private val SCENE_HEIGHT = 220.dp

/**
 * One reply, with its variants. The last reply follows a horizontal drag a third of the
 * way, then steps to the neighbouring variant (or asks for a new one past the end) and
 * slides it in from the side it came from; anywhere else the turn stays put.
 */
@Composable
private fun BotTurn(
    turn: Turn.Bot,
    live: Map<Long, SendPipeline.LiveReply>,
    now: Long,
    isLast: Boolean,
    busy: Boolean,
    characterName: String,
    characterAvatarUrl: String?,
    onAvatarClick: () -> Unit = {},
    personaName: String?,
    personaPronouns: PronounsDto? = null,
    editing: Boolean,
    editDraft: String,
    editStatus: EditStatus,
    actions: TranscriptActions,
    intros: List<String> = emptyList(),
) {
    val shown = turn.shown
    val writing = live.containsKey(shown.localId)
    // Butter mode: a reply with butter in it can fold to just those beats, per reply.
    val hasButter = remember(shown.markup, shown.text) { SceneTags.has(shown.markup ?: shown.text, SceneTag.Butter) }
    var skim by androidx.compose.runtime.saveable.rememberSaveable(shown.localId) { mutableStateOf(false) }
    val canSwipe = isLast && turn.answersUser && shown.serverId != null || (isLast && turn.answersUser && turn.variants.size > 1)
    val haptics = LocalHapticFeedback.current
    val byId = turn.variants.associateBy { it.localId }
    val indexOf = { id: Long -> turn.variants.indexOfFirst { it.localId == id } }

    fun step(delta: Int) {
        val target = turn.index + delta
        when {
            target < 0 -> Unit
            target >= turn.variants.size -> actions.onAskAgain()
            else -> actions.onSelect(turn.variants, turn.variants[target])
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .pointerInput(shown.localId, editing) {
                detectTapGestures(onLongPress = {
                    if (!editing && !writing) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onLongPress(shown)
                    }
                })
            }
            // No swipe: a sideways brush while reading changed the reply or asked for a new
            // one. Variants move only by the arrows under it.
    ) {
        val janitor = LocalChatStyle.current == ChatStyle.Janitor
        if (!janitor) {
            // A fixed height: the tools appearing when a reply finishes must not shift the page.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp).height(34.dp)) {
                Avatar(
                    url = characterAvatarUrl, name = characterName, size = 30.dp, initialStyle = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClickLabel = "Show picture", onClick = onAvatarClick),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = characterName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                if (!editing && !writing) LineTools(onMore = { actions.onLongPress(shown) }, butter = if (hasButter) skim else null, onButter = { skim = !skim })
            }
        }

        JanitorFrame(
            enabled = janitor,
            avatarUrl = characterAvatarUrl,
            name = characterName,
            nameColor = MaterialTheme.colorScheme.primary,
            onAvatar = onAvatarClick,
            tools = if (!editing && !writing) ({ LineTools(onMore = { actions.onLongPress(shown) }, butter = if (hasButter) skim else null, onButter = { skim = !skim }) }) else null,
        ) {
        if (editing) {
            LineEditor(
                text = editDraft,
                onChange = actions.onEditChanged,
                status = editStatus,
                onSave = actions.onEditSave,
                onCancel = actions.onEditCancel,
            )
        } else {
            AnimatedContent(
                targetState = shown.localId,
                transitionSpec = {
                    val target = byId[targetState]
                    if (target == null || target.text.isEmpty() || live.containsKey(targetState)) {
                        // A fresh reply replaces the old one at once: sliding a long reply out while
                        // the turn shrank left the page blank for a frame, then snapped it back.
                        EnterTransition.None togetherWith ExitTransition.None using null
                    } else {
                        val dir = sign((indexOf(targetState) - indexOf(initialState)).toFloat()).toInt().takeIf { it != 0 } ?: 1
                        // The height changes in one step; only the words slide.
                        (slideInHorizontally(Motion.enter()) { w -> dir * w / 3 } + fadeIn(Motion.enter())) togetherWith
                            (slideOutHorizontally(Motion.exit()) { w -> -dir * w / 3 } + fadeOut(Motion.exit())) using null
                    }
                },
                label = "variant",
                modifier = Modifier,
            ) { id ->
                val message = byId[id] ?: return@AnimatedContent
                ReplyBody(
                    skim = skim && hasButter,
                    message = message,
                    job = turn.job?.takeIf { it.botMessageLocalId == id },
                    live = live[id],
                    characterName = characterName,
                    personaName = personaName,
                    personaPronouns = personaPronouns,
                    actions = actions,
                )
            }
        }

        // A swipe or continue that is waiting or has failed says so under the reply. A cut-
        // short reply already says so in its own line, with Continue beside it.
        turn.job
            ?.takeUnless { j -> j.state == SendJobState.FAILED && byId[j.botMessageLocalId]?.streamState == MessageStreamState.PARTIAL }
            ?.let { job -> BotJobState(job, now, actions) }

        // The last reply always keeps the bar's room, even while it is being written, so the
        // bar arriving at the end does not push the page (it showed as a jump at the finish).
        if (isLast && !editing) {
            // The opening, before anyone has answered it, steps through the character's intros
            // instead: -1 when the line was edited into none of them.
            val introIndex = if (intros.size > 1) intros.indexOfFirst { it.trim() == shown.text.trim() } else null
            val barReady = !writing && (canSwipe || introIndex != null || shown.serverId != null)
            VariantBar(
                index = introIndex ?: turn.index.coerceAtLeast(0),
                count = if (introIndex != null) intros.size else turn.variants.size,
                canSwipe = canSwipe || introIndex != null,
                busy = busy,
                noun = if (introIndex != null) "intro" else "reply",
                canAskNew = introIndex == null,
                canContinue = !busy && shown.serverId != null && shown.text.isNotEmpty() &&
                    shown.streamState != MessageStreamState.PARTIAL,
                onPrevious = {
                    if (introIndex != null) intros.getOrNull(introIndex - 1)?.let { actions.onPickIntro(shown, it) } else step(-1)
                },
                onNext = {
                    if (introIndex != null) intros.getOrNull(introIndex + 1)?.let { actions.onPickIntro(shown, it) } else step(1)
                },
                onNew = actions.onGuidedRetry,
                onContinue = { actions.onContinue(shown.localId) },
                onChoices = if (actions.choicesAvailable && !busy && shown.serverId != null && actions.choices?.forReply != shown.localId) {
                    { actions.onChoices(shown.localId) }
                } else {
                    null
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer { alpha = if (barReady) 1f else 0f }
                    .then(if (barReady) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }),
            )
            actions.choices?.takeIf { it.forReply == shown.localId && !busy }?.let { state ->
                ChoicesPanel(
                    state = state,
                    onPick = actions.onPickChoice,
                    onAgain = { actions.onChoices(shown.localId) },
                    onHide = actions.onHideChoices,
                )
            }
        }
        }
    }
}

/**
 * Janitor's line layout: the face on the left, the name beside it, the words under the
 * name. Off, it draws its content straight into the parent as before.
 */
@Composable
private fun JanitorFrame(
    enabled: Boolean,
    avatarUrl: String?,
    name: String,
    nameColor: androidx.compose.ui.graphics.Color,
    tools: (@Composable () -> Unit)? = null,
    onAvatar: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(
            url = avatarUrl, name = name, size = 44.dp, initialStyle = MaterialTheme.typography.titleMedium,
            modifier = if (onAvatar != null) Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Show picture", onClick = onAvatar) else Modifier,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                tools?.invoke()
            }
            content()
        }
    }
}


@Composable
private fun ReplyBody(
    skim: Boolean = false,
    message: MessageEntity,
    job: SendJobEntity?,
    live: SendPipeline.LiveReply?,
    characterName: String,
    personaName: String?,
    personaPronouns: PronounsDto? = null,
    actions: TranscriptActions,
) {
    val writing = live != null
    val pending = !writing && message.streamState == MessageStreamState.STREAMING
    val text = remember(message.text, message.markup, live?.text, personaName, personaPronouns, characterName, skim) {
        // The tagged copy when there is one, so the butter shows; folded, only the butter.
        val source = live?.text ?: message.markup ?: message.text
        val shown = if (skim) SceneTags.only(source, SceneTag.Butter) ?: source else source
        shown.fillNames(user = personaName, char = characterName, markUser = true, pronouns = personaPronouns)
    }
    val thinking = live?.thinking?.takeIf { it.isNotEmpty() } ?: message.thinking?.takeIf { it.isNotEmpty() }
    val thinkingOnly = writing && live!!.text.isEmpty() && live.thinking.isNotEmpty()
    val waitingToStart = pending && message.text.isEmpty() && job?.state in WAITING_STATES
    // The reveal runs a moment behind the stream; when the stream ends it finishes its
    // fade instead of the rest popping in at once.
    var revealing by remember(message.localId) { mutableStateOf(false) }
    LaunchedEffect(writing) { if (writing) revealing = true }

    // While a reply is live its height glides instead of stepping a whole line at a time:
    // each frame then grows by a pixel or two, which the scroll rules absorb invisibly.
    // (A step of a full line showed as a one-frame jump of everything above it.)
    // Once live, the row keeps its height animation: switching it off while the animation
    // still trailed the text snapped the last few pixels in one frame.
    var everLive by remember(message.localId) { mutableStateOf(false) }
    if (writing || revealing) everLive = true
    val growing = everLive
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (growing) Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) else Modifier),
    ) {
        if (thinking != null) {
            ThinkingLine(
                text = thinking.fillNames(user = personaName, char = characterName, pronouns = personaPronouns),
                streaming = thinkingOnly,
                word = remember(message.localId) { ThinkingWords.forSeed(message.localId) },
                onOpen = { actions.onOpenThought(message.localId) },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        when {
            thinkingOnly -> Unit // the thought's field carries the activity; a caret would double it
            !writing && message.text.isEmpty() && message.streamState == MessageStreamState.PARTIAL -> Unit
            writing || revealing || waitingToStart || (pending && message.text.isEmpty()) -> StreamingProse(
                text = text,
                style = proseStyle(),
                color = MaterialTheme.colorScheme.onSurface,
                finished = !writing,
                onRevealed = { revealing = false },
            )
            else -> RpText(
                text = text,
                style = proseStyle(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!writing && message.streamState == MessageStreamState.PARTIAL) {
            val empty = message.text.isEmpty()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = when {
                        empty -> "Stopped before the reply"
                        job?.lastError != null -> "Cut short: ${job.lastError}"
                        else -> "Cut short"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ButlerTheme.colors.textLow,
                    modifier = Modifier.weight(1f),
                )
                if (empty) {
                    StateAction(label = "Ask again", onClick = { actions.onRegenerate(message.localId) }, emphasis = true)
                } else {
                    StateAction(label = "Continue", onClick = { actions.onContinue(message.localId) }, emphasis = true)
                }
            }
        }
    }
}

private val WAITING_STATES = setOf(SendJobState.QUEUED, SendJobState.GENERATING)

/** The state of a swipe or continue: a countdown while it retries, a slip once it gives up. */
@Composable
private fun BotJobState(job: SendJobEntity, now: Long, actions: TranscriptActions) {
    when (job.state) {
        SendJobState.WAITING_RETRY -> {
            val seconds = ((job.nextAttemptAt - now) / 1000).coerceAtLeast(0) + 1
            TurnState("${job.lastError ?: "Failed"} · retrying in ${seconds}s") {
                StateAction("Retry now", onClick = { actions.onRetry(job.id) })
                StateAction("Dismiss", onClick = { actions.onDismiss(job.id) })
            }
        }
        SendJobState.FAILED -> if (job.lastError != "Interrupted") {
            ErrataSlip(
                title = job.lastError ?: "Failed",
                body = guidanceFor(job.lastError),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (job.lastErrorRetryable) StateAction("Retry", onClick = { actions.onRetry(job.id) }, emphasis = true)
                StateAction("Dismiss", onClick = { actions.onDismiss(job.id) })
            }
        }
        else -> Unit
    }
}

@Composable
private fun UserTurn(
    message: MessageEntity,
    persona: PersonaOption?,
    job: SendJobEntity?,
    now: Long,
    characterName: String,
    personaName: String?,
    personaPronouns: PronounsDto? = null,
    editing: Boolean,
    editDraft: String,
    editStatus: EditStatus,
    actions: TranscriptActions,
    showAskAgain: Boolean,
) {
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.78f
    val haptics = LocalHapticFeedback.current
    val text = remember(message.text, personaName, personaPronouns, characterName) { message.text.fillNames(user = personaName, char = characterName, markUser = true, pronouns = personaPronouns) }
    val style = LocalChatStyle.current
    val bubbles = style == ChatStyle.Bubbles
    val janitor = style == ChatStyle.Janitor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (janitor) 10.dp else 8.dp),
        horizontalAlignment = if (janitor) Alignment.Start else Alignment.End,
    ) {
        JanitorFrame(
            enabled = janitor,
            avatarUrl = persona?.avatarUrl,
            name = persona?.name ?: personaName.orEmpty(),
            nameColor = MaterialTheme.colorScheme.onSurface,
            tools = if (!editing && message.serverId != null) ({ LineTools(onMore = { actions.onLongPress(message) }) }) else null,
        ) {
        if (editing) {
            LineEditor(
                text = editDraft,
                onChange = actions.onEditChanged,
                status = editStatus,
                onSave = actions.onEditSave,
                onCancel = actions.onEditCancel,
            )
        } else {
        Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .then(if (janitor) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth))
                .then(
                    // The story box by default, filled bubbles or bare Janitor lines when chosen.
                    if (janitor) {
                        Modifier
                    } else if (bubbles) {
                        Modifier.card(
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                            color = if (job?.state == SendJobState.FAILED) ButlerTheme.colors.surfaceElevated.copy(alpha = 0.6f) else ButlerTheme.colors.surfaceHigh,
                        )
                    } else {
                        Modifier.hairlineFrame(if (job?.state == SendJobState.FAILED) ButlerTheme.colors.outlineFaint else ButlerTheme.colors.rule)
                    },
                )
                .pointerInput(message.localId) {
                    detectTapGestures(onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onLongPress(message)
                    })
                },
        ) {
            RpText(
                text = text,
                style = proseStyle(),
                color = if (message.serverId == null && job != null) ButlerTheme.colors.textMed else MaterialTheme.colorScheme.onSurface,
                modifier = when {
                    janitor -> Modifier
                    bubbles -> Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                    else -> Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                },
                paragraphSpacing = 8.dp,
            )
        }
        }
        if (job != null) {
            when (job.state) {
                SendJobState.QUEUED -> TurnState("Queued")
                SendJobState.POSTING_USER -> TurnState("Sending")
                SendJobState.WAITING_RETRY -> {
                    val seconds = ((job.nextAttemptAt - now) / 1000).coerceAtLeast(0) + 1
                    TurnState("${job.lastError ?: "Failed"} · retrying in ${seconds}s") {
                        StateAction("Retry now", onClick = { actions.onRetry(job.id) })
                        StateAction("Dismiss", onClick = { actions.onDismiss(job.id) })
                    }
                }
                SendJobState.FAILED -> ErrataSlip(
                    title = job.lastError ?: "Failed",
                    body = guidanceFor(job.lastError),
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = maxWidth),
                ) {
                    StateAction("Retry", onClick = { actions.onRetry(job.id) }, emphasis = true)
                    StateAction("Dismiss", onClick = { actions.onDismiss(job.id) })
                }
                else -> Unit
            }
        } else if (showAskAgain && message.serverId != null) {
            // A line with no reply after it: the character never answered, or the answer
            // was stopped before a word of it arrived.
            TurnState("No reply") {
                StateAction("Ask again", onClick = actions.onAskAgain, emphasis = true)
            }
        }
        }
        }
    }
}

@Composable
private fun TranscriptSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(width = 30.dp, height = 30.dp, radius = 8.dp)
            Spacer(Modifier.width(10.dp))
            SkeletonBlock(width = 110.dp, height = 14.dp)
        }
        SkeletonBlock(width = 320.dp, height = 14.dp)
        SkeletonBlock(width = 300.dp, height = 14.dp)
        SkeletonBlock(width = 260.dp, height = 14.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(width = 280.dp, height = 14.dp)
        SkeletonBlock(width = 240.dp, height = 14.dp)
    }
}

/** One frame of the live-reply scroll rules, forever (see the caller). */
private suspend fun followFrames(listState: androidx.compose.foundation.lazy.LazyListState, absorber: GrowthAbsorber) {
    var following = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    var wasScrolling = false
    try {
        while (true) {
            withFrameNanos { }
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            if (first == null || listState.firstVisibleItemIndex != 0) {
                absorber.reset()
                following = false
                continue
            }
            val offset = listState.firstVisibleItemScrollOffset
            val scrolling = listState.isScrollInProgress
            if (wasScrolling && !scrolling) following = offset <= FOLLOW_SLOP
            if (scrolling && offset > FOLLOW_SLOP) following = false
            wasScrolling = scrolling

            // Growth into the offset (the draw-time cover in GrowthAbsorber hides the frame
            // it takes), then the glide toward the live edge if following: one request.
            var target = offset
            if (first.key == absorber.key && absorber.size > 0 && first.size > absorber.size) target += first.size - absorber.size
            absorber.key = first.key
            absorber.size = first.size
            if (following && !scrolling && target > 0) target -= glideStep(target)
            if (target != offset) listState.requestScrollToItem(0, target)
        }
    } finally {
        absorber.reset()
    }
}

/**
 * Keeps the words on screen still while the reply under them grows. A reversed list pins a
 * growing item's bottom edge, so its text rises by the new lines' height; the frame loop
 * ([followFrames]) puts that height into the scroll offset, but only on the next frame. In
 * between, [unabsorbed] (read at draw time, from the same frame's layout) offsets the list
 * by exactly that height, so nothing visibly moves; a held finger saw it shake before.
 */
private class GrowthAbsorber(private val state: androidx.compose.foundation.lazy.LazyListState) {
    /** The reply size the scroll offset accounts for; null while no reply is live. */
    var key by androidx.compose.runtime.mutableStateOf<Any?>(null)
    var size by androidx.compose.runtime.mutableIntStateOf(-1)

    fun unabsorbed(): Int {
        val k = key ?: return 0
        if (state.firstVisibleItemIndex != 0) return 0
        val first = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 } ?: return 0
        return if (first.key == k && size > 0 && first.size > size) first.size - size else 0
    }

    fun reset() {
        key = null
        size = -1
    }
}

private const val FOLLOW_SLOP = 24
private fun glideStep(offset: Int): Int = (offset * FOLLOW_EASE).toInt().coerceAtLeast(1)

/** Share of the remaining distance closed per frame: a quick, soft glide. */
private const val FOLLOW_EASE = 0.16f
