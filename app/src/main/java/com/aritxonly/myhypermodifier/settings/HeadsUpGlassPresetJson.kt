package com.aritxonly.myhypermodifier

import org.json.JSONArray
import org.json.JSONObject

/** Portable preset format for the two SystemUI heads-up glass payloads. */
internal object HeadsUpGlassPresetJson {
    private const val VERSION = 1

    data class Preset(val regular: FloatArray, val dark: FloatArray,
        val backgroundBlurPercent: Float? = null, val glassBlurEnabled: Boolean? = null,
        val glassBlurRadius: Float? = null)

    fun export(settings: ModifierSettings, scope: GlassParameterScope = GlassParameterScope.HeadsUp): String {
        val regular = scope.values(settings, false)
        val dark = scope.values(settings, true)
        return JSONObject()
            .put("format", scope.presetFormat)
            .put("version", if (scope == GlassParameterScope.ShadeCards) 3 else VERSION)
            .apply {
                if (scope == GlassParameterScope.ShadeCards) {
                    put("mode", "relative-to-system")
                    put("parameters", JSONArray(regular.map { it.toDouble() }))
                } else {
                    put("regular", JSONArray(regular.map { it.toDouble() }))
                    put("dark", JSONArray(dark.map { it.toDouble() }))
                }
            }
            .toString(2)
    }

    fun import(serialized: String, scope: GlassParameterScope = GlassParameterScope.HeadsUp): Preset? = runCatching {
        val root = JSONObject(serialized)
        require(root.optString("format") == scope.presetFormat)
        val version = root.optInt("version")
        if (scope == GlassParameterScope.ShadeCards && version == 3) {
            require(root.optString("mode") == "relative-to-system")
            val common = readArray(root.getJSONArray("parameters"))
            return@runCatching Preset(common, common.copyOf())
        }
        if (scope == GlassParameterScope.ShadeCards && version == 2) {
            require(root.optString("mode") == "relative-to-system")
            val common = readArray(root.getJSONArray("parameters"))
            val percent = root.getDouble("backgroundBlurPercent").toFloat()
            val radius = root.getDouble("glassBlurRadius").toFloat()
            require(percent.isFinite() && percent in 0f..200f)
            require(radius.isFinite() && radius in 0f..ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat())
            return@runCatching Preset(common, common.copyOf(), percent, root.getBoolean("glassBlurEnabled"), radius)
        }
        require(version == VERSION)
        if (scope == GlassParameterScope.ShadeCards) require(root.optString("mode") == "relative-to-system")
        val radius = if (scope == GlassParameterScope.HeadsUp && root.has("glassBlurRadius")) {
            root.getDouble("glassBlurRadius").toFloat().also {
                require(it.isFinite() && it in 0f..ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS.toFloat())
            }
        } else null
        Preset(
            regular = readArray(root.getJSONArray("regular")),
            dark = readArray(root.getJSONArray("dark")),
            glassBlurEnabled = if (scope == GlassParameterScope.HeadsUp && root.has("glassBlurEnabled"))
                root.getBoolean("glassBlurEnabled") else null,
            glassBlurRadius = radius,
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
