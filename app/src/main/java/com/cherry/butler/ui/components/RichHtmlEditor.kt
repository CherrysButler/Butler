package com.cherry.butler.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cherry.butler.core.data.PictureUpload
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.markdown.EditorHtml
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import com.mohamedrejeb.richeditor.model.LocalImageLoader
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.launch

/**
 * The website's editor, as you see it: bold is bold while you write, a heading is a heading,
 * a picture sits in the text. Over it, the website's toolbar in its order: paragraph or
 * heading, bold, italic, underline, highlight, text colour, alignment, a picture, a link.
 * The keys never take the keyboard away from the text.
 *
 * [html] is the website's HTML, and [onChange] gets it back in the website's shape after
 * each edit (see [EditorHtml]). [uploadPicture] puts a picture in the media library and waits
 * for Janitor's approval; null leaves the picture key out.
 */
@OptIn(ExperimentalRichTextApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RichHtmlEditor(
    label: String,
    html: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int = 4,
    uploadPicture: (suspend (Uri, (PictureUpload.MediaStage) -> Unit) -> PictureUpload.MediaPicture)? = null,
) {
    val state = rememberRichTextState()
    val focus = remember { FocusRequester() }
    val emit by rememberUpdatedState(onChange)

    // What was loaded, and the same thing as the editor writes it back: until the writing
    // differs from that, the original HTML stands, so opening and closing changes nothing.
    var original by remember { mutableStateOf<String?>(null) }
    var baseline by remember { mutableStateOf("") }
    var lastSent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(html) {
        if (html == lastSent) return@LaunchedEffect
        state.setHtml(EditorHtml.toEditor(html))
        original = html
        baseline = EditorHtml.toWebsite(state.toHtml())
        lastSent = html
    }
    LaunchedEffect(state) {
        snapshotFlow { state.annotatedString }.collect {
            if (original == null) return@collect
            val written = EditorHtml.toWebsite(state.toHtml())
            val out = if (written == baseline) original!! else written
            if (out != lastSent) {
                lastSent = out
                emit(out)
            }
        }
    }

    var colouring by remember { mutableStateOf<String?>(null) }
    var linking by remember { mutableStateOf(false) }
    // A picture on its way in: where it goes, how far it has got, and why it stopped if it did.
    var uploadStage by remember { mutableStateOf<PictureUpload.MediaStage?>(null) }
    var uploadFailure by remember { mutableStateOf<String?>(null) }
    val uploading = uploadStage != null
    val scope = rememberCoroutineScope()
    fun back() = runCatching { focus.requestFocus() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val up = uploadPicture
        if (uri == null || up == null) { back(); return@rememberLauncherForActivityResult }
        // Where the cursor is now; the pop-up keeps the text still until the picture is in.
        val at = state.selection.max
        uploadFailure = null
        uploadStage = PictureUpload.MediaStage.Uploading
        scope.launch {
            runCatching { up(uri) { s -> uploadStage = s } }
                .onSuccess { p ->
                    val w = p.width.coerceAtMost(MAX_PICTURE_WIDTH)
                    val h = if (p.width > 0) p.height * w / p.width else p.height
                    // In at the cursor: a plain marker put there and swapped for the picture, all in one go.
                    state.selection = TextRange(at.coerceAtMost(state.annotatedString.text.length))
                    state.addTextAfterSelection(PICTURE_MARK)
                    val withPicture = EditorHtml.toWebsite(state.toHtml()).replace(PICTURE_MARK, """<img src="${p.url}" width="$w" height="$h">""")
                    state.setHtml(EditorHtml.toEditor(withPicture))
                    kotlinx.coroutines.delay(DONE_SHOWN_MS)
                    uploadStage = null
                    back()
                }
                .onFailure { uploadFailure = it.userMessage() }
        }
    }
    uploadStage?.let { stage ->
        PictureUploadDialog(stage = stage, failure = uploadFailure, onClose = { uploadStage = null; uploadFailure = null; back() })
    }

    val span = state.currentSpanStyle
    val align = state.currentParagraphStyle.textAlign
    val level = headingAt(state)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeadingKey(level, enabled, onPicked = ::back) { n -> setHeading(state, n) }
            Gap()
            ToolKey(Icons.Rounded.FormatBold, "Bold", enabled, on = span.fontWeight == FontWeight.Bold) { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
            ToolKey(Icons.Rounded.FormatItalic, "Italic", enabled, on = span.fontStyle == FontStyle.Italic) { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
            ToolKey(Icons.Rounded.FormatUnderlined, "Underline", enabled, on = span.textDecoration?.contains(TextDecoration.Underline) == true) {
                state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
            }
            ToolKey(Icons.Rounded.BorderColor, "Highlight", enabled, on = span.background.isSpecified()) { colouring = "mark" }
            ToolKey(Icons.Rounded.FormatColorText, "Text colour", enabled, on = span.color.isSpecified()) { colouring = "text" }
            Gap()
            ToolKey(Icons.AutoMirrored.Rounded.FormatAlignLeft, "Align left", enabled, on = align == TextAlign.Left || align == TextAlign.Start) { setAlign(state, TextAlign.Left) }
            ToolKey(Icons.Rounded.FormatAlignCenter, "Centre", enabled, on = align == TextAlign.Center) { setAlign(state, TextAlign.Center) }
            ToolKey(Icons.AutoMirrored.Rounded.FormatAlignRight, "Align right", enabled, on = align == TextAlign.Right || align == TextAlign.End) { setAlign(state, TextAlign.Right) }
            ToolKey(Icons.Rounded.FormatAlignJustify, "Justify", enabled, on = align == TextAlign.Justify) { setAlign(state, TextAlign.Justify) }
            Gap()
            if (uploadPicture != null) {
                ToolKey(Icons.Rounded.Image, "Picture", enabled && !uploading) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            ToolKey(Icons.Rounded.Link, "Link", enabled, on = state.isLink) { linking = true }
        }
        CompositionLocalProvider(LocalImageLoader provides CoilPictures) {
            OutlinedRichTextEditor(
                state = state,
                enabled = enabled,
                minLines = minLines,
                placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = ButlerTheme.colors.textLow) }) else null,
                textStyle = com.cherry.butler.core.design.proseOf(MaterialTheme.typography.bodyMedium),
                shape = MaterialTheme.shapes.medium,
                colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
                    containerColor = ButlerTheme.colors.surfaceHigh,
                    unfocusedBorderColor = ButlerTheme.colors.outlineFaint,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(focus),
            )
        }
    }

    colouring?.let { which ->
        ColorPickerSheet(
            title = if (which == "mark") "Highlight" else "Text colour",
            initial = if (which == "mark") Color(0xFF9D0808) else MaterialTheme.colorScheme.primary,
            recent = emptyList(),
            onPick = { argb ->
                colouring = null
                val c = Color(argb.toInt() or 0xFF000000.toInt())
                if (which == "mark") {
                    state.removeSpanStyle(SpanStyle(background = state.currentSpanStyle.background))
                    state.addSpanStyle(SpanStyle(background = c))
                } else {
                    state.removeSpanStyle(SpanStyle(color = state.currentSpanStyle.color))
                    state.addSpanStyle(SpanStyle(color = c))
                }
                back()
            },
            onDismiss = { colouring = null; back() },
        )
    }
    if (linking) {
        LinkDialog(
            selected = state.selection.let { if (it.collapsed) "" else state.annotatedString.text.substring(it.min, it.max) },
            onLink = { url ->
                linking = false
                if (state.selection.collapsed) state.addLink(text = url, url = url) else state.addLinkToSelection(url)
                back()
            },
            onDismiss = { linking = false; back() },
        )
    }
}

