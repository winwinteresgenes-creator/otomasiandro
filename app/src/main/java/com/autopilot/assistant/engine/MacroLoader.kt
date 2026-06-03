package com.autopilot.assistant.engine

import android.content.Context
import com.autopilot.assistant.model.Macro
import java.io.File
import org.json.JSONObject

/**
 * Loads [Macro] definitions from JSON. Two sources are merged:
 *  1. Bundled example ".json" files in the "assets/macros" folder (shipped
 *     with the APK).
 *  2. User-editable ".json" files in the app's external files dir under
 *     "macros", i.e. "Android/data/<package>/files/macros" — drop new flows
 *     there to add or override automations without rebuilding the app.
 *
 * A user file overrides a bundled macro with the same id.
 */
object MacroLoader {

    private const val ASSET_DIR = "macros"
    private const val USER_DIR = "macros"

    fun loadAll(context: Context): List<Macro> {
        val byId = LinkedHashMap<String, Macro>()
        loadFromAssets(context).forEach { byId[it.id] = it }
        loadFromUserDir(context).forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun userMacroDir(context: Context): File =
        File(context.getExternalFilesDir(null), USER_DIR).apply { mkdirs() }

    private fun loadFromAssets(context: Context): List<Macro> {
        val assets = context.assets
        val files = assets.list(ASSET_DIR)?.filter { it.endsWith(".json") } ?: emptyList()
        return files.mapNotNull { name ->
            runCatching {
                val json = assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
                Macro.fromJson(JSONObject(json))
            }.getOrNull()
        }
    }

    private fun loadFromUserDir(context: Context): List<Macro> {
        val dir = userMacroDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { Macro.fromJson(JSONObject(file.readText())) }.getOrNull()
        }
    }
}
