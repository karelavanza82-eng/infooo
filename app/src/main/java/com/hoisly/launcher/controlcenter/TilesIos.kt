package com.hoisly.launcher.controlcenter

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import kotlinx.coroutines.delay

/** Rotation lock: "active" means auto-rotate is OFF (portrait locked), like the reference. */
class RotationLockTile : Tile("rotlock") {
    override fun read(ctx: Context) = TileUi(
        id, "Rotation lock", IosIcons.RotationLock,
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
    )

    override suspend fun click(host: TileHost): Boolean? {
        val ctx = host.context
        val locked = read(ctx).active
        if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
            return !locked
        }
        host.launch(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }
}

/** The bell: active when the ringer is silent or vibrating. */
class SilentTile : Tile("silent") {
    override fun read(ctx: Context): TileUi {
        val silent = ctx.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL
        return TileUi(id, "Silent", if (silent) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications, silent)
    }

    override suspend fun click(host: TileHost): Boolean? {
        val ctx = host.context
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val silent = am.ringerMode != AudioManager.RINGER_MODE_NORMAL
        return try {
            am.ringerMode = when {
                silent -> AudioManager.RINGER_MODE_NORMAL
                nm.isNotificationPolicyAccessGranted -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_VIBRATE
            }
            !silent
        } catch (_: SecurityException) {
            host.launch(settingsIntent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            null
        }
    }

    override fun longClick(host: TileHost) = host.launch(settingsIntent(Settings.ACTION_SOUND_SETTINGS))
}

/**
 * Starts the system screen recorder's dialog. Needs Shizuku (SystemUI's dialog isn't exported);
 * without it the tile does nothing.
 */
class ScreenRecordTile : Tile("screenrecord") {
    override val needsShizuku = true

    override fun read(ctx: Context) = TileUi(id, "Screen Recording", IosIcons.ScreenRecord, false, stateless = true)

    override suspend fun click(host: TileHost): Boolean? {
        host.collapse()
        delay(350)
        Shell.run("am start -n com.android.systemui/.screenrecord.ScreenRecordDialog")
        return null
    }
}

internal fun iosTile(id: String): Tile? = when (id) {
    "rotlock" -> RotationLockTile()
    "silent" -> SilentTile()
    "screenrecord" -> ScreenRecordTile()
    "qr" -> LaunchTile(id, "Scan Code", Icons.Rounded.QrCodeScanner) {
        // Google Lens reads QR codes; fall back to the camera if it isn't installed.
        it.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            ?: Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    }
    "mirror" -> LaunchTile(id, "Screen Mirroring", IosIcons.ScreenMirror) { Intent(Settings.ACTION_CAST_SETTINGS) }
    "shazam" -> LaunchTile(id, "Music Recognition", IosIcons.Shazam) {
        it.packageManager.getLaunchIntentForPackage("com.shazam.android")
            ?: it.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
    }
    else -> null
}
