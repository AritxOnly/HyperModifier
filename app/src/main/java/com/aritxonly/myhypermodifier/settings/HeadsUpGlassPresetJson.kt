package com.aritxonly.myhypermodifier

import org.json.JSONArray
import org.json.JSONObject

/** Portable preset format for the two SystemUI heads-up glass payloads. */
internal object HeadsUpGlassPresetJson {
    private const val VERSION = 1

    data class Preset(val regular: FloatArray, val dark: FloatArray)

    fun export(settings: ModifierSettings): String {
        val regular = HeadsUpGlassParameters.parseSerializedOrDefault(
            settings.headsUpGlassParameters,
            HeadsUpGlassParameters.regularDefault,
        )
        val dark = HeadsUpGlassParameters.parseSerializedOrDefault(
            settings.headsUpGlassDarkParameters,
            HeadsUpGlassParameters.darkDefault,
        )
        return JSONObject()
            .put("format", "myhypermodifier-heads-up-glass")
            .put("version", VERSION)
            .put("regular", JSONArray(regular.map { it.toDouble() }))
            .put("dark", JSONArray(dark.map { it.toDouble() }))
            .toString(2)
    }

    fun import(serialized: String): Preset? = runCatching {
        val root = JSONObject(serialized)
        require(root.optString("format") == "myhypermodifier-heads-up-glass")
        require(root.optInt("version") == VERSION)
        Preset(
            regular = readArray(root.getJSONArray("regular")),
            dark = readArray(root.getJSONArray("dark")),
        )
    }.getOrNull()

    private fun readArray(values: JSONArray): FloatArray {
        require(values.length() == HeadsUpGlassParameters.COUNT)
        return FloatArray(HeadsUpGlassParameters.COUNT) { index ->
            val value = values.getDouble(index).toFloat()
            require(!value.isNaN() && !value.isInfinite())
            value
        }
    }
}
