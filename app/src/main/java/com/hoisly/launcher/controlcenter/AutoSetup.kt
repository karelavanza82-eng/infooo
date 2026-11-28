package com.hoisly.launcher.controlcenter

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** What the Control Center needs, in the order the guided setup asks for it. */
enum class SetupStep { Runtime, WriteSettings, Dnd, NotificationAccess, Accessibility }

/** Detects missing access and, when Shizuku is allowed, grants every item without opening Settings. */
object AutoSetup {
    private val RUNTIME = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT)

    val runtimePermissions: Array<String> get() = RUNTIME

    fun isDone(ctx: Context, step: SetupStep): Boolean = when (step) {
        SetupStep.Runtime -> RUNTIME.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        SetupStep.WriteSettings -> Settings.System.canWrite(ctx)
        SetupStep.Dnd -> ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        SetupStep.NotificationAccess ->
            enabled(ctx, "enabled_notification_listeners", NotifListener.component(ctx))
        SetupStep.Accessibility -> ShadeService.running != null ||
            enabled(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentName(ctx, ShadeService::class.java))
    }

    fun nextMissing(ctx: Context, skip: Set<SetupStep> = emptySet()): SetupStep? =
        SetupStep.entries.firstOrNull { it !in skip && !isDone(ctx, it) }

    private fun enabled(ctx: Context, key: String, c: ComponentName): Boolean =
        Settings.Secure.getString(ctx.contentResolver, key)?.split(':')?.any {
            it == c.flattenToString() || it == c.flattenToShortString()
        } == true

    /**
     * With Shizuku allowed, grants all access in one go. Returns false when Shizuku is not available,
     * in which case the caller falls back to the guided Settings flow.
     */
    suspend fun grantAllWithShizuku(ctx: Context): Boolean {
        if (!Shell.available()) return false
        val pkg = ctx.packageName
        val listener = NotifListener.component(ctx).flattenToShortString()
        val a11y = ComponentName(ctx, ShadeService::class.java).flattenToShortString()
        Shell.run("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        RUNTIME.forEach { Shell.run("pm grant $pkg $it") }
        Shell.run("appops set $pkg WRITE_SETTINGS allow")
        Shell.run("cmd notification allow_dnd $pkg")
        Shell.run("cmd notification allow_listener $pkg/$listener")
        // Append instead of replacing, so Dynamic Island and other services stay enabled.
        Shell.run(
            "cur=\$(settings get secure enabled_accessibility_services); " +
                "case \"\$cur\" in *$pkg/$a11y*) ;; null|'') settings put secure enabled_accessibility_services $pkg/$a11y ;; " +
                "*) settings put secure enabled_accessibility_services \"\$cur:$pkg/$a11y\" ;; esac; " +
                "settings put secure accessibility_enabled 1",
        )
        return true
    }
}
