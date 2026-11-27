package com.hoisly.launcher.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.island.core.IslandBus
import com.akira.island.update.DownloadState
import com.akira.island.update.UpdateChecker
import com.akira.island.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Row "Cek pembaruan" for the main settings card, placed under the version row.
 * Uses the same [UpdateChecker] (GitHub releases of this repo) as the Dynamic Island settings,
 * so it updates the whole Hiosly APK.
 */
@Composable
fun CheckUpdateTile(modifier: Modifier = Modifier, index: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateChecker.state.collectAsState()
    val download by UpdateChecker.download.collectAsState()
    val lang = LocalConfiguration.current.locales[0].language
    val indo = lang == "in" || lang == "id"
    fun t(id: String, en: String) = if (indo) id else en

    var showDialog by remember { mutableStateOf(false) }
    var userChecked by remember { mutableStateOf(false) }

    // After a manual check that found a newer release, open the dialog right away.
    LaunchedEffect(state) {
        if (userChecked && state is UpdateState.Available) showDialog = true
    }

    val value: String? = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> t("Memeriksa…", "Checking…")
        UpdateState.UpToDate -> t("Sudah terbaru", "Up to date")
        is UpdateState.Available -> t("v${s.release.version} tersedia", "v${s.release.version} available")
        is UpdateState.Error -> t("Gagal", "Failed")
    }

    Box(modifier) {
    BentoTile(
        icon = Icons.Rounded.SystemUpdate,
        title = t("Cek pembaruan", "Check updates"),
        subtitle = value ?: t("Ketuk untuk cek", "Tap to check"),
        color = SettingsStyle.Tile.Blue,
        kind = TileKind.Compact,
        index = index,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            val s = state
            if (s is UpdateState.Available) {
                showDialog = true
            } else if (s !is UpdateState.Checking) {
                userChecked = true
                scope.launch { UpdateChecker.check(context.applicationContext, force = true) }
            }
        },
    )

    val release = (state as? UpdateState.Available)?.release
    if (showDialog && release != null) {
        val d = download
        val ready = UpdateChecker.readyFile(release, d)
        val running = d as? DownloadState.Running
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1C1C1E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.75f),
            title = { Text(t("Versi ${release.version} tersedia", "Version ${release.version} is available")) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    val size = if (release.sizeBytes > 0) " · %.1f MB".format(release.sizeBytes / 1_048_576.0) else ""
                    Text(
                        t("Terpasang: ${UpdateChecker.currentVersion(context)}$size", "Installed: ${UpdateChecker.currentVersion(context)}$size"),
                        fontSize = 14.sp,
                    )
                    if (running != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { running.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            t("Mengunduh… ${(running.progress * 100).toInt()}%", "Downloading… ${(running.progress * 100).toInt()}%"),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (d is DownloadState.Failed) {
                        Text(
                            t("Gagal mengunduh. Periksa internet lalu coba lagi.", "Download failed. Check your internet and try again."),
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (ready != null) {
                        Text(
                            t(
                                "Jika layar pemasangan tidak muncul, izinkan Hiosly memasang aplikasi lalu ketuk Pasang lagi.",
                                "If the installer doesn't open, allow Hiosly to install apps and tap Install again.",
                            ),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    running != null -> TextButton(onClick = {}, enabled = false) { Text(t("Mengunduh…", "Downloading…")) }
                    ready != null -> TextButton(onClick = { UpdateChecker.install(context, ready, release) }) { Text(t("Pasang", "Install")) }
                    else -> TextButton(onClick = {
                        IslandBus.scope.launch { UpdateChecker.downloadAndInstall(context.applicationContext, release) }
                    }) { Text(t("Perbarui", "Update")) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(t("Nanti", "Later")) }
            },
        )
    }
    }
}
