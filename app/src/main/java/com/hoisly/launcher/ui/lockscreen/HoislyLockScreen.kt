package com.hoisly.launcher.ui.lockscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A decorative launcher overlay; it is deliberately not an Android security/keyguard replacement. */
@Composable
fun HoislyLockScreen(onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF06345C), Color(0xFF0875A5), Color(0xFF06182D))
                )
            )
            .pointerInput(Unit) {
                var dragDistance = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = { if (dragDistance < -65f) onDismiss(); dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                )
            }
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(date, color = Color.White.copy(alpha = .92f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                time,
                color = Color.White.copy(alpha = .72f),
                fontSize = 112.sp,
                lineHeight = 116.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-5).sp,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Swipe up to continue",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp),
            color = Color.White.copy(alpha = .85f), fontSize = 14.sp
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 86.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(54.dp).background(Color.White.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FlashlightOn, contentDescription = "Flashlight", tint = Color.White)
            }
            Box(Modifier.size(54.dp).background(Color.White.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = "Camera", tint = Color.White)
            }
        }
    }
}
