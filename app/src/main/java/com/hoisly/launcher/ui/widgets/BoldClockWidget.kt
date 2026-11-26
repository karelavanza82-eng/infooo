package com.hoisly.launcher.ui.widgets

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.data.WidgetEntry
import com.hoisly.launcher.data.WidgetVisualStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Typeface

private data class BoldClockFace(val label: String, val family: FontFamily, val italic: Boolean = false, val spacing: androidx.compose.ui.unit.TextUnit = 0.sp)

/** Five deliberately heavy numeral treatments. All faces keep Black weight. */
private val BoldClockFaces = listOf(
    BoldClockFace("Heavy Sans", FontFamily.SansSerif, spacing = (-1.8).sp),
    BoldClockFace("Bold Italic", FontFamily.SansSerif, italic = true, spacing = (-1.2).sp),
    BoldClockFace("Heavy Serif", FontFamily.Serif, spacing = (-0.8).sp),
    BoldClockFace("Tech Mono", FontFamily.Monospace, spacing = (-1.0).sp),
    BoldClockFace("Condensed Heavy", FontFamily.SansSerif, spacing = (-1.4).sp),
)

/** Transparent text-only clock. Per-widget scale and face survive launcher restarts. */
@Composable
internal fun BoldClockWidget(entry: WidgetEntry) {
    val context = LocalContext.current
    val prefs = remember(entry.id) { context.getSharedPreferences("bold_clock_widget", 0) }
    var scale by remember(entry.id) { mutableFloatStateOf(prefs.getFloat("${entry.id}_scale", 1f).coerceIn(.45f, 3.2f)) }
    var faceIndex by remember(entry.id) { mutableIntStateOf(prefs.getInt("${entry.id}_face", 0).coerceIn(0, 4)) }
    var settingsOpen by remember(entry.id) { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(entry.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val clockText = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val dateText = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val face = BoldClockFaces[faceIndex]
    val ink = when (entry.visualStyle) {
        WidgetVisualStyle.Light -> Color(0xFF17181C)
        WidgetVisualStyle.Dark -> Color.White
        // Liquid Glass remains background-free: only the glyphs get a soft glass-like sheen.
        WidgetVisualStyle.LiquidGlass -> Color.White
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(entry.id) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(.45f, 3.2f)
                    prefs.edit().putFloat("${entry.id}_scale", scale).apply()
                }
            }
            .pointerInput(entry.id) {
                detectTapGestures(onLongPress = { settingsOpen = true })
            },
        contentAlignment = Alignment.Center,
    ) {
        val base = minOf(maxWidth.value / (clockText.length * .64f), maxHeight.value * .62f)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (entry.visualStyle == WidgetVisualStyle.LiquidGlass) {
                val androidTypeface = when (faceIndex) {
                    1 -> Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
                    2 -> Typeface.create("serif", Typeface.BOLD)
                    3 -> Typeface.create("monospace", Typeface.BOLD)
                    4 -> Typeface.create("sans-serif-condensed", Typeface.BLACK)
                    else -> Typeface.create("sans-serif", Typeface.BLACK)
                }
                GlyphLiquidGlassClock(
                    text = clockText,
                    fontSize = (base * scale).dp,
                    typeface = androidTypeface,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = clockText,
                    modifier = Modifier.fillMaxWidth(),
                    color = ink,
                    fontSize = (base * scale).sp,
                    fontFamily = face.family,
                    fontWeight = FontWeight.Black,
                    fontStyle = if (face.italic) FontStyle.Italic else FontStyle.Normal,
                    letterSpacing = face.spacing,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(lineHeight = (base * scale * 1.02f).sp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = dateText,
                color = ink.copy(alpha = if (entry.visualStyle == WidgetVisualStyle.LiquidGlass) .88f else .78f),
                fontSize = (base * .105f).coerceAtLeast(10f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .4.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Bold Clock · Font") },
            text = {
                Column {
                    BoldClockFaces.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = faceIndex == index, onClick = {
                                faceIndex = index
                                prefs.edit().putInt("${entry.id}_face", index).apply()
                            })
                            Text(item.label, fontFamily = item.family, fontWeight = FontWeight.Black,
                                fontStyle = if (item.italic) FontStyle.Italic else FontStyle.Normal)
                        }
                    }
                    Text("Cubit dua jari untuk mengubah ukuran angka. Tekan lama untuk memilih font.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Selesai") } },
        )
    }
}