/** The heading sizes the editor writes as `<h1>`…`<h6>` (its own, rc11). */
private val HEADING_SIZES = listOf(2f, 1.5f, 1.17f, 1.12f, 0.83f, 0.75f)

private fun headingStyle(level: Int) = SpanStyle(fontSize = HEADING_SIZES[level - 1].em, fontWeight = FontWeight.Bold)

/** The paragraph the caret is in: the editor gives each paragraph its own style range. */
private fun paragraphAt(state: com.mohamedrejeb.richeditor.model.RichTextState): TextRange? {
    val at = state.selection.min
    val p = state.annotatedString.paragraphStyles.firstOrNull { at in it.start..it.end } ?: return null
    return TextRange(p.start, p.end).takeUnless { it.collapsed }
}

private fun headingAt(state: com.mohamedrejeb.richeditor.model.RichTextState): Int {
    val size = state.currentSpanStyle.fontSize
    if (!size.isEm) return 0
    return HEADING_SIZES.indexOfFirst { kotlin.math.abs(it - size.value) < 0.01f } + 1
}

/** Heading [level] for the whole paragraph the caret is in; 0 makes it a paragraph again. */
@OptIn(ExperimentalRichTextApi::class)
private fun setHeading(state: com.mohamedrejeb.richeditor.model.RichTextState, level: Int) {
    val range = paragraphAt(state) ?: return
    val now = headingAt(state)
    if (now > 0) state.removeSpanStyle(headingStyle(now), range)
    if (level > 0) state.addSpanStyle(headingStyle(level), range)
}

