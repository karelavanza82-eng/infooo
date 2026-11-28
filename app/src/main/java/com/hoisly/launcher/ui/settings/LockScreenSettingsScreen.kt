package com.hoisly.launcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hoisly.launcher.R
import com.hoisly.launcher.lockscreen.HoislyLockScreenActivity

@Composable
fun LockScreenSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali") }
        Text("Hoisly Lock Screen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Rekonstruksi awal preset iOS 26 beta2 dari KLCK. Tampilan ini berjalan sebagai Activity Hoisly; PIN, pola, sidik jari, dan proses autentikasi tetap ditangani Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp))) {
            Image(painterResource(R.drawable.ios26_lock_wallpaper), "Pratinjau wallpaper preset", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x22001840)))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White)
                Text("Hoisly Lock Screen", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Wallpaper preset iOS 26", color = Color.White)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            context.startActivity(Intent(context, HoislyLockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Text("  Buka pratinjau Lock Screen")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Catatan: Android biasa tidak mengizinkan launcher menggantikan Keyguard sistem sepenuhnya. Aktivitas ini dapat tampil di atas layar terkunci, tetapi swipe-up selalu meminta Android melakukan unlock. Tampilan otomatis saat setiap layar menyala belum diaktifkan karena pembatasan peluncuran Activity dari background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
