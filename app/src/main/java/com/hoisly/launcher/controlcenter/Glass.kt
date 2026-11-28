package com.hoisly.launcher.controlcenter

import com.hoisly.launcher.ui.glass.glass as hoislyGlass

import android.app.NotificationManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/** Colours of the frosted-glass look: translucent white over a dark, blurred backdrop. */
object G {
    val fill = Color(0x38FFFFFF)
    val fillDeep = Color(0x4DFFFFFF)
    val rimTop = Color(0x70FFFFFF)
    val rimBottom = Color(0x12FFFFFF)

    val white = Color(0xF7FFFFFF)
    val blue = Color(0xFF0A84FF)
    val green = Color(0xFF30D158)
    val orange = Color(0xFFFF9F0A)
    val yellow = Color(0xFFFFD60A)
    val purple = Color(0xFF6E5CE6)
    val cyan = Color(0xFF4FC3F7)
    val red = Color(0xFFF0707A)
    val ink = Color(0xFF1C1C1E)
    val dim = Color(0x99FFFFFF)
}

/** Frosted surface: translucent fill with a soft top-lit rim, clipped to [shape]. */
/**
 * Control Center surfaces deliberately use the same Hoisly glass renderer as the launcher,
 * rather than maintaining a second, visually divergent glass implementation.
 */
fun Modifier.glass(shape: Shape, fill: Color = G.fill): Modifier =
    this.hoislyGlass(
        shape = shape,
        tint = fill,
        profile = com.hoisly.launcher.ui.glass.GlassProfile.Panel,
    )

/** The shared Hoisly renderer already draws its optical edge; do not add a second rim. */
fun Modifier.rim(shape: Shape): Modifier = this

/** Absolute placement in "reference dp": x, y, width and height measured on the 360dp reference screen. */
@Composable
fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier = this.offset(x.u, y.u).size(w.u, h.u)

/** A glass button with press-scale and a colour that animates between states. */
@Composable
fun GlassButton(
    modifier: Modifier,
    shape: Shape,
    fill: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 440f),
        label = "press",
    )
    val bg by animateColorAsState(fill, tween(200, easing = Op.ColorEasing), label = "glassFill")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glass(shape, bg)
            .combinedClickable(source, indication = null, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The 62dp round tiles of the two bottom rows: grey glass when off, solid white when on. */
@Composable
fun GlassCircleTile(tile: TileUi, size: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val on = tile.active && !tile.stateless
    val tint = when {
        !on -> Color.White
        tile.id == "rotlock" -> G.red
        else -> G.ink
    }
    GlassButton(Modifier.size(size), CircleShape, if (on) G.white else G.fill, onClick, onLongClick) {
        val bmp = tile.bitmap
        if (bmp != null) {
            Image(bmp, null, modifier = Modifier.size(34.u).clip(CircleShape))
        } else {
            Icon(tile.icon, null, tint = tint, modifier = Modifier.size(30.u))
        }
    }
}

/** Round button used inside the connectivity block and on the connectivity page. */
@Composable
fun ConnectivityButton(
    ui: TileUi?,
    activeFill: Color,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRotate: Float = 0f,
) {
    val on = ui != null && ui.active && !ui.stateless
    GlassButton(modifier.size(size), CircleShape, if (on) activeFill else G.fillDeep, onClick, onLongClick) {
        Icon(
            icon ?: ui?.icon ?: Icons.Rounded.Wifi, null,
            tint = if (on) Color.White else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = iconRotate },
        )
    }
}

/**
 * The two wide pills (Low Power Mode, Do Not Disturb): an icon on the left, a label in up to two
 * lines and, for most tiles, an On / Off line under it.
 */
