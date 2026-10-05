package com.cherry.butler.feature.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.cherry.butler.core.data.ChatBackgroundChoice
import com.cherry.butler.core.data.ChatBackgrounds
import com.cherry.butler.core.data.SavedBackground
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.SheetShape
import com.cherry.butler.core.design.softBlur
import com.cherry.butler.ui.components.Backdrop
import com.cherry.butler.ui.components.SheetHandle
import com.cherry.butler.ui.components.SheetScrim
import com.cherry.butler.ui.components.card
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackgroundsViewModel @Inject constructor(private val store: ChatBackgrounds) : ViewModel() {
    val library: StateFlow<List<SavedBackground>> = store.library
    val globalId: StateFlow<String?> = store.globalId

    fun choice(chatId: Long?): Flow<ChatBackgroundChoice> = chatId?.let(store::choice) ?: flowOf(ChatBackgroundChoice.Default)

    fun shownFile(id: String) = store.shownFile(id)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun preview(uri: Uri): Bitmap = store.previewOf(uri)
    suspend fun preview(id: String): Bitmap = store.previewOf(id)

    /** Adds the picture and puts it to use where it was added from. */
    fun add(uri: Uri, blur: Float, dim: Float, parallax: Boolean, chatId: Long?) {
        viewModelScope.launch {
            runCatching { store.add(uri, blur, dim, parallax) }
                .onSuccess { id -> use(id, chatId); _error.value = null }
                .onFailure { _error.value = "Couldn't use that picture." }
        }
    }

    fun update(id: String, blur: Float, dim: Float, parallax: Boolean) {
        viewModelScope.launch {
            runCatching { store.update(id, blur, dim, parallax) }
                .onFailure { _error.value = "Couldn't save that change." }
        }
    }

    fun use(id: String, chatId: Long?) {
        if (chatId == null) store.setGlobal(id) else store.setChat(chatId, ChatBackgroundChoice.Own(id))
    }

    fun useDefault(chatId: Long) = store.setChat(chatId, ChatBackgroundChoice.Default)

    fun useNone(chatId: Long?) {
        if (chatId == null) store.setGlobal(null) else store.setChat(chatId, ChatBackgroundChoice.None)
    }

    fun delete(id: String) = store.delete(id)
}

/** What the editor is working on: a picture just picked, or one already saved. */
private sealed interface Editing {
    data class New(val uri: Uri) : Editing
    data class Saved(val item: SavedBackground) : Editing
}

/**
 * The backgrounds library, opened for every chat ([chatId] null, from Settings) or for one
 * chat. Every picture ever set is here to use again, change, or delete; a new one goes
 * through the full-screen editor before it is kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundsSheet(chatId: Long?, onDismiss: () -> Unit, viewModel: BackgroundsViewModel = hiltViewModel()) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val globalId by viewModel.globalId.collectAsStateWithLifecycle()
    val choice by remember(chatId) { viewModel.choice(chatId) }.collectAsStateWithLifecycle(ChatBackgroundChoice.Default)
    val error by viewModel.error.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) editing = Editing.New(uri)
    }

    // The one in use here: this chat's own, or (for Settings, or a chat on the default) every chat's.
    val inUse: String? = when (val c = choice) {
        is ChatBackgroundChoice.Own -> c.id
        ChatBackgroundChoice.None -> null
        ChatBackgroundChoice.Default -> if (chatId == null || globalId != null) globalId else null
    }
    val noneHere = if (chatId == null) globalId == null else choice == ChatBackgroundChoice.None

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Background", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                if (chatId == null) "Behind every chat. A chat can still pick its own from its menu." else "For this chat only.",
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (chatId != null) {
                    Option(
                        label = if (globalId != null) "Same as every chat" else "Default (none)",
                        selected = choice == ChatBackgroundChoice.Default,
                        onClick = { viewModel.useDefault(chatId) },
                    )
                }
                Option(label = "None", selected = noneHere, onClick = { viewModel.useNone(chatId) })
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.danger, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            }
            // Three to a row, the shape of a phone; the first is always "add".
            val tiles: List<SavedBackground?> = listOf<SavedBackground?>(null) + library
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            val m = Modifier.weight(1f)
                            if (item == null) {
                                AddTile(m) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            } else {
                                SavedTile(
                                    item = item,
                                    file = viewModel.shownFile(item.id),
                                    selected = item.id == inUse,
                                    everyChat = chatId != null && item.id == globalId,
                                    onUse = { viewModel.use(item.id, chatId) },
                                    onEdit = { editing = Editing.Saved(item) },
                                    onDelete = { viewModel.delete(item.id) },
                                    modifier = m,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        val start = (e as? Editing.Saved)?.item
        BackgroundEditor(
            load = { if (e is Editing.New) viewModel.preview(e.uri) else viewModel.preview((e as Editing.Saved).item.id) },
            startBlur = start?.blur ?: 0f,
            startDim = start?.dim ?: ChatBackgrounds.DEFAULT_DIM,
            startParallax = start?.parallax ?: true,
            saveLabel = if (e is Editing.New) "Use" else "Save",
            bars = bars,
            onSave = { blur, dim, parallax ->
                editing = null
                when (e) {
                    is Editing.New -> viewModel.add(e.uri, blur, dim, parallax, chatId)
                    is Editing.Saved -> viewModel.update(e.item.id, blur, dim, parallax)
                }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else ButlerTheme.colors.surfaceHigh)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.cardOutline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(MaterialTheme.shapes.medium)
            .background(ButlerTheme.colors.surfaceHigh)
            .border(1.dp, ButlerTheme.colors.cardOutline, MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Add a picture", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text("Add picture", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** A saved picture: tap to use it here; ⋯ to change it or delete it. */
