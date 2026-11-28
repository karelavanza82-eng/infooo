package com.hoisly.launcher.lockscreen

import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.hoisly.launcher.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hoisly's KLCK-inspired lock-screen surface.
 * Android Keyguard remains the security authority: this Activity never unlocks the device itself.
 */
class HoislyLockScreenActivity : ComponentActivity() {
    private var torchEnabled = false
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setTorch() else Toast.makeText(this, "Izin kamera diperlukan untuk senter", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        setContent {
            HoislyLockScreen(
                onUnlock = ::requestSystemUnlock,
                onTorch = ::toggleTorch,
                onCamera = {
                    runCatching {
                        startActivity(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
                    }.onFailure {
                        Toast.makeText(this, "Kamera tidak tersedia", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    override fun onStop() {
        if (torchEnabled && checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCatching {
                val manager = getSystemService(CameraManager::class.java)
                val cameraId = manager.cameraIdList.firstOrNull { id ->
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (cameraId != null) manager.setTorchMode(cameraId, false)
            }
            torchEnabled = false
        }
        super.onStop()
    }

    private fun toggleTorch() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(android.Manifest.permission.CAMERA)
        } else {
            setTorch()
        }
    }

    private fun setTorch() {
        runCatching {
            val manager = getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: error("Flash tidak tersedia")
            torchEnabled = !torchEnabled
            manager.setTorchMode(cameraId, torchEnabled)
        }.onFailure {
            torchEnabled = false
            Toast.makeText(this, "Senter tidak dapat diaktifkan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestSystemUnlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }

            override fun onDismissCancelled() {
                // Keep the Hoisly surface visible; Android keeps the device protected.
            }

            override fun onDismissError() {
                Toast.makeText(this@HoislyLockScreenActivity, "Gunakan keamanan layar Android untuk membuka", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

private val IosRegular = FontFamily(Font(R.font.ios_sf_regular, FontWeight.Normal))
private val IosMedium = FontFamily(Font(R.font.ios_sf_medium, FontWeight.Medium))
private val IosBold = FontFamily(Font(R.font.ios_sf_bold, FontWeight.Bold))

@Composable
private fun HoislyLockScreen(onUnlock: () -> Unit, onTorch: () -> Unit, onCamera: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1_000)
        }
    }
    val clock = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(now) }
    val pull by animateFloatAsState(
        targetValue = (dragDistance / 180f).coerceIn(0f, 1f),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "unlockPull",
    )

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, amount -> dragDistance = (dragDistance - amount).coerceIn(0f, 220f) },
                onDragEnd = {
                    if (dragDistance > 72f) onUnlock()
                    dragDistance = 0f
                },
                onDragCancel = { dragDistance = 0f },
            )
        },
    ) {
        Image(
            painter = painterResource(R.drawable.ios26_lock_wallpaper),
            contentDescription = "Wallpaper iOS 26 dari preset KLCK",
            modifier = Modifier.fillMaxSize().scale(1.04f),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x18001840), Color(0x08001840), Color(0x50001840)))))

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                Text("Hoisly", color = Color.White.copy(alpha = 0.92f), fontFamily = IosBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(42.dp))
            Icon(Icons.Rounded.Lock, contentDescription = "Perangkat terkunci", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(12.dp))
            Text(date.replaceFirstChar { it.titlecase(Locale("id", "ID")) }, color = Color.White.copy(alpha = 0.95f), fontFamily = IosMedium, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                clock,
                color = Color.White.copy(alpha = 0.94f),
                fontFamily = IosRegular,
                fontWeight = FontWeight.Light,
                fontSize = 112.sp,
                lineHeight = 116.sp,
                letterSpacing = (-7).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().scale(1f - pull * 0.06f),
            )
            Spacer(Modifier.weight(1f))
            Text("Geser ke atas untuk membuka", color = Color.White.copy(alpha = 0.9f), fontFamily = IosMedium, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LockCircle(onClick = onTorch) {
                    Icon(Icons.Rounded.FlashlightOn, contentDescription = "Senter", tint = Color.White, modifier = Modifier.size(25.dp))
                }
                LockCircle(onClick = onCamera) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Kamera", tint = Color.White, modifier = Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(width = 108.dp, height = 5.dp).background(Color.White.copy(alpha = 0.95f), CircleShape))
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun LockCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(58.dp)
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

