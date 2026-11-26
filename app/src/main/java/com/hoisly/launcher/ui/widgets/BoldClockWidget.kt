package com.hoisly.launcher.ui.widgets

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.hoisly.launcher.data.WidgetEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class BoldClockFace(val label: String, val family: FontFamily, val italic: Boolean = false, val spacing: TextUnit = 0.sp)
private val BoldClockFaces = listOf(
    BoldClockFace("Heavy Sans", FontFamily.SansSerif, spacing = (-1.8).sp),
    BoldClockFace("Bold Italic", FontFamily.SansSerif, italic = true, spacing = (-1.2).sp),
    BoldClockFace("Heavy Serif", FontFamily.Serif, spacing = (-0.8).sp),
    BoldClockFace("Tech Mono", FontFamily.Monospace, spacing = (-1.0).sp),
    BoldClockFace("Rounded Heavy", FontFamily.Cursive, spacing = (-1.0).sp),
)

/** Pinch-resizable digital clock. Face and scale are stored per widget ID on this device. */
@Composable
internal fun BoldClockWidget(entry: WidgetEntry) {
    val context = LocalContext.current
    val prefs = remember(entry.id) { context.getSharedPreferences("bold_clock_widget", 0) }
    var scale by remember(entry.id) { mutableFloatStateOf(prefs.getFloat("${entry.id}_scale", 1f).coerceIn(.55f, 1.8f)) }
    var faceIndex by remember(entry.id) { mutableIntStateOf(prefs.getInt("${entry.id}_face", 0).coerceIn(0, 4)) }
    var settingsOpen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val clockText = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val face = BoldClockFaces[faceIndex]
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(entry.id) {
            detectTransformGestures { _, _, zoom, _ ->
                scale = (scale * zoom).coerceIn(.55f, 1.8f)
                prefs.edit().putFloat("${entry.id}_scale", scale).apply()
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val base = minOf(maxWidth.value / (clockText.length * .64f), maxHeight.value * .58f)
        Text(
            text = clockText,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            color = LocalWidgetForeground.current,
            fontSize = (base * scale).sp,
            fontFamily = face.family,
            fontWeight = FontWeight.Black,
            fontStyle = if (face.italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = face.spacing,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(lineHeight = (base * scale * 1.05f).sp),
        )
        IconButton(
            onClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
        ) { Icon(Icons.Rounded.Tune, contentDescription = "Clock style", tint = LocalWidgetForeground.current.copy(alpha = .72f), modifier = Modifier.size(17.dp)) }
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Bold Clock style") },
            text = {
                Column {
                    Text("Choose a bold number font", fontWeight = FontWeight.SemiBold)
                    BoldClockFaces.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = faceIndex == index, onClick = {
                                faceIndex = index
                                prefs.edit().putInt("${entry.id}_face", index).apply()
                            })
                            Text(item.label, fontFamily = item.family, fontWeight = FontWeight.Black, fontStyle = if (item.italic) FontStyle.Italic else FontStyle.Normal)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Pinch with two fingers on the clock to resize it.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Done") } },
        )
    }
}
