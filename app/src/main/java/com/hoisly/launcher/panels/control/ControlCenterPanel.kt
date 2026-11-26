package com.hoisly.launcher.panels.control

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hoisly.launcher.R
import com.hoisly.launcher.ui.settings.IosSlider
import com.hoisly.launcher.ui.theme.Haptics
import kotlinx.coroutines.delay

private val TileFill = Color.White.copy(alpha = 0.16f)
private val ActiveBlue = Color(0xFF0A84FF)
private val ActiveOrange = Color(0xFFFF9F0A)
private val ActiveGreen = Color(0xFF30D158)
private val ActivePurple = Color(0xFF5E5CE6)

/**
 * The Control Center sheet. Every control does what it says: Android's own panels open for what
 * apps may not switch, and the rest is driven directly (flashlight, volume, brightness,
 * rotation, Focus, media). [onClose] dismisses the sheet after something opens another screen.
 */
@Composable
fun ControlCenterPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val torch = rememberTorch()

    // Bumped whenever the launcher resumes (after Android's own pages) or a control changed state.
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

    // Polled only while the sheet is composed, so nothing runs once it is closed.
    var nowPlaying by remember { mutableStateOf<NowPlaying?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = readNowPlaying(context)
            if (next?.sameAs(nowPlaying) != true) nowPlaying = next
            delay(1_000L)
        }
    }

    fun tap() = haptic.performHapticFeedback(Haptics.Tick)
    fun launch(action: () -> Boolean) { tap(); if (action()) onClose() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Connectivity block: four round controls in a 2x2 card.
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(TileFill).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundControl(Icons.Rounded.AirplanemodeActive, stringResource(R.string.control_airplane), airplane, ActiveOrange) { launch(context::openAirplaneSettings) }
                    RoundControl(Icons.Rounded.SignalCellularAlt, stringResource(R.string.control_mobile_data), !airplane, ActiveGreen) { launch(context::openMobileDataPanel) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundControl(Icons.Rounded.Wifi, stringResource(R.string.control_wifi), wifi, ActiveBlue) { launch(context::openWifiPanel) }
                    RoundControl(Icons.Rounded.Bluetooth, stringResource(R.string.control_bluetooth), bluetooth, ActiveBlue) { launch(context::openBluetoothSettings) }
                }
            }
            MusicCard(nowPlaying, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SquareTile(Icons.Rounded.ScreenLockRotation, stringResource(R.string.control_rotation_lock), rotationLocked, ActiveOrange, Modifier.weight(1f)) {
                tap(); context.toggleRotationLock(); tick++
            }
            SquareTile(Icons.Rounded.Bedtime, stringResource(R.string.control_focus), focus, ActivePurple, Modifier.weight(1f)) {
                tap(); context.toggleFocus(); tick++
            }
        }
        if (!canWrite) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TileFill)
                    .clickable { context.requestWriteSettings() }.padding(14.dp),
            ) {
                Text(stringResource(R.string.control_need_write_settings), color = Color.White, fontSize = 14.sp)
            }
        }
        SliderCard(Icons.Rounded.WbSunny, stringResource(R.string.control_brightness), brightness, canWrite) {
            brightness = it
            context.setBrightnessFraction(it)
        }
        SliderCard(Icons.Rounded.VolumeUp, stringResource(R.string.control_volume), volume, true) {
            volume = it
            context.setVolumeFraction(it)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SquareTile(Icons.Rounded.FlashlightOn, stringResource(R.string.control_flashlight), torch.on, Color.White, Modifier.weight(1f), activeContent = Color.Black) {
                tap(); if (!torch.toggle()) android.widget.Toast.makeText(context, R.string.control_flashlight_unavailable, android.widget.Toast.LENGTH_SHORT).show()
            }
            SquareTile(Icons.Rounded.Calculate, stringResource(R.string.control_calculator), false, ActiveBlue, Modifier.weight(1f)) { launch(context::openCalculator) }
            SquareTile(Icons.Rounded.CameraAlt, stringResource(R.string.control_camera), false, ActiveBlue, Modifier.weight(1f)) { launch(context::openCamera) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RoundControl(icon: ImageVector, label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).clip(CircleShape).background(if (active) activeColor else Color.White.copy(alpha = 0.2f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SquareTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    activeContent: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(if (active) activeColor else TileFill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) activeContent else Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun SliderCard(icon: ImageVector, label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TileFill).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        IosSlider(value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MusicCard(now: NowPlaying?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(TileFill).padding(14.dp)
            .clickable(enabled = now != null) { now?.openPlayer(context) },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            now?.title ?: stringResource(R.string.control_media_idle),
            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(now?.artist.orEmpty(), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            MediaButton(Icons.Rounded.SkipPrevious, now != null, 28.dp) { now?.previous() }
            MediaButton(if (now?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, now != null, 34.dp) { now?.playPause() }
            MediaButton(Icons.Rounded.SkipNext, now != null, 28.dp) { now?.next() }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    Icon(
        icon, contentDescription = null,
        tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
    )
}
