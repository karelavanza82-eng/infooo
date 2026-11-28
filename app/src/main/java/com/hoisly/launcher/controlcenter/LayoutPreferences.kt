package com.hoisly.launcher.controlcenter

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-element size/position overrides for the Control Center layout.
 *
 * Every "slot" in the CC has a stable string id:
 *   "media"       – the music/media card (width × height)
 *   "connectivity"– the 2×2 WiFi/BT/etc block
 *   "brightness"  – the vertical brightness slider pill
 *   "volume"      – the vertical volume slider pill
 *   "pill0"       – the first wide pill  (Low Power Mode / etc)
 *   "pill1"       – the second wide pill (Do Not Disturb / etc)
 *
 * Each slot stores a [SlotPrefs] with independent width/height scale factors and
 * an optional (dx, dy) offset from the slot's default position.
 *
 * Values are intentionally narrow so the layout cannot break badly:
 *   scale  → 0.60 … 1.60  (60 % … 160 % of default)
 *   offset → −80 … +80 dp on each axis
 */
data class SlotPrefs(
    val scaleW: Float = 1f,
    val scaleH: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
) {
    fun coerced() = copy(
        scaleW = scaleW.coerceIn(0.60f, 1.60f),
        scaleH = scaleH.coerceIn(0.60f, 1.60f),
        dx = dx.coerceIn(-80f, 80f),
        dy = dy.coerceIn(-80f, 80f),
    )
}

val LocalLayoutPrefs = staticCompositionLocalOf<Map<String, SlotPrefs>> { emptyMap() }

object LayoutPreferencesStore {

    private const val PREFS_FILE = "hoisly_cc_layout"
    private val SLOT_IDS = listOf("media", "connectivity", "brightness", "volume", "pill0", "pill1")

    private var flow: MutableStateFlow<Map<String, SlotPrefs>>? = null

    @Synchronized
    fun prefs(ctx: Context): StateFlow<Map<String, SlotPrefs>> =
        flow ?: MutableStateFlow(load(ctx)).also { flow = it }

    fun save(ctx: Context, id: String, value: SlotPrefs) {
        val clean = value.coerced()
        val next = prefs(ctx).value.toMutableMap().apply { put(id, clean) }
        persist(ctx, next)
        (prefs(ctx) as MutableStateFlow).value = next
    }

    fun reset(ctx: Context, id: String) {
        val next = prefs(ctx).value.toMutableMap().apply { remove(id) }
        persist(ctx, next)
        (prefs(ctx) as MutableStateFlow).value = next
    }

    fun resetAll(ctx: Context) {
        persist(ctx, emptyMap())
        (prefs(ctx) as MutableStateFlow).value = emptyMap()
    }

    private fun persist(ctx: Context, map: Map<String, SlotPrefs>) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val ed = p.edit()
        SLOT_IDS.forEach { id ->
            val s = map[id]
            if (s != null) {
                ed.putFloat("${id}_sw", s.scaleW)
                   .putFloat("${id}_sh", s.scaleH)
                   .putFloat("${id}_dx", s.dx)
                   .putFloat("${id}_dy", s.dy)
            } else {
                ed.remove("${id}_sw").remove("${id}_sh").remove("${id}_dx").remove("${id}_dy")
            }
        }
        ed.apply()
    }

    private fun load(ctx: Context): Map<String, SlotPrefs> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        return SLOT_IDS.mapNotNull { id ->
            if (!p.contains("${id}_sw")) return@mapNotNull null
            id to SlotPrefs(
                scaleW = p.getFloat("${id}_sw", 1f),
                scaleH = p.getFloat("${id}_sh", 1f),
                dx = p.getFloat("${id}_dx", 0f),
                dy = p.getFloat("${id}_dy", 0f),
            ).coerced()
        }.toMap()
    }
}
