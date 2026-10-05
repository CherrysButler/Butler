package com.cherry.butler.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.design.ButlerTheme
import com.cherry.butler.core.design.SheetShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Any colour, the classic way: a wheel (hue around it, strength out from the middle), a
 * brightness bar under it, the hex code for typing or pasting, and the last colours picked.
 * The old colour sits beside the new one until "Use".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Color,
    recent: List<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    var hex by remember { mutableStateOf(hexOf(initial)) }
    fun take(c: Color) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hex = hexOf(c)
    }
    fun moved() { hex = hexOf(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = SheetScrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                // Before → after.
                Box(Modifier.size(28.dp).clip(CircleShape).background(initial).border(1.dp, ButlerTheme.colors.rule, CircleShape))
                Text("→", style = MaterialTheme.typography.titleMedium, color = ButlerTheme.colors.textLow, modifier = Modifier.padding(horizontal = 6.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(color).border(1.dp, ButlerTheme.colors.rule, CircleShape))
            }

            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                HueWheel(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { h, s -> hue = h; sat = s; moved() },
                )
            }

            Text("Brightness", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed)
            BrightnessBar(hue = hue, sat = sat, value = value, onChange = { value = it; moved() }, modifier = Modifier.padding(top = 8.dp))

            Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hex", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.width(44.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(ButlerTheme.colors.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = hex,
                        onValueChange = { typed ->
                            val clean = typed.uppercase().filter { it in "#0123456789ABCDEF" }.let { if (it.startsWith("#")) it else "#$it" }.take(7)
                            hex = clean
                            parseHex(clean)?.let { c ->
                                val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (recent.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelMedium, color = ButlerTheme.colors.textMed, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.forEach { argb ->
                        val c = Color(argb)
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (c.toArgb() == color.toArgb()) 2.dp else 1.dp, if (c.toArgb() == color.toArgb()) MaterialTheme.colorScheme.onSurface else ButlerTheme.colors.rule, CircleShape)
                                .clickable(onClickLabel = hexOf(c)) { take(c) },
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onPick(color.toArgb().toLong() and 0xFFFFFFFFL) }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) { Text("Use", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}

/** Hue around the ring, strength from the middle out, shown at the current brightness. */
@Composable
private fun HueWheel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    fun pick(pos: Offset, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = min(cx, cy)
        val dx = pos.x - cx
        val dy = pos.y - cy
        val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
        onChange(angle, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    val sweep = remember {
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }
    Canvas(
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { pick(it, size.width.toFloat(), size.height.toFloat()) }) { change, _ ->
                    change.consume()
                    pick(change.position, size.width.toFloat(), size.height.toFloat())
                }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(sweep, radius = r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center = center, radius = r), radius = r)
        drawCircle(Color.Black.copy(alpha = 1f - value), radius = r)
        val a = Math.toRadians(hue.toDouble())
        val at = Offset(center.x + (cos(a) * sat * r).toFloat(), center.y + (sin(a) * sat * r).toFloat())
        val shown = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
        drawCircle(shown, radius = 13.dp.toPx(), center = at)
        drawCircle(Color.White, radius = 13.dp.toPx(), center = at, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 14.5.dp.toPx(), center = at, style = Stroke(1.dp.toPx()))
    }
}

/** Black to the colour at full brightness; drag or tap. */
@Composable
private fun BrightnessBar(hue: Float, sat: Float, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val full = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onChange((it.x / size.width).coerceIn(0f, 1f)) }) { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.Black, full)))
        val x = (value * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
        val ring = if (Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))).luminance() > 0.5f) Color.Black else Color.White
        drawCircle(ring, radius = size.height / 2f - 3.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
    }
}

private fun hexOf(c: Color): String = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun parseHex(s: String): Color? {
    val digits = s.removePrefix("#")
    if (digits.length != 6) return null
    return digits.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
