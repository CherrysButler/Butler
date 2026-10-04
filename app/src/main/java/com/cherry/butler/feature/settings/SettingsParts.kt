package com.cherry.butler.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.cherry.butler.ui.components.card
import com.cherry.butler.ui.components.dropLastPixel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.Motion
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.cherry.butler.ui.components.HairlineRule
import com.cherry.butler.ui.components.hairlineFrame
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.ExperimentalMaterial3Api
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A titled group of rows: a plain title, then the rows together on one rounded card. The
 * divider under the last row is tucked away so the card ends on its own edge.
 */
@Composable
fun SettingsSection(title: String, footnote: String? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ButlerTheme.colors.textMed,
            modifier = Modifier.padding(start = 28.dp, end = 16.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .card(shape = MaterialTheme.shapes.large)
                .dropLastPixel(),
        ) { content() }
        if (footnote != null) {
            Text(
                text = footnote,
                style = MaterialTheme.typography.bodySmall,
                color = ButlerTheme.colors.textLow,
                modifier = Modifier.padding(start = 28.dp, end = 20.dp, top = 8.dp),
            )
        }
    }
}

/** The divider between two rows of a grouped card. */
@Composable
internal fun RowDivider() {
    HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(start = 16.dp))
}

/** A choice among siblings: a radio dot, filled red on the chosen one. */
@Composable
fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy, onClick = onClick)
                .heightIn(min = 60.dp)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ring by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textLow,
                animationSpec = Motion.enter(Motion.SHORT),
                label = "radio-ring",
            )
            val dot by animateFloatAsState(if (selected) 1f else 0f, animationSpec = Motion.enter(Motion.SHORT), label = "radio-dot")
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, ring, CircleShape)
                    .padding(5.dp)
                    .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = if (busy) "Saving…" else subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ButlerTheme.colors.textLow,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
        RowDivider()
    }
}

/** A row that opens another screen. */
@Composable
fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ButlerTheme.colors.textLow)
        }
        RowDivider()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onChange(!checked) }
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.textLow)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = ButlerTheme.colors.textMed,
                    uncheckedTrackColor = ButlerTheme.colors.surfaceHigh,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        RowDivider()
    }
}

/**
 * A number on a range. The thumb moves freely; the value is sent once, when the finger
 * lifts, so a drag is one request rather than fifty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    busy: Boolean = false,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    Column {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    text = if (busy) "Saving…" else format(local),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (local != value) MaterialTheme.colorScheme.primary else ButlerTheme.colors.textMed,
                )
            }
            Slider(
                value = local.coerceIn(range),
                onValueChange = { local = (it / step).roundToInt() * step },
                onValueChangeFinished = { if (local != value) onCommit(local) },
                valueRange = range,
                steps = if (steps <= 200) steps else 0,
                modifier = Modifier.height(32.dp),
                // Slim: a round thumb on a thin line, no stop dots.
                thumb = {
                    Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                },
                track = { state ->
                    val active = MaterialTheme.colorScheme.primary
                    val rest = ButlerTheme.colors.surfaceHigh
                    val span = state.valueRange.endInclusive - state.valueRange.start
                    val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(rest, cornerRadius = r)
                        drawRoundRect(active, size = Size(size.width * fraction, size.height), cornerRadius = r)
                    }
                },
            )
        }
        RowDivider()
    }
}

/** A whole number typed in; sent when the field is left or Done is pressed. */
@Composable
fun NumberRow(title: String, subtitle: String?, value: Int, onCommit: (Int) -> Unit, range: IntRange) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val n = text.toIntOrNull()?.coerceIn(range)
        if (n == null) text = value.toString() else { text = n.toString(); if (n != value) onCommit(n) }
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow)
            }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { t -> text = t.filter { it.isDigit() }.take(7) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    fontFeatureSettings = "tnum",
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier
                    .width(110.dp)
                    .hairlineFrame(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .background(ButlerTheme.colors.surfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .onFocusChanged { f ->
                        if (focused && !f.isFocused) commit()
                        focused = f.isFocused
                    },
            )
        }
        RowDivider()
    }
}

/** A labelled text field, filled and rounded; outlined red only while focused. */
@Composable
fun FieldBlock(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineFrame(
                    when {
                        error != null -> ButlerTheme.colors.danger
                        focused -> MaterialTheme.colorScheme.primary
                        else -> Color.Transparent
                    },
                    MaterialTheme.shapes.medium,
                )
                .background(ButlerTheme.colors.surfaceHigh)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, style = textStyle, color = ButlerTheme.colors.textLow)
                    inner()
                },
            )
        }
        if (error != null) Text(error, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.danger)
    }
}

fun Float.fixed(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

/** Keeps a remembered value in step when the source changes underneath. */
@Composable
fun <T> rememberSynced(source: T): androidx.compose.runtime.MutableState<T> {
    val state = remember { mutableStateOf(source) }
    LaunchedEffect(source) { state.value = source }
    return state
}

/**
 * One chosen value with the rest a tap away: the row shows the choice, the menu drops from
 * it, as wide as the row. [items] draws the menu's own rows.
 */
@Composable
fun DropRow(
    title: String,
    value: String?,
    busy: Boolean = false,
    valueIsWarning: Boolean = false,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Column {
        Box(modifier = Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { open = true }
                    .heightIn(min = 60.dp)
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (value != null || busy) {
                        Text(
                            text = if (busy) "Saving…" else value.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (valueIsWarning && !busy) ButlerTheme.colors.danger else ButlerTheme.colors.textLow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val turn by animateFloatAsState(if (open) 180f else 0f, animationSpec = Motion.enter(Motion.SHORT), label = "drop-chevron")
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = ButlerTheme.colors.textMed,
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                offset = DpOffset(12.dp, 0.dp),
                modifier = Modifier.width(with(density) { width.toDp() } - 24.dp),
            ) { items { open = false } }
        }
        RowDivider()
    }
}

/** A row in a [DropRow]'s menu: a tick on the chosen one, an optional trailing action. */
@Composable
fun DropItem(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = ButlerTheme.colors.textLow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected && trailing == null) Icon(Icons.Rounded.Check, contentDescription = "Chosen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        trailing?.invoke()
    }
}

/** A quiet line between groups of menu rows. */
@Composable
fun DropDivider() {
    HairlineRule(color = ButlerTheme.colors.outlineFaint, modifier = Modifier.padding(vertical = 4.dp))
}
