package com.hoisly.launcher.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything a backup file holds: the stored settings and the home screen layout file. */
data class Backup(val settings: Map<String, Any>, val layout: String?)

/**
 * Reads and writes the backup file. Pure JSON, no Android types, so it is covered by JVM tests.
 * Each setting keeps its type (`b` boolean, `i` int, `l` long, `f` float, `s` string, `a` set of
 * strings), because SharedPreferences distinguishes them.
 */
object BackupCodec {
    const val FORMAT: Int = 1
    private const val MARKER = "hoisly-backup"

    fun encode(settings: Map<String, Any?>, layout: String?, createdAtMs: Long): String {
        val values = JSONObject()
        settings.forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "a").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> return@forEach
            }
            values.put(key, entry)
        }
        return JSONObject()
            .put("app", MARKER)
            .put("format", FORMAT)
            .put("created", createdAtMs)
            .put("settings", values)
            .put("layout", layout ?: JSONObject.NULL)
            .toString()
    }

    /** Null when [text] is not a backup of a known format. */
    fun decode(text: String): Backup? = try {
        val root = JSONObject(text)
        if (root.optString("app") != MARKER || root.optInt("format", -1) != FORMAT) {
            null
        } else {
            val values = root.getJSONObject("settings")
            val settings = HashMap<String, Any>()
            values.keys().forEach { key ->
                val entry = values.getJSONObject(key)
                val value: Any? = when (entry.getString("t")) {
                    "b" -> entry.getBoolean("v")
                    "i" -> entry.getInt("v")
                    "l" -> entry.getLong("v")
                    "f" -> entry.getDouble("v").toFloat()
                    "s" -> entry.getString("v")
                    "a" -> entry.getJSONArray("v").let { array -> (0 until array.length()).map(array::getString).toSet() }
                    else -> null
                }
                if (value != null) settings[key] = value
            }
            val layout = if (root.isNull("layout")) null else root.getString("layout")
            Backup(settings, layout)
        }
    } catch (_: JSONException) {
        null
    }
}
