package com.hoisly.launcher.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local, private snapshots of Home Screen arrangements. No export/import file is shown to users. */
class HomeSetupStore(context: Context) {
    private val directory = File(context.filesDir, "home_setups").apply { mkdirs() }

    data class Setup(val id: String, val name: String, val layout: LayoutData, val savedAt: Long)

    @Synchronized
    fun all(legacyGrid: GridSpec): List<Setup> = directory.listFiles { file -> file.extension == "setup" }
        .orEmpty().mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                val layout = layoutFromJson(json.getString("layout"), legacyGrid) ?: return@runCatching null
                Setup(json.getString("id"), json.getString("name"), layout, json.optLong("savedAt"))
            }.getOrNull()
        }.sortedByDescending { it.savedAt }

    @Synchronized
    fun save(name: String, layout: LayoutData, legacyGrid: GridSpec): List<Setup> {
        val clean = name.trim().take(40)
        require(clean.isNotEmpty()) { "Setup name cannot be empty" }
        val existing = all(legacyGrid).firstOrNull { it.name.equals(clean, ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val file = File(directory, "$id.setup")
        val temp = File(directory, "$id.tmp")
        temp.writeText(JSONObject()
            .put("id", id)
            .put("name", clean)
            .put("savedAt", System.currentTimeMillis())
            .put("layout", layout.toJson())
            .toString())
        check(temp.renameTo(file)) { "Unable to save Home Screen setup" }
        return all(legacyGrid)
    }

    @Synchronized
    fun delete(id: String, legacyGrid: GridSpec): List<Setup> {
        File(directory, "$id.setup").delete()
        return all(legacyGrid)
    }
}
