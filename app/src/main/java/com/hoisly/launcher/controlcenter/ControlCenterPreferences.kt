package com.hoisly.launcher.controlcenter

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User-adjustable geometry and translucency for the Hoisly Control Center. */
data class ControlCenterPreferences(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val backgroundOpacity: Float = 1f,
) {
    companion object {
        private const val FILE = "hoisly_control_center"
        private val mutable = MutableStateFlow(ControlCenterPreferences())
        val state: StateFlow<ControlCenterPreferences> = mutable
        @Volatile private var initialized = false

        @Synchronized
        fun init(context: Context) {
            if (initialized) return
            val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            mutable.value = ControlCenterPreferences(
                scale = p.getFloat("scale", 1f).coerceIn(0.75f, 1.2f),
                offsetX = p.getFloat("offset_x", 0f).coerceIn(-48f, 48f),
                offsetY = p.getFloat("offset_y", 0f).coerceIn(-48f, 48f),
                backgroundOpacity = p.getFloat("background_opacity", 1f).coerceIn(0.1f, 1f),
            )
            initialized = true
        }

        @Synchronized
        fun update(context: Context, change: (ControlCenterPreferences) -> ControlCenterPreferences) {
            val next = change(mutable.value).let {
                it.copy(
                    scale = it.scale.coerceIn(0.75f, 1.2f),
                    offsetX = it.offsetX.coerceIn(-48f, 48f),
                    offsetY = it.offsetY.coerceIn(-48f, 48f),
                    backgroundOpacity = it.backgroundOpacity.coerceIn(0.1f, 1f),
                )
            }
            mutable.value = next
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putFloat("scale", next.scale)
                .putFloat("offset_x", next.offsetX)
                .putFloat("offset_y", next.offsetY)
                .putFloat("background_opacity", next.backgroundOpacity)
                .apply()
        }

        fun reset(context: Context) = update(context) { ControlCenterPreferences() }
    }
}
