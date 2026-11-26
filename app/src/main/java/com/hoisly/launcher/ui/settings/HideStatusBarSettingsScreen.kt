package com.hoisly.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.hoisly.launcher.statusbar.CleanBarController

@Composable
fun HideStatusBarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(CleanBarController.isActive(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { revision++ }
        val dead = Shizuku.OnBinderDeadListener { revision++ }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> revision++ }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    LaunchedEffect(revision) { active = CleanBarController.isActive(context) }
    val running = remember(revision) { CleanBarController.shizukuRunning() }
    val allowed = remember(revision) { CleanBarController.hasPermission() }

    SettingsPage("Hide Status Bar", onBack) {
        item {
            SettingsCard {
                SwitchRow(
                    title = "Hide System Status Bar",
                    checked = active,
                    onCheckedChange = { hide ->
                        if (busy) return@SwitchRow
                        if (!running) {
                            message = "Jalankan Shizuku terlebih dahulu, lalu kembali ke Hoisly."
                        } else if (!allowed) {
                            CleanBarController.requestPermission()
                            message = "Pilih Allow untuk Hoisly pada dialog Shizuku."
                            revision++
                        } else {
                            busy = true
                            message = "Menerapkan pengaturan sistem…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { CleanBarController.apply(context, hide) }
                                busy = false
                                if (result.isSuccess) {
                                    active = hide
                                    message = if (hide) "Perintah CleanBar berhasil diterapkan. Status bar sistem diminta tersembunyi secara global." else "Pengaturan status bar dipulihkan."
                                } else {
                                    active = CleanBarController.isActive(context)
                                    message = "Gagal: ${result.exceptionOrNull()?.message ?: "perintah Shizuku gagal"}"
                                }
                            }
                        }
                    },
                    description = if (busy) "Sedang memproses…" else "Aktifkan untuk menyembunyikan status bar Android di seluruh aplikasi.",
                )
            }
        }
        item {
            SettingsCard {
                Text(
                    when {
                        !running -> "Shizuku belum terhubung. Buka Shizuku dan pastikan servicenya berjalan."
                        !allowed -> "Shizuku aktif. Hoisly belum diberi izin. Tekan tombol di bawah dan setujui permintaan atas nama Hoisly."
                        else -> "Shizuku terhubung dan izin Hoisly telah diberikan."
                    },
                    modifier = Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 12.dp),
                )
                if (!running || !allowed) {
                    TextButton(onClick = {
                        if (running) {
                            CleanBarController.requestPermission()
                            message = "Setujui permintaan izin untuk Hoisly di dialog Shizuku."
                        } else {
                            runCatching {
                                val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (launch != null) context.startActivity(launch)
                                else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")))
                            }.onFailure { message = "Tidak dapat membuka Shizuku: ${it.message}" }
                        }
                    }) { Text(if (running) "Izinkan Hoisly di Shizuku" else "Buka Shizuku") }
                }
                message?.let { Text(it, modifier = Modifier.padding(horizontal = SettingsStyle.RowPadding, vertical = 8.dp)) }
            }
        }
        item {
            SectionFooter("Mesin CleanBar berjalan di dalam aplikasi Hoisly. Shizuku akan menampilkan nama Hoisly sebagai aplikasi peminta izin; tidak perlu memasang atau memberi izin kepada aplikasi CleanBar terpisah.")
        }
        item {
            SettingsCard {
                NavRow(
                    Icons.Rounded.VisibilityOff,
                    "Pulihkan status bar",
                    onClick = {
                        if (!running || !allowed) message = "Hubungkan Shizuku dan berikan izin Hoisly terlebih dahulu."
                        else if (!busy) {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { CleanBarController.apply(context, false) }
                                busy = false
                                if (result.isSuccess) { active = false; message = "Status bar dipulihkan." }
                                else message = "Gagal memulihkan: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    tileColor = SettingsStyle.Tile.Green,
                )
            }
        }
    }
}