@Composable
fun WidePill(ui: TileUi, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val dnd = ui.id == "dnd"
    GlassButton(modifier, RoundedCornerShape(50), G.fill, onClick, onLongClick) {
        Row(Modifier.fillMaxSize().padding(start = 13.u), verticalAlignment = Alignment.CenterVertically) {
            if (dnd) {
                Box(
                    Modifier.size(37.u).clip(CircleShape).background(if (ui.active) G.purple else G.white),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DarkMode, null, tint = if (ui.active) Color.White else G.purple, modifier = Modifier.size(22.u))
                }
            } else {
                Box(Modifier.size(37.u), contentAlignment = Alignment.Center) {
                    Icon(
                        ui.icon, null, tint = if (ui.active) G.yellow else Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(38.u),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 8.u, end = if (dnd) 2.u else 6.u)) {
                Text(
                    ui.label, color = Color.White, fontSize = 14.5.us, lineHeight = 17.us, fontFamily = Op.Body,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Clip,
                )
                if (!dnd) {
                    Text(
                        if (ui.active) "On" else "Off", color = G.dim, fontSize = 14.5.us, lineHeight = 17.us,
                        fontFamily = Op.Body, maxLines = 1,
                    )
                }
            }
            if (dnd) {
                Icon(Icons.Rounded.UnfoldMore, null, tint = G.dim, modifier = Modifier.padding(end = 10.u).size(20.u))
            }
        }
    }
}

// region status row

@Composable
fun IosStatusRow(modifier: Modifier) {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(StatusIcons.read(ctx)) }
    var rotationLocked by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    var carrier by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            st = StatusIcons.read(ctx)
            rotationLocked = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
            dnd = ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter >
                NotificationManager.INTERRUPTION_FILTER_ALL
            carrier = if (Demo.enabled(ctx)) Demo.CARRIER
            else ctx.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
            delay(1500)
        }
    }
    Row(modifier.padding(horizontal = 6.u), verticalAlignment = Alignment.CenterVertically) {
        Text(carrier, color = Color.White, fontSize = 15.us, fontFamily = Op.Body, fontWeight = FontWeight.Medium, maxLines = 1)
        if (st.wifiLevel != null) {
            Icon(Icons.Rounded.Wifi, null, tint = Color.White, modifier = Modifier.padding(start = 5.u).size(17.u))
        }
        Spacer(Modifier.weight(1f))
        if (rotationLocked) {
            Icon(IosIcons.RotationLock, null, tint = Color.White, modifier = Modifier.padding(end = 8.u).size(17.u))
        }
        if (dnd) {
            Icon(Icons.Rounded.DarkMode, null, tint = Color.White, modifier = Modifier.padding(end = 8.u).size(16.u))
        }
        Text(
            "${st.battery}%", color = Color.White, fontSize = 14.us, fontFamily = Op.Body, fontWeight = FontWeight.Medium,
            maxLines = 1, modifier = Modifier.padding(end = 5.u),
        )
        BatteryGlyph(st)
    }
}

@Composable
private fun BatteryGlyph(s: StatusState) {
    val fill = when {
        s.charging -> Color(0xFF34C759)
        s.powerSave -> Color(0xFFFFC233)
        s.battery <= 15 -> Color(0xFFFF5449)
        else -> Color.White
    }
    Canvas(Modifier.size(27.u, 13.u)) {
        val bodyW = size.width - 2.dp.toPx()
        val h = size.height
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.38f), size = Size(bodyW, h), cornerRadius = r)
        val pad = 1.6.dp.toPx()
        val inner = (bodyW - pad * 2f) * (s.battery.coerceIn(0, 100) / 100f)
        if (inner > 0f) {
            drawRoundRect(
                fill, topLeft = Offset(pad, pad), size = Size(inner.coerceAtLeast(h - pad * 2f), h - pad * 2f),
                cornerRadius = CornerRadius(2.6.dp.toPx()),
            )
        }
        drawRoundRect(
            Color.White.copy(alpha = 0.45f), topLeft = Offset(bodyW + 0.6.dp.toPx(), h * 0.32f),
            size = Size(1.3.dp.toPx(), h * 0.36f), cornerRadius = CornerRadius(0.7.dp.toPx()),
        )
    }
}

// endregion

// region media card

/**
 * The wide music card: artwork on the left; title, artist and an output button on top;
 * previous / play / next in the middle; a flat seek bar with elapsed and remaining time below.
 * With no player it shows "Not Playing" and dims the buttons.
 */
