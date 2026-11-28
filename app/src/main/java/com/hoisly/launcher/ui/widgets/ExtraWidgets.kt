package com.hoisly.launcher.ui.widgets

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hoisly.launcher.data.WidgetKind
import com.hoisly.launcher.data.WidgetSize
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Built-in widgets added in the 20-widget catalogue. No fabricated health/activity figures. */
@Composable
fun ExtraWidget(kind: WidgetKind, size: WidgetSize) {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(30_000) } }
    val battery = remember(tick) {
        runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", 100) ?: 100
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        }.getOrNull()
    }
    val now = remember(tick) { LocalDateTime.now() }
    val title: String
    val value: String
    val detail: String
    when (kind) {
        WidgetKind.WorldClock -> { title = "WORLD CLOCK"; value = now.format(DateTimeFormatter.ofPattern("HH:mm")); detail = now.format(DateTimeFormatter.ofPattern("EEE, d MMM")) }
        WidgetKind.Reminders -> { title = "REMINDERS"; value = "No reminder data"; detail = "Add reminders in your calendar app" }
        WidgetKind.BatteryPro -> { title = "BATTERY"; value = battery?.let { "$it%" } ?: "—"; detail = "Phone battery level" }
        WidgetKind.ScreenTime -> { title = "SCREEN TIME"; value = "Permission needed"; detail = "Usage access is not enabled" }
        WidgetKind.WeatherForecast -> { title = "FORECAST"; value = "Open Weather"; detail = "Choose a location in Weather settings" }
        WidgetKind.AlbumArt -> { title = "ALBUM ART"; value = "No active media"; detail = "Play music to show its artwork" }
        WidgetKind.ActivityRings -> { title = "ACTIVITY"; value = "No activity data"; detail = "Connect a supported activity source" }
        WidgetKind.StepCounter -> { title = "STEPS"; value = "Sensor data unavailable"; detail = "No step total is assumed" }
        WidgetKind.Countdown -> { title = "COUNTDOWN"; value = "Set a target date"; detail = "Countdown setup is not configured" }
        WidgetKind.QuickNotes -> { title = "QUICK NOTES"; value = "No saved note"; detail = "Create a note in your notes app" }
        WidgetKind.Calculator -> { title = "CALCULATOR"; value = "Open Calculator"; detail = "Use your installed calculator app" }
        WidgetKind.ChargingStatus -> { title = "CHARGING"; value = battery?.let { "$it%" } ?: "—"; detail = "Battery status from Android" }
        WidgetKind.DailyQuote -> { title = "DAILY NOTE"; value = "Make today count."; detail = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
        else -> { title = "WIDGET"; value = "—"; detail = "" }
    }
    val compact = size == WidgetSize.Small
    Column(
        Modifier.fillMaxSize().padding(if (compact) 18.dp else 22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(title, color = Color.White.copy(alpha = .68f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = Color.White, fontSize = if (compact) 23.sp else 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 27.sp else 34.sp)
            Text(detail, color = Color.White.copy(alpha = .62f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
