package com.hoisly.launcher.panels.control

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hoisly.launcher.ui.glass.GlassProfile
import com.hoisly.launcher.ui.glass.glass
import com.hoisly.launcher.ui.theme.Haptics
import kotlinx.coroutines.delay

private val GlassWhite = Color.White.copy(alpha = .15f)
private val GlassStroke = Color.White.copy(alpha = .24f)
private val IOSBlue = Color(0xFF0A84FF)
private val IOSGreen = Color(0xFF30D158)
private val IOSOrange = Color(0xFFFF9F0A)
private val IOSPurple = Color(0xFF5E5CE6)

/** Full replacement Control Center. Its structure follows the supplied iOS reference. */
@Composable
fun ControlCenterPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val torch = rememberTorch()
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val wifi = remember(tick) { context.wifiOn() }
    val bluetooth = remember(tick) { context.bluetoothOn() }
    val airplane = remember(tick) { context.airplaneOn() }
    val focus = remember(tick) { context.focusOn() }
    val rotationLocked = remember(tick) { context.rotationLocked() }
    val canWrite = remember(tick) { context.canWriteSystemSettings() }
    var brightness by remember { mutableFloatStateOf(context.brightnessFraction()) }
    var volume by remember { mutableFloatStateOf(context.volumeFraction()) }
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = readNowPlaying(context)
            if (next?.sameAs(nowPlaying) != true) nowPlaying = next
            delay(900L)
        }
    }
    val tap: () -> Unit = { haptic.performHapticFeedback(Haptics.Tick) }
    val launch: (() -> Boolean) -> Unit = { action -> tap(); if (action()) onClose() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // iOS media player: artwork on the left, metadata and transport controls on the right.
        MediaPlayerGlass(nowPlaying, Modifier.fillMaxWidth())

        // Connectivity card + two tall, independently draggable faders.
        Row(Modifier.fillMaxWidth().height(166.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1.48f).fillMaxHeight().clip(RoundedCornerShape(30.dp))
                    .glass(RoundedCornerShape(30.dp), tint = GlassWhite, stroke = GlassStroke, profile = GlassProfile.Panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CircleToggle(Icons.Rounded.AirplanemodeActive, "Airplane Mode", airplane, IOSOrange) { launch { context.openAirplaneSettings() } }
                    CircleToggle(Icons.Rounded.Podcasts, "AirDrop-style sharing", false, IOSBlue) { launch { context.openShareSheet() } }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleToggle(Icons.Rounded.Wifi, "Wi-Fi", wifi, IOSBlue, Modifier.weight(1.15f), 62.dp) { launch { context.openWifiPanel() } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        MiniToggle(Icons.Rounded.SignalCellularAlt, "Mobile Data", !airplane, IOSGreen) { launch { context.openMobileDataPanel() } }
                        MiniToggle(Icons.Rounded.Bluetooth, "Bluetooth", bluetooth, IOSBlue) { launch { context.openBluetoothSettings() } }
                    }
                }
            }
            VerticalFader(Icons.Rounded.WbSunny, "Brightness", brightness, Modifier.weight(.62f), enabled = canWrite) {
                brightness = it; context.setBrightnessFraction(it)
            }
            VerticalFader(Icons.Rounded.VolumeUp, "Volume", volume, Modifier.weight(.62f), enabled = true) {
                volume = it; context.setVolumeFraction(it)
            }
        }

        Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideToggle(Icons.Rounded.BatterySaver, "Low Power Mode", "${if (context.isPowerSaveMode()) "On" else "Off"}", context.isPowerSaveMode(), IOSGreen, Modifier.weight(1f)) {
                launch { context.openBatterySaverSettings() }
            }
            WideToggle(Icons.Rounded.DarkMode, "Do Not Disturb", if (focus) "On" else "Off", focus, IOSPurple, Modifier.weight(1f)) {
                tap(); context.toggleFocus(); tick++
            }
        }

        // Four-column iOS shortcut grid. Controls that Android restricts open the supported system UI.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Rounded.FlashlightOn, "Flashlight", torch.on, Modifier.weight(1f)) {
                    tap(); if (!torch.toggle()) android.widget.Toast.makeText(context, "Flashlight unavailable", android.widget.Toast.LENGTH_SHORT).show()
                }
                Shortcut(Icons.Rounded.FiberManualRecord, "Screen Record", false, Modifier.weight(1f)) { context.showUnavailable("Screen recording needs a MediaProjection consent flow; it is not wired into Hoisly yet.") }
                Shortcut(Icons.Rounded.ScreenLockRotation, "Rotation Lock", rotationLocked, Modifier.weight(1f)) { tap(); context.toggleRotationLock(); tick++ }
                Shortcut(Icons.Rounded.Notifications, "Notifications", false, Modifier.weight(1f)) { launch { context.openNotificationSettings() } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut(Icons.Rounded.QrCodeScanner, "QR Scanner", false, Modifier.weight(1f)) { context.showUnavailable("QR scanning is not implemented in this Hoisly build.") }
                Shortcut(Icons.Rounded.Calculate, "Calculator", false, Modifier.weight(1f)) { launch { context.openCalculator() } }
                Shortcut(Icons.Rounded.Cast, "Screen Mirroring", false, Modifier.weight(1f)) { launch { context.openCastSettings() } }
                Shortcut(Icons.Rounded.GraphicEq, "Music Recognition", false, Modifier.weight(1f)) { context.showUnavailable("Music recognition needs a dedicated recognition provider.") }
            }
        }
        if (!canWrite) {
            Text("Allow Hoisly to modify system settings to control brightness and rotation.",
                color = Color.White.copy(alpha = .82f), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .glass(RoundedCornerShape(18.dp), tint = GlassWhite, stroke = GlassStroke)
                    .clickable { context.requestWriteSettings() }.padding(14.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MediaPlayerGlass(now: NowPlaying?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var position by remember(now) { mutableLongStateOf(now?.positionMs() ?: 0L) }
    LaunchedEffect(now) {
        while (now != null) {
            position = now.positionMs()
            delay(1_000L)
        }
    }
    val progress = if (now != null && now.durationMs > 0L) (position.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
    Row(
        modifier.clip(RoundedCornerShape(30.dp))
            .glass(RoundedCornerShape(30.dp), tint = Color.White.copy(alpha = .19f), stroke = GlassStroke, profile = GlassProfile.Panel)
            .clickable(enabled = now != null) { now?.openPlayer(context) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val artwork = now?.artwork
        if (artwork != null) {
            Image(artwork, contentDescription = "Album artwork", modifier = Modifier.size(112.dp).clip(RoundedCornerShape(19.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(38.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(now?.title ?: "Not Playing", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.artist ?: "Media Player", color = Color.White.copy(alpha = .66f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Color.White.copy(alpha = .92f)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                MediaAction(Icons.Rounded.SkipPrevious, "Previous", now != null) { now?.previous() }
                MediaAction(if (now?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", now != null, 34.dp) { now?.playPause() }
                MediaAction(Icons.Rounded.SkipNext, "Next", now != null) { now?.next() }
            }
        }
    }
}

@Composable
private fun MediaAction(icon: ImageVector, label: String, enabled: Boolean, size: Dp = 28.dp, onClick: () -> Unit) {
    Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(2.dp))
}

@Composable
private fun CircleToggle(icon: ImageVector, label: String, active: Boolean, activeColor: Color, modifier: Modifier = Modifier, size: Dp = 54.dp, onClick: () -> Unit) {
    Box(modifier.size(size).clip(CircleShape)
        .glass(CircleShape, tint = if (active) activeColor.copy(alpha = .92f) else Color.White.copy(alpha = .17f), stroke = GlassStroke, profile = GlassProfile.SmallButton)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (size < 58.dp) 23.dp else 25.dp))
    }
}

@Composable
private fun MiniToggle(icon: ImageVector, label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).clip(CircleShape)
        .background(if (active) color else Color.White.copy(alpha = .16f)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun VerticalFader(icon: ImageVector, label: String, value: Float, modifier: Modifier, enabled: Boolean, onChange: (Float) -> Unit) {
    BoxWithConstraints(modifier.fillMaxHeight().clip(RoundedCornerShape(34.dp))
        .glass(RoundedCornerShape(34.dp), tint = Color.White.copy(alpha = .18f), stroke = GlassStroke, profile = GlassProfile.Panel)
        .pointerInput(enabled, maxHeight) {
            if (enabled) {
                fun update(y: Float) = onChange((1f - (y / size.height)).coerceIn(0f, 1f))
                detectTapGestures { update(it.y) }
            }
        }
        .pointerInput(enabled, maxHeight) {
            if (enabled) detectDragGestures { change, _ -> change.consume(); onChange((1f - change.position.y / size.height).coerceIn(0f, 1f)) }
        }, contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = .92f)))
        Icon(icon, label, tint = if (value > .52f) Color(0xFF1C1C1E) else Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(25.dp))
    }
}

@Composable
private fun WideToggle(icon: ImageVector, title: String, subtitle: String, active: Boolean, activeColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxHeight().clip(RoundedCornerShape(38.dp))
        .glass(RoundedCornerShape(38.dp), tint = if (active) activeColor.copy(alpha = .28f) else Color.White.copy(alpha = .17f), stroke = GlassStroke, profile = GlassProfile.Panel)
        .clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(if (active) activeColor else Color.White.copy(alpha = .9f)), contentAlignment = Alignment.Center) {
            Icon(icon, title, tint = if (active) Color.White else activeColor, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
            .glass(CircleShape, tint = if (active) IOSBlue else Color.White.copy(alpha = .19f), stroke = GlassStroke, profile = GlassProfile.Button)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(29.dp))
        }
        Text(label, color = Color.White.copy(alpha = .82f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Context.isPowerSaveMode(): Boolean = runCatching { getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
private fun Context.openBatterySaverSettings(): Boolean = startQuietly(android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS))
private fun Context.openShareSheet(): Boolean = startQuietly(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "Shared from Hoisly"), "Share"))
private fun Context.showUnavailable(message: String) { android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show() }
private fun Context.openNotificationSettings(): Boolean = startQuietly(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
private fun Context.openCastSettings(): Boolean = startQuietly(android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS))
