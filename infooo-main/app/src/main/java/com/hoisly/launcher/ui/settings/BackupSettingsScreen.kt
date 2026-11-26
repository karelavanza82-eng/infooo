package com.hoisly.launcher.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hoisly.launcher.R
import com.hoisly.launcher.data.BackupCodec
import com.hoisly.launcher.settings.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val LAYOUT_FILE = "layout.json"

/** Backup and Restore: one JSON file, chosen by the user through Android's file picker. */
@Composable
fun BackupSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { writeBackup(context, vm.settings, uri) }
            toast(context, if (ok) R.string.backup_done else R.string.backup_failed)
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    SettingsPage(stringResource(R.string.backup_title), onBack) {
        item {
            SettingsCard {
                NavRow(Icons.Rounded.Backup, stringResource(R.string.backup_export), onClick = { create.launch("hoisly-backup.json") }, tileColor = SettingsStyle.Tile.Green)
                NavRow(Icons.Rounded.Restore, stringResource(R.string.backup_restore), onClick = { open.launch(arrayOf("application/json", "*/*")) }, tileColor = SettingsStyle.Tile.Blue)
            }
            SectionFooter(stringResource(R.string.backup_footer))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { restoreBackup(context, vm.settings, uri) }
                        toast(context, if (result == RestoreResult.Done) R.string.backup_restored else R.string.backup_invalid)
                        if (result == RestoreResult.Done) {
                            delay(700)
                            // The launcher keeps the layout in memory; a fresh process reads the restored files.
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore_action)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class RestoreResult { Done, Invalid }

private fun toast(context: Context, message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private fun writeBackup(context: Context, settings: LauncherSettings, uri: Uri): Boolean = runCatching {
    val layout = File(context.filesDir, LAYOUT_FILE).takeIf { it.exists() }?.readText()
    val text = BackupCodec.encode(settings.exportValues(), layout, System.currentTimeMillis())
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
}.getOrDefault(false)

private fun restoreBackup(context: Context, settings: LauncherSettings, uri: Uri): RestoreResult = runCatching {
    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    val backup = text?.let(BackupCodec::decode) ?: return@runCatching RestoreResult.Invalid
    // The layout goes first through a temporary file, so a failed write never leaves half a layout.
    backup.layout?.let { layout ->
        val target = File(context.filesDir, LAYOUT_FILE)
        val temp = File(context.filesDir, "$LAYOUT_FILE.restore")
        temp.writeText(layout)
        if (!temp.renameTo(target)) { target.writeText(layout); temp.delete() }
    }
    if (settings.importValues(backup.settings)) RestoreResult.Done else RestoreResult.Invalid
}.getOrDefault(RestoreResult.Invalid)
