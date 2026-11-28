package com.hoisly.launcher.ui.widgets

import android.graphics.Typeface
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hoisly.launcher.R
import com.hoisly.launcher.data.WidgetEntry
import com.hoisly.launcher.data.WidgetVisualStyle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class BoldClockFace(
    val labelRes: Int,
    val typeface: Typeface,
)

/** Five platform typefaces with distinct silhouettes, all rendered at a heavy weight. */
private val BoldClockFaces = listOf(
    BoldClockFace(R.string.bold_clock_font_heavy_sans, Typeface.create("sans-serif-black", Typeface.NORMAL)),
    BoldClockFace(R.string.bold_clock_font_bold_italic, Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC)),
    BoldClockFace(R.string.bold_clock_font_heavy_serif, Typeface.create("serif", Typeface.BOLD)),
    BoldClockFace(R.string.bold_clock_font_tech_mono, Typeface.create("monospace", Typeface.BOLD)),
    BoldClockFace(R.string.bold_clock_font_rounded_heavy, Typeface.create("sans-serif-rounded", Typeface.BOLD)),
)

private const val MIN_CLOCK_SCALE = 0.45f
private const val MAX_CLOCK_SCALE = 1.8f

/** Transparent, glyph-only clock. Font, appearance and size persist per placed widget. */
@Composable
internal fun BoldClockWidget(entry: WidgetEntry) {
    val context = LocalContext.current
    val prefs = remember(entry.id) { context.getSharedPreferences("bold_clock_widget", 0) }
    var scale by remember(entry.id) {
        mutableFloatStateOf(prefs.getFloat("${entry.id}_scale", 1f).coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE))
    }
    var faceIndex by remember(entry.id) {
        mutableIntStateOf(prefs.getInt("${entry.id}_face", 0).coerceIn(0, BoldClockFaces.lastIndex))
    }
    var appearance by remember(entry.id) {
        mutableStateOf(
            WidgetVisualStyle.entries.firstOrNull { it.name == prefs.getString("${entry.id}_style", null) }
                ?: entry.visualStyle
        )
    }
    var settingsOpen by remember(entry.id) { mutableStateOf(false) }
    var draftScale by remember(entry.id) { mutableFloatStateOf(scale) }
    var draftFace by remember(entry.id) { mutableIntStateOf(faceIndex) }
    var draftAppearance by remember(entry.id) { mutableStateOf(appearance) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(entry.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val clockText = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val dateText = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val face = BoldClockFaces[faceIndex]
    val ink = clockInk(appearance)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(entry.id) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE)
                    prefs.edit().putFloat("${entry.id}_scale", scale).apply()
                }
            }
            .pointerInput(entry.id) {
                detectTapGestures(onLongPress = {
                    draftScale = scale
                    draftFace = faceIndex
                    draftAppearance = appearance
                    settingsOpen = true
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val maxFit = fitScale(maxWidth.value, maxHeight.value, clockText.length)
        val displayScale = scale.coerceAtMost(maxFit)
        val base = minOf(
            maxWidth.value / (clockText.length * 0.64f * maxFit),
            maxHeight.value / (1.22f * maxFit + (12f / 36f)),
        ).coerceAtLeast(1f)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlyphLiquidGlassClock(
                text = clockText,
                fontSize = (base * displayScale).dp,
                typeface = face.typeface,
                modifier = Modifier.fillMaxWidth(),
                liquidGlass = appearance == WidgetVisualStyle.LiquidGlass,
                color = ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dateText,
                color = ink.copy(alpha = if (appearance == WidgetVisualStyle.LiquidGlass) .88f else .78f),
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
            title = { Text(stringResource(R.string.bold_clock_customize)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.bold_clock_preview_hint), style = MaterialTheme.typography.labelSmall)
                    GlyphLiquidGlassClock(
                        text = clockText,
                        fontSize = (34f * draftScale).dp,
                        typeface = BoldClockFaces[draftFace].typeface,
                        modifier = Modifier.fillMaxWidth().height((42f * draftScale).dp)
                            .pointerInput(draftFace) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    draftScale = (draftScale * zoom).coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE)
                                }
                            },
                        liquidGlass = draftAppearance == WidgetVisualStyle.LiquidGlass,
                        color = clockInk(draftAppearance),
                    )
                    Text(stringResource(R.string.bold_clock_appearance), style = MaterialTheme.typography.labelMedium)
                    WidgetVisualStyle.entries.forEach { style ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = draftAppearance == style,
                                onClick = { draftAppearance = style },
                            )
                            Text(
                                stringResource(
                                    when (style) {
                                        WidgetVisualStyle.Light -> R.string.bold_clock_style_light
                                        WidgetVisualStyle.Dark -> R.string.bold_clock_style_dark
                                        WidgetVisualStyle.LiquidGlass -> R.string.bold_clock_style_liquid
                                    }
                                ),
                            )
                        }
                    }
                    Text(stringResource(R.string.bold_clock_font), style = MaterialTheme.typography.labelMedium)
                    BoldClockFaces.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = draftFace == index,
                                onClick = { draftFace = index },
                            )
                            Text(stringResource(item.labelRes), fontWeight = FontWeight.Black)
                        }
                    }
                    Text(stringResource(R.string.bold_clock_size, (draftScale * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    faceIndex = draftFace
                    scale = draftScale
                    appearance = draftAppearance
                    prefs.edit()
                        .putInt("${entry.id}_face", faceIndex)
                        .putFloat("${entry.id}_scale", scale)
                        .putString("${entry.id}_style", appearance.name)
                        .apply()
                    settingsOpen = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { settingsOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun clockInk(style: WidgetVisualStyle): Color = when (style) {
    WidgetVisualStyle.Light -> Color(0xFF17181C)
    WidgetVisualStyle.Dark, WidgetVisualStyle.LiquidGlass -> Color.White
}

/** Maximum user scale that keeps the full time string inside the current widget bounds. */
private fun fitScale(widthDp: Float, heightDp: Float, digits: Int): Float {
    val widthFit = widthDp / (digits * 0.64f * 36f)
    val heightFit = heightDp / (1.22f * 36f + 12f)
    return minOf(MAX_CLOCK_SCALE, widthFit, heightFit).coerceAtLeast(MIN_CLOCK_SCALE)
}