@Composable
private fun SavedTile(
    item: SavedBackground,
    file: java.io.File,
    selected: Boolean,
    everyChat: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val request = remember(item.shownKey) {
        ImageRequest.Builder(context).data(file).memoryCacheKey("${item.shownKey}@thumb").size(360).diskCachePolicy(CachePolicy.DISABLED).build()
    }
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(MaterialTheme.shapes.medium)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.cardOutline, MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Use this background", onClick = onUse),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = "In use", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
        } else if (everyChat) {
            Text(
                "Every chat",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(30.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .clickable(onClickLabel = "More") { menu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                DropdownMenuItem(text = { Text("Edit", style = MaterialTheme.typography.titleSmall) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Delete", style = MaterialTheme.typography.titleSmall, color = ButlerTheme.colors.danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** Which edge slider is out, if any. */
private enum class Panel { None, Blur, Dim }

/**
 * The picture full screen, drawn exactly as a chat will draw it, with a few sample lines on
 * top. Three keys at the bottom: blur (left slider), drift (on/off), dim (right slider).
 * Blur follows the finger on a small copy and is redone at full size when it lifts.
 * [bars] are the screen's system bars, measured outside this window: a dialog window does
 * not always get them, and without them the keys sat under the navigation bar.
 */
@Composable
private fun BackgroundEditor(
    load: suspend () -> Bitmap,
    startBlur: Float,
    startDim: Float,
    startParallax: Boolean,
    saveLabel: String,
    bars: PaddingValues,
    onSave: (blur: Float, dim: Float, parallax: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var small by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var blur by remember { mutableFloatStateOf(startBlur) }
    var dim by remember { mutableFloatStateOf(startDim) }
    var parallax by remember { mutableStateOf(startParallax) }
    var panel by remember { mutableStateOf(Panel.None) }
    var dragging by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        runCatching { load() }
            .onSuccess { bmp ->
                source = bmp
                small = withContext(Dispatchers.Default) {
                    val f = 360f / maxOf(bmp.width, bmp.height)
                    if (f >= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
                }
            }
            .onFailure { failed = true }
    }
    // While dragging, the small copy keeps up with the finger; at rest, the full one is redone.
    LaunchedEffect(source, small) {
        val full = source ?: return@LaunchedEffect
        val quick = small ?: full
        snapshotFlow { blur to dragging }.collectLatest { (b, d) ->
            val from = if (d) quick else full
            shown = withContext(Dispatchers.Default) { from.softBlur(b).asImageBitmap() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures { panel = Panel.None } },
        ) {
            shown?.let { Backdrop(dim = dim, parallax = parallax, image = it) }

            when {
                failed -> Text("Couldn't open that picture.", color = ButlerTheme.colors.textLow, modifier = Modifier.align(Alignment.Center))
                source == null -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                else -> SampleChat(Modifier.align(Alignment.Center).padding(horizontal = 56.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = bars.calculateTopPadding())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarKey("Cancel", emphasis = false, onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                BarKey(saveLabel, emphasis = true, enabled = source != null) { onSave(blur, dim, parallax) }
            }

            if (panel == Panel.Blur) {
                EdgeSlider(
                    value = blur,
                    max = 1f,
                    label = if (blur <= 0f) "Off" else "${(blur * 100).toInt()}%",
                    onChange = { blur = it },
                    onDragging = { dragging = it },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                )
            }
            if (panel == Panel.Dim) {
                EdgeSlider(
                    value = dim,
                    max = ChatBackgrounds.MAX_DIM,
                    label = "${(dim * 100).toInt()}%",
                    onChange = { dim = it },
                    onDragging = { },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bars.calculateBottomPadding() + 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                ToolKey(
                    icon = Icons.Rounded.WaterDrop,
                    label = if (blur <= 0f) "Blur" else "Blur ${(blur * 100).toInt()}%",
                    on = blur > 0f,
                    open = panel == Panel.Blur,
                ) {
                    panel = if (panel == Panel.Blur) Panel.None else {
                        if (blur <= 0f) blur = 0.25f
                        Panel.Blur
                    }
                }
                ToolKey(icon = Icons.Rounded.Animation, label = if (parallax) "Drift on" else "Drift off", on = parallax, open = false) {
                    parallax = !parallax
                    panel = Panel.None
                }
                ToolKey(
                    icon = Icons.Rounded.BrightnessMedium,
                    label = "Dim ${(dim * 100).toInt()}%",
                    on = dim > 0f,
                    open = panel == Panel.Dim,
                ) { panel = if (panel == Panel.Dim) Panel.None else Panel.Dim }
            }
        }
    }
}

/**
 * A round key on the picture: open (its slider is out) fills red, on tints the icon red,
 * off stays plain. The name sits under it on a dark slip so it reads on any picture.
 */
@Composable
private fun ToolKey(icon: ImageVector, label: String, on: Boolean, open: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (open) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = if (open) 0f else 0.18f), CircleShape)
                .clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    open -> MaterialTheme.colorScheme.onPrimary
                    on -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .padding(top = 6.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * A tall slider on the screen's edge: drag up for more, down for less, or tap a height.
 * The fill rises from the bottom; the value sits above it.
 */
@Composable
private fun EdgeSlider(
    value: Float,
    max: Float,
    label: String,
    onChange: (Float) -> Unit,
    onDragging: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = (value / max).coerceIn(0f, 1f)
    fun at(y: Float, height: Int) = ((1f - y / height).coerceIn(0f, 1f) * max * 100f).roundToInt() / 100f
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(260.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                .pointerInput(max) {
                    detectTapGestures { pos -> onChange(at(pos.y, size.height)) }
                }
                .pointerInput(max) {
                    detectVerticalDragGestures(
                        onDragStart = { pos -> onDragging(true); onChange(at(pos.y, size.height)) },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            onChange(at(change.position.y, size.height))
                        },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
            )
        }
    }
}

/** Two made-up turns, to judge the picture against real text. */
@Composable
private fun SampleChat(modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SampleLine("Character", "*She looks up from her book, a small smile tugging at her lips.* \"You're late again. I was starting to think you'd forgotten.\"", MaterialTheme.colorScheme.primary)
        SampleLine("You", "*I set the coffee down beside her.* \"Traffic. I brought this as an apology.\"", ButlerTheme.colors.textMed)
    }
}

@Composable
private fun SampleLine(name: String, text: String, nameColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = nameColor)
        Text(styled(text, ButlerTheme.colors.textMed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
    }
}

/** `*actions*` in italics and a quieter colour, the way a chat sets them. */
private fun styled(text: String, actionColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var italic = false
    text.split('*').forEachIndexed { i, part ->
        if (i > 0) italic = !italic
        if (italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor)) { append(part) } else append(part)
    }
}

@Composable
private fun BarKey(label: String, emphasis: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (emphasis && enabled) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> ButlerTheme.colors.textLow
                emphasis -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
