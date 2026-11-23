package com.zfl.launcher.panels.lock

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.os.BatteryManager
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.panels.PanelController
import com.zfl.launcher.panels.control.ControlState
import com.zfl.launcher.panels.control.SystemControls
import com.zfl.launcher.panels.control.startQuietly
import com.zfl.launcher.panels.notifications.CenterNotification
import com.zfl.launcher.panels.notifications.NotificationCard
import com.zfl.launcher.panels.verticalPanelDrag
import kotlinx.coroutines.delay
import java.util.Date

private const val MAX_STACK = 3

/**
 * The Lock Screen view: wallpaper, large clock and date, the latest notifications, flashlight
 * and camera shortcuts, and a home indicator. It is a layer of the launcher and is NOT the
 * system lock screen: it adds no security and unlocking is a swipe. Android does not let a
 * launcher replace or bypass the secure lock screen, and this view does not try to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LockScreenOverlay(
    controller: PanelController,
    controls: SystemControls,
    controlState: ControlState,
    wallpaper: ImageBitmap?,
    notifications: List<CenterNotification>,
    hideContent: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    val deviceFallback = stringResource(R.string.lock_device_fallback)
    val deviceName = remember(context, deviceFallback) {
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL ?: deviceFallback
    }
    // Wakes exactly at each minute change, so the clock never ticks while the display is idle.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            battery = runCatching {
                context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            }.getOrDefault(-1)
            charging = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }.getOrDefault(false)
            delay(60_000L - now % 60_000L)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(now, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
        java.text.SimpleDateFormat(pattern, locale).format(Date(now)).replace(Regex("\\s?[AaPp][Mm]$"), "")
    }
    val date = remember(now, locale) {
        java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale).format(Date(now))
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val shown = controller.lockFraction
                translationY = -(1f - shown) * size.height
                alpha = (shown * 3f).coerceIn(0f, 1f)
            }
            .background(Color.Black)
            .verticalPanelDrag(
                accepts = { true },
                onStart = {},
                onDrag = { controller.dragLock(it) },
                onEnd = controller::releaseLock,
            ),
    ) {
        if (wallpaper != null) {
            // Give the wallpaper a little depth: as the lock view is revealed or swiped away,
            // the image moves more slowly than the foreground (a restrained parallax effect).
            Image(
                wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val shown = controller.lockFraction.coerceIn(0f, 1f)
                        val travel = (1f - shown) * size.height
                        scaleX = 1.06f - 0.06f * shown
                        scaleY = 1.06f - 0.06f * shown
                        translationY = travel * 0.14f
                    },
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.35f))),
            ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val shown = controller.lockFraction.coerceIn(0f, 1f)
                    val settle = 1f - shown
                    alpha = 0.88f + 0.12f * shown
                    scaleX = 0.975f + 0.025f * shown
                    scaleY = 0.975f + 0.025f * shown
                    translationY = settle * size.height * 0.035f
                }
                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(date, color = Color.White.copy(alpha = 0.82f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                time,
                color = Color.White.copy(alpha = 0.82f), fontSize = 104.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-3).sp, lineHeight = 108.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            // Lock Screen widgets under the clock: battery, a camera shortcut and a gauge.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BatteryLockWidget(level = battery, charging = charging, name = deviceName, modifier = Modifier.width(158.dp))
                LockCircle(Modifier.size(54.dp), onClick = {
                    context.startQuietly(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                }, description = stringResource(R.string.control_camera)) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                BrightnessGauge(controlState.brightness, Modifier.size(64.dp))
            }
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                notifications.take(MAX_STACK).forEach { item ->
                    NotificationCard(item, hideContent, onClick = controller::unlock)
                }
            }
            // Flashlight bottom-left, camera bottom-right.
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundShortcut(
                    Icons.Rounded.FlashlightOn, stringResource(R.string.control_flashlight), controlState.torch,
                ) { if (controlState.torchAvailable) controls.setTorch(!controlState.torch) }
                RoundShortcut(Icons.Rounded.CameraAlt, stringResource(R.string.control_camera), false) {
                    context.startQuietly(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                }
            }
            // Home indicator: tap or swipe up to unlock.
            Box(
                Modifier
                    .padding(top = 18.dp, bottom = 8.dp)
                    .width(134.dp).height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lock_screen_unlock), onClick = controller::unlock),
            )
        }
    }
}

@Composable
private fun RoundShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.38f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(24.dp))
    }
}

private val WidgetTrack = Color.White.copy(alpha = 0.28f)

/** Round dark button with a glyph, for the widget row under the clock. */
@Composable
private fun LockCircle(modifier: Modifier, onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Lock Screen battery widget: phone glyph, bolt while charging and the percentage, the device name and a level bar. */
@Composable
private fun BatteryLockWidget(level: Int, charging: Boolean, name: String, modifier: Modifier = Modifier) {
    val shown = level.coerceIn(0, 100)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            if (charging) Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(if (level in 0..100) "$level%" else "--", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(name, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(9.dp).clip(RoundedCornerShape(50)).background(WidgetTrack)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown / 100f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)))
        }
    }
}

/** Circular gauge with the screen brightness in percent and a sun at the bottom of the ring. */
@Composable
private fun BrightnessGauge(brightness: Float, modifier: Modifier = Modifier) {
    val fraction = brightness.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // A 270 degree ring open at the bottom, where the sun sits.
            drawArc(WidgetTrack, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Color.White.copy(alpha = 0.92f), 135f, 270f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("${(fraction * 100).toInt()}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.Rounded.LightMode, contentDescription = null, tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.BottomCenter).size(12.dp),
        )
    }
}
