package com.aritxonly.myhypermodifier

import org.json.JSONObject
import org.json.JSONTokener

/** A complete gesture-handle preset; importing never modifies other module settings. */
internal object GestureHandlePresetJson {
    const val MAX_LENGTH = 262_144

    data class Preset(val modulePreset: Boolean, val apps: Map<String, String>) {
        fun applyTo(settings: ModifierSettings) = settings.copy(
            gestureHandleModulePreset = modulePreset,
            gestureHandleAppModes = apps,
        )
    }

    fun export(settings: ModifierSettings): String = JSONObject()
        .put("format", "myhypermodifier-gesture-handle")
        .put("version", 1)
        .put("preset", if (settings.gestureHandleModulePreset) "module" else "system")
        .put("apps", JSONObject(settings.gestureHandleAppModes.toSortedMap()))
        .toString(2)

    fun parse(serialized: String): Preset? = runCatching {
        require(serialized.length <= MAX_LENGTH)
        val input = JSONTokener(serialized.removePrefix("\uFEFF"))
        val root = input.nextValue() as? JSONObject ?: error("Expected an object")
        require(input.nextClean() == '\u0000')
        require(root.getString("format") == "myhypermodifier-gesture-handle")
        require(root.get("version") == 1)
        require(root.keys().asSequence().toSet() == setOf("format", "version", "preset", "apps"))
        val module = when (root.get("preset")) {
            "module" -> true
            "system" -> false
            else -> error("Unknown preset")
        }
        val source = root.getJSONObject("apps")
        val apps = source.keys().asSequence().associateWith { packageName ->
            require(packageName.isNotBlank() && packageName.none(Char::isWhitespace))
            val mode = source.get(packageName)
            require(mode is String && mode in setOf(
                GestureHandleRules.SHOW, GestureHandleRules.HIDE,
                GestureHandleRules.IMMERSIVE, GestureHandleRules.SYSTEM,
            ))
            mode
        }
        Preset(module, apps)
    }.getOrNull()
}