@Composable
fun IosMediaCard(item: MediaItem?, host: ShadeService, modifier: Modifier) {
    val shape = RoundedCornerShape(34.u)
    val enabled = item != null
    Box(modifier.glass(shape, G.fill)) {
        Row(Modifier.fillMaxSize().padding(12.u)) {
            Box(
                Modifier.size(114.u).clip(RoundedCornerShape(15.u)).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                val art = item?.art
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Rounded.MusicNote, null, tint = G.dim, modifier = Modifier.size(44.u))
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.u), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 3.u)) {
                        Text(
                            item?.title ?: "Not Playing", color = Color.White.copy(alpha = 0.92f), fontSize = 17.us, lineHeight = 20.us,
                            fontFamily = Op.Display, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item?.artist.orEmpty(), color = Color.White.copy(alpha = 0.5f), fontSize = 17.us, lineHeight = 20.us,
                            fontFamily = Op.Display, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .size(37.u)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .combinedClickable(enabled = enabled, onClick = { item?.let { host.openOutputSwitcher(it.pkg, it.token) } }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(IosIcons.AirPlay, null, tint = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f), modifier = Modifier.size(23.u))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TransportButton(Icons.Rounded.FastRewind, 44.u, 34.u, enabled && item!!.has(PlaybackState.ACTION_SKIP_TO_PREVIOUS)) {
                        item?.let { MediaRepo.command(it.token, MediaRepo.Cmd.PREV) }
                    }
                    TransportButton(
                        if (item?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 46.u, 44.u, enabled,
                    ) {
                        item?.let { MediaRepo.command(it.token, if (it.playing) MediaRepo.Cmd.PAUSE else MediaRepo.Cmd.PLAY) }
                    }
                    TransportButton(Icons.Rounded.FastForward, 44.u, 34.u, enabled && item!!.has(PlaybackState.ACTION_SKIP_TO_NEXT)) {
                        item?.let { MediaRepo.command(it.token, MediaRepo.Cmd.NEXT) }
                    }
                }
                SeekWithTimes(item)
            }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, box: Dp, iconSize: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(box).clip(CircleShape).combinedClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(iconSize))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
private fun SeekWithTimes(item: MediaItem?) {
    val canSeek = item != null && item.duration > 0 && item.has(PlaybackState.ACTION_SEEK_TO)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(item?.playing, item?.positionTime) {
        while (item?.playing == true) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
        now = SystemClock.elapsedRealtime()
    }
    var drag by remember { mutableFloatStateOf(-1f) }
    val current by rememberUpdatedState(item)
    val duration = item?.duration ?: 0L
    val elapsed = when {
        item == null -> 0L
        drag >= 0f -> (drag * duration).toLong()
        else -> item.positionAt(now)
    }
    val fraction = if (duration > 0) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val seekTo: (Long) -> Unit = { ms -> current?.let { MediaRepo.controller(it.token)?.transportControls?.seekTo(ms) } }

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.u)
                .then(
                    if (!canSeek) Modifier else Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { o -> seekTo(((o.x / size.width) * (current?.duration ?: 0L)).toLong()) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = { seekTo((drag * (current?.duration ?: 0L)).toLong()); drag = -1f },
                                onDragCancel = { drag = -1f },
                            ) { ch, _ -> ch.consume(); drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
                        },
                ),
        ) {
            val h = (if (drag >= 0f) 8.dp else 6.dp).toPx()
            val top = (size.height - h) / 2f
            val r = CornerRadius(h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.32f), Offset(0f, top), Size(size.width, h), r)
            val w = size.width * fraction
            if (w > 0f) drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(0f, top), Size(w.coerceAtLeast(h), h), r)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(elapsed), color = G.dim, fontSize = 13.us, fontFamily = Op.Body, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("-" + fmt((duration - elapsed).coerceAtLeast(0)), color = G.dim, fontSize = 13.us, fontFamily = Op.Body, maxLines = 1)
        }
    }
}

// endregion
