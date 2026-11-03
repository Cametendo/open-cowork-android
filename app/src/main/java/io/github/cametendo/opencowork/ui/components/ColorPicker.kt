package io.github.cametendo.opencowork.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.cametendo.opencowork.ui.theme.contentColorFor
import kotlin.math.roundToInt

private val Presets = listOf(
    0xFF1D5FD1, 0xFF2563EB, 0xFF3B82F6, 0xFF60A5FA, 0xFF93C5FD, 0xFF0EA5E9,
    0xFF0A1424, 0xFF0F1C31, 0xFF13223A, 0xFF1E3A5F, 0xFFF2F7FF, 0xFFFFFFFF,
    0xFF0D9488, 0xFF16A34A, 0xFF7C3AED, 0xFFC026D3, 0xFFDB2777, 0xFFE11D48,
    0xFFEA580C, 0xFFD97706, 0xFF475569, 0xFF1F2937, 0xFF111111, 0xFFF5F5F4,
).map { Color(it) }

private fun Color.toHsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(toArgb(), it) }
private fun hsv(h: Float, s: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

fun parseHex(text: String): Color? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 && t.length != 8) return null
    return t.toLongOrNull(16)?.let { Color(if (t.length == 6) 0xFF000000 or it else it) }
}

/**
 * Pick a color with presets, hue/saturation/brightness sliders and a hex field.
 * [onPick] receives null when the user resets to the default.
 */
@Composable
fun ColorPickerDialog(title: String, initial: Color, default: Color, onDismiss: () -> Unit, onPick: (Color?) -> Unit) {
    val start = remember { initial.toHsv() }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    val color = hsv(h, s, v)
    var hexText by remember { mutableStateOf(initial.hex()) }

    fun setColor(c: Color) {
        val x = c.toHsv()
        h = x[0]; s = x[1]; v = x[2]
        hexText = c.hex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(color.hex(), color = contentColorFor(color), fontFamily = FontFamily.Monospace) }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.forEach { p ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(p)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { setColor(p) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p.toArgb() == color.toArgb()) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = contentColorFor(p))
                        }
                    }
                }

                LabeledSlider("Hue", h / 360f, Brush.horizontalGradient((0..6).map { hsv(it * 60f % 360f, 1f, 1f) })) { h = it * 360f; hexText = hsv(h, s, v).hex() }
                LabeledSlider("Saturation", s, Brush.horizontalGradient(listOf(hsv(h, 0f, v), hsv(h, 1f, v)))) { s = it; hexText = hsv(h, s, v).hex() }
                LabeledSlider("Brightness", v, Brush.horizontalGradient(listOf(Color.Black, hsv(h, s, 1f)))) { v = it; hexText = hsv(h, s, v).hex() }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t
                        parseHex(t)?.let { c -> val x = c.toHsv(); h = x[0]; s = x[1]; v = x[2] }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(default).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Text("Default ${default.hex()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color); onDismiss() }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null); onDismiss() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, brush: Brush, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(value, brush, onChange)
    }
}

@Composable
private fun GradientSlider(value: Float, brush: Brush, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thumb = 24.dp
        val thumbPx = with(density) { thumb.toPx() }
        fun valueAt(x: Float) = ((x - thumbPx / 2) / (widthPx - thumbPx)).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(widthPx) { detectTapGestures { onChange(valueAt(it.x)) } }
                .pointerInput(widthPx) { detectDragGestures { change, _ -> onChange(valueAt(change.position.x)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 4.dp).clip(CircleShape).background(brush))
            Box(
                Modifier
                    .offset { IntOffset(((widthPx - thumbPx) * value).roundToInt(), 0) }
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0x55000000), CircleShape),
            )
        }
    }
}
