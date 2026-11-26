package com.hoisly.launcher.ui.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.positionChange
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoisly.launcher.R
import com.hoisly.launcher.panels.control.rememberTorch
import com.hoisly.launcher.panels.notifications.CenterNotification
import com.hoisly.launcher.panels.notifications.NotificationCenterFeed
import com.hoisly.launcher.settings.LocalSettingsState
import com.hoisly.launcher.ui.glass.LocalGlassSource
import com.hoisly.launcher.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_CARDS = 3

/**
 * Hoisly's launcher lock screen: a decorative surface shown above the swipe keyguard. It is
 * deliberately not an Android security/keyguard replacement; [onDismiss] asks Android to dismiss
 * its own keyguard, so PIN, pattern and biometrics stay with the system. [onCamera] opens the
 * secure camera.
 */
@Composable
fun HoislyLockScreen(onDismiss: () -> Unit, onCamera: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = LocalSettingsState.current.panels
    val wallpaper = LocalGlassSource.current?.sharp?.takeIf { prefs.lockWallpaper }
    val torch = rememberTorch()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(millisToNextMinute(now))
        }
    }
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val time = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    val battery = if (prefs.lockShowBattery) rememberBattery() else null
    val cards = if (prefs.notificationCenter && prefs.lockShowNotifications) {
        val items by NotificationCenterFeed.items.collectAsStateWithLifecycle()
        items.take(MAX_CARDS)
    } else {
        emptyList()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .graphicsLayer {
                translationY = offset.value
                alpha = 1f - unlockProgress(offset.value, size.height) * 0.6f
            }
            .swipeUpToUnlock(
                onDrag = { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) } },
                onEnd = { velocity ->
                    scope.launch {
                        if (shouldUnlock(offset.value, heightPx, velocity)) {
                            offset.animateTo(-heightPx, Motion.expand())
                            onDismiss()
                        } else {
                            offset.animateTo(0f, Motion.sheet())
                        }
                    }
                },
            ),
    ) {
        Backdrop(wallpaper, prefs.lockWallpaperBlur)
        Column(
            Modifier
                .align(BiasAlignment(0f, if (prefs.lockClockCentered) -0.2f else -1f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = if (prefs.lockClockCentered) 0.dp else 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (battery != null) BatteryPill(battery)
            Text(date, color = Color.White.copy(alpha = .92f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                time,
                color = Color.White.copy(alpha = .8f),
                fontSize = (112f * prefs.lockClockScale).sp,
                lineHeight = (116f * prefs.lockClockScale).sp,
                fontWeight = clockFontWeight(prefs.lockClockWeight),
                letterSpacing = (-5).sp,
                textAlign = TextAlign.Center,
            )
        }
        if (cards.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 16.dp, end = 16.dp, bottom = 168.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cards.forEach { LockNotificationCard(it, hideContent = prefs.hideNotificationContent) }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutButton(
                Icons.Rounded.FlashlightOn, stringResource(R.string.lock_flashlight), active = torch.on,
            ) { torch.toggle() }
            ShortcutButton(Icons.Rounded.CameraAlt, stringResource(R.string.lock_camera), active = false, onClick = onCamera)
        }
        Text(
            stringResource(R.string.lock_swipe_hint),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 24.dp),
            color = Color.White.copy(alpha = .85f), fontSize = 14.sp,
        )
    }
}

@Composable
private fun Backdrop(wallpaper: androidx.compose.ui.graphics.ImageBitmap?, amount: Float) {
    if (wallpaper == null) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF06345C), Color(0xFF0875A5), Color(0xFF06182D))),
            ),
        )
        return
    }
    Image(
        bitmap = wallpaper,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur((amount * 24f).dp),
    )
    // A scrim keeps the clock readable on bright wallpapers; stronger blur dims a little more.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f + 0.2f * amount)))
}

@Composable
private fun BatteryPill(state: BatteryState) {
    Row(
        Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.charging) {
            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text("${state.percent}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LockNotificationCard(item: CenterNotification, hideContent: Boolean) {
    val hidden = hideContent || item.secret
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(item.appLabel, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!hidden) {
            item.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.text?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShortcutButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape)
            .background(if (active) Color.White else Color.White.copy(alpha = .16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (active) Color.Black else Color.White)
    }
}

internal data class BatteryState(val percent: Int, val charging: Boolean)

/** Battery level and charging state from the sticky broadcast; registered only while shown. */
@Composable
private fun rememberBattery(): BatteryState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            intent ?: return
            val percent = batteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)) ?: return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            state = BatteryState(percent, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = read(intent)
        }
        read(ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** One upward drag with release velocity (px/s, negative = up); buttons keep their own taps. */
private fun Modifier.swipeUpToUnlock(onDrag: (Float) -> Unit, onEnd: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var started = false
        val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            started = true
            tracker.addPosition(change.uptimeMillis, change.position)
            onDrag(overSlop)
        } ?: return@awaitEachGesture
        val completed = verticalDrag(slop.id) { change ->
            tracker.addPosition(change.uptimeMillis, change.position)
            onDrag(change.positionChange().y)
            change.consume()
        }
        if (started) onEnd(if (completed) tracker.calculateVelocity().y else 0f)
    }
}
