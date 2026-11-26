package com.hoisly.launcher.statusbar

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Hoisly's in-app CleanBar integration. Uses Shizuku shell to set Android's global immersive
 * status policy (not Activity window flags), and backs up the previous policy before changing it.
 */
object CleanBarController {
    private const val REQUEST_CODE = 7462
    private const val PREFS = "hoisly_cleanbar"
    private const val BACKUP_POLICY = "previous_global_policy_control"
    private const val BACKUP_EXISTS = "previous_global_policy_exists"
    private const val BACKUP_CAPTURED = "previous_global_policy_captured"

    fun shizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = shizukuRunning() && runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (!shizukuRunning() || runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun requestCode(): Int = REQUEST_CODE

    fun apply(context: Context, hide: Boolean): Result<Unit> {
        if (!hasPermission()) return Result.failure(IllegalStateException("Shizuku belum aktif atau izin belum diberikan"))
        return runCatching {
            if (hide) {
                val current = shell("settings get global policy_control")
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (!prefs.getBoolean(BACKUP_CAPTURED, false)) {
                    prefs.edit()
                        .putBoolean(BACKUP_CAPTURED, true)
                        .putBoolean(BACKUP_EXISTS, current != "null")
                        .putString(BACKUP_POLICY, current.takeUnless { it == "null" }.orEmpty())
                        .commit()
                }
                shell("settings put global policy_control 'immersive.status=*'")
                val verify = shell("settings get global policy_control")
                check(verify.contains("immersive.status")) { "Android tidak menerima kebijakan immersive status bar" }
            } else {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean(BACKUP_CAPTURED, false)) {
                    if (prefs.getBoolean(BACKUP_EXISTS, false)) {
                        val previous = prefs.getString(BACKUP_POLICY, "").orEmpty()
                        shell("settings put global policy_control ${quote(previous)}")
                    } else {
                        shell("settings delete global policy_control")
                    }
                    prefs.edit().remove(BACKUP_POLICY).remove(BACKUP_EXISTS).remove(BACKUP_CAPTURED).commit()
                }
            }
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun shell(command: String): String {
        check(hasPermission()) { "Shizuku tidak memiliki izin" }
        val process = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
            .invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        try {
            val output = StringBuilder()
            val errors = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { it.forEachLine { line -> output.append(line).append('\n') } }
            BufferedReader(InputStreamReader(process.errorStream)).use { it.forEachLine { line -> errors.append(line).append('\n') } }
            val code = process.waitFor()
            check(code == 0) { errors.toString().ifBlank { "Perintah gagal (exit $code)" } }
            return output.toString().trim()
        } finally {
            process.destroy()
        }
    }
}
