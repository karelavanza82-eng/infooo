package com.hoisly.launcher.statusbar

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/** CleanBar's status-bar engine hosted by Hoisly. Shizuku therefore authorizes com.hoisly.launcher. */
object CleanBarController {
    private const val REQUEST_CODE = 7462
    private const val PREFS = "hoisly_cleanbar_engine"
    private const val BACKUP_DONE = "backup_done"
    private const val BACKUP_POLICY = "backup_policy"
    private const val BACKUP_POLICY_EXISTS = "backup_policy_exists"
    private val blacklistTables = listOf("secure", "system", "global")
    private val blacklistKey = "icon_blacklist"
    private val cleanBarSlots = listOf(
        "battery", "clock", "time", "wifi", "mobile", "phone_signal", "data_connection", "vpn",
        "airplane", "hotspot", "alarm_clock", "volume", "headset", "wireless_headset",
        "bluetooth_handsfree_battery", "bluetooth", "location", "gps", "zen", "cast", "nfc",
        "micphone", "call_record", "privacy_mode", "sync_failing", "sync_active", "camera", "mute", "managed_profile"
    )

    fun shizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun hasPermission(): Boolean = shizukuRunning() && runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (shizukuRunning() && !runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        }
    }

    fun requestCode(): Int = REQUEST_CODE

    /** Runs CleanBar's global SystemUI/immersive sequence and verifies the policy was applied. */
    fun apply(context: Context, hide: Boolean): Result<Unit> = runCatching {
        check(hasPermission()) { "Shizuku belum aktif atau izin Hoisly belum diberikan" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (hide) {
            if (!prefs.getBoolean(BACKUP_DONE, false)) captureOriginalSettings(prefs)
            // CleanBar sequence: clear demo/disable state, then disable system slots and apply global immersive policy.
            runShell("am broadcast -a com.android.systemui.demo -e command exit", allowFailure = true)
            runShell("cmd statusbar send-disable-flag none", allowFailure = true)
            runShell("cmd statusbar send-disable-flag system-icons clock notification-icons", allowFailure = true)
            runShell("settings put global policy_control 'immersive.status=*'")
            val policy = runShell("settings get global policy_control")
            check(policy.contains("immersive.status")) { "System Android tidak menerapkan immersive.status" }
            blacklistTables.forEach { table ->
                val previous = runShell("settings get $table $blacklistKey", allowFailure = true)
                val existing = previous.takeUnless { it.isBlank() || it == "null" }.orEmpty()
                val merged = (existing.split(',').filter { it.isNotBlank() } + cleanBarSlots).distinct().joinToString(",")
                runShell("settings put $table $blacklistKey ${quote(merged)}", allowFailure = true)
            }
            // A successful settings write alone is not enough: require the global policy read-back.
            prefs.edit().putBoolean("active", true).commit()
        } else {
            restoreOriginalSettings(prefs)
            runShell("cmd statusbar send-disable-flag none", allowFailure = true)
            runShell("cmd statusbar send-disable-flag-2 none", allowFailure = true)
            runShell("am broadcast -a com.android.systemui.demo -e command exit", allowFailure = true)
            prefs.edit().putBoolean("active", false).commit()
        }
        Unit
    }

    fun isActive(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("active", false)

    private fun captureOriginalSettings(prefs: android.content.SharedPreferences) {
        val policy = runShell("settings get global policy_control", allowFailure = true)
        val edit = prefs.edit()
            .putBoolean(BACKUP_DONE, true)
            .putBoolean(BACKUP_POLICY_EXISTS, policy.isNotBlank() && policy != "null")
            .putString(BACKUP_POLICY, policy.takeUnless { it == "null" }.orEmpty())
        blacklistTables.forEach { table ->
            val value = runShell("settings get $table $blacklistKey", allowFailure = true)
            edit.putBoolean("backup_${table}_exists", value.isNotBlank() && value != "null")
                .putString("backup_${table}", value.takeUnless { it == "null" }.orEmpty())
        }
        check(edit.commit()) { "Tidak dapat menyimpan cadangan pengaturan Android" }
    }

    private fun restoreOriginalSettings(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(BACKUP_DONE, false)) {
            if (prefs.getBoolean(BACKUP_POLICY_EXISTS, false)) {
                runShell("settings put global policy_control ${quote(prefs.getString(BACKUP_POLICY, "").orEmpty())}", allowFailure = true)
            } else runShell("settings delete global policy_control", allowFailure = true)
            blacklistTables.forEach { table ->
                if (prefs.getBoolean("backup_${table}_exists", false)) {
                    runShell("settings put $table $blacklistKey ${quote(prefs.getString("backup_$table", "").orEmpty())}", allowFailure = true)
                } else runShell("settings delete $table $blacklistKey", allowFailure = true)
            }
            prefs.edit().clear().putBoolean("active", false).commit()
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun runShell(command: String, allowFailure: Boolean = false): String {
        check(hasPermission()) { "Izin Shizuku Hoisly tidak tersedia" }
        val process = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
            .invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        try {
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { it.forEachLine { line -> stdout.append(line).append('\n') } }
            BufferedReader(InputStreamReader(process.errorStream)).use { it.forEachLine { line -> stderr.append(line).append('\n') } }
            val code = process.waitFor()
            if (!allowFailure) check(code == 0) { stderr.toString().trim().ifBlank { "Perintah gagal: $command (exit $code)" } }
            return stdout.toString().trim()
        } finally { process.destroy() }
    }
}