private fun setAlign(state: com.mohamedrejeb.richeditor.model.RichTextState, align: TextAlign) {
    state.addParagraphStyle(ParagraphStyle(textAlign = align))
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified && this != Color.Transparent

/** The editor's pictures, drawn by Coil as Butler's others are. */
@OptIn(ExperimentalRichTextApi::class)
private object CoilPictures : ImageLoader {
    @Composable
    override fun load(model: Any): ImageData = ImageData(painter = coil.compose.rememberAsyncImagePainter(model))
}

/** Paragraph, or Heading 1 to 6, for the paragraph the caret is in. */
@Composable
private fun HeadingKey(level: Int, enabled: Boolean, onPicked: () -> Unit, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .focusProperties { canFocus = false }
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, ButlerTheme.colors.rule, MaterialTheme.shapes.small)
                .clickable(enabled = enabled) { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (level == 0) "Paragraph" else "Heading $level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false; onPicked() },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            (0..6).forEach { n ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (n == 0) "Paragraph" else "Heading $n",
                            style = if (n == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                            fontWeight = if (n == level) FontWeight.Bold else null,
                        )
                    },
                    onClick = { open = false; onPick(n); onPicked() },
                )
            }
        }
    }
}

/** A toolbar key: lit while what it does is on where the caret is. It never takes the focus. */
@Composable
private fun ToolKey(icon: ImageVector, label: String, enabled: Boolean, on: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .focusProperties { canFocus = false }
            .clip(MaterialTheme.shapes.small)
            .then(if (on) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) else Modifier)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else ButlerTheme.colors.rule, MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (!enabled) ButlerTheme.colors.textLow else if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Gap() = Spacer(Modifier.width(6.dp).height(1.dp))

/** A link's address; the selected words become the link, or the address itself when none are. */
@Composable
private fun LinkDialog(selected: String, onLink: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("https://") }
    val ok = url.trim().let { it.startsWith("http://") || it.startsWith("https://") } && url.trim().length > 8
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a link") },
        text = {
            Column {
                if (selected.isNotBlank()) {
                    Text("“$selected” links to:", style = MaterialTheme.typography.bodyMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(bottom = 8.dp))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onLink(url.trim().replace("\"", "%22")) }, enabled = ok) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** About the website editor's own width, so a picture added here sits as it would there. */
private const val MAX_PICTURE_WIDTH = 400

/** Swapped for the picture the moment it's in; plain letters, so the editor's HTML keeps them as they are. */
private const val PICTURE_MARK = "BUTLERPICTURE7F3A2C"

/** How long Done stays up before the pop-up closes. */
private const val DONE_SHOWN_MS = 700L
