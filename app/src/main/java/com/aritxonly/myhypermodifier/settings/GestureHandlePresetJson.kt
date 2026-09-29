package com.aritxonly.myhypermodifier

import org.json.JSONObject
import org.json.JSONTokener

/** A complete gesture-handle preset; importing never modifies other module settings. */
internal object GestureHandlePresetJson {
    const val MAX_LENGTH = 262_144

    data class Preset(
        val preset: String,
        val apps: Map<String, String>,
        val touchReveal: Boolean,
        val swipeMotion: Boolean,
        val bottomTouchAreaDp: Float,
    ) {
        fun applyTo(settings: ModifierSettings) = settings.copy(
            gestureHandlePreset = preset,
            gestureHandleAppModes = apps,
            gestureHandleTouchReveal = touchReveal,
            gestureHandleSwipeMotion = swipeMotion,
            gestureHandleTouchAreaDp = bottomTouchAreaDp,
        )
    }

    fun export(settings: ModifierSettings): String = JSONObject()
        .put("format", "myhypermodifier-gesture-handle")
        .put("version", 1)
        .put("preset", settings.gestureHandlePreset)
        .put("apps", JSONObject((if (settings.gestureHandlePreset == GestureHandlePresets.MODULE)
            GestureHandlePresets.MODULE_APPS + settings.gestureHandleAppModes
        else settings.gestureHandleAppModes).toSortedMap()))
        .put("touchReveal", settings.gestureHandleTouchReveal)
        .put("swipeMotion", settings.gestureHandleSwipeMotion)
        .put("bottomTouchAreaDp", GestureHandleTouchArea.normalize(settings.gestureHandleTouchAreaDp))
        .toString(2)

    fun parse(serialized: String): Preset? = runCatching {
        require(serialized.length <= MAX_LENGTH)
        val input = JSONTokener(serialized.removePrefix("\uFEFF"))
        val root = input.nextValue() as? JSONObject ?: error("Expected an object")
        require(input.nextClean() == '\u0000')
        require(root.getString("format") == "myhypermodifier-gesture-handle")
        require(root.get("version") == 1)
        require(root.keys().asSequence().toSet().let { keys ->
            keys.containsAll(setOf("format", "version", "preset", "apps")) &&
                keys.all { it in setOf("format", "version", "preset", "apps", "touchReveal", "swipeMotion",
                    "bottomTouchAreaDp") }
        })
        val preset = when (val value = root.get("preset")) {
            "system" -> GestureHandlePresets.SHOW
            is String -> value.takeIf(GestureHandlePresets::valid) ?: error("Unknown preset")
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
        val touchReveal = optionalBoolean(root, "touchReveal")
        val swipeMotion = optionalBoolean(root, "swipeMotion")
        val bottomTouchAreaDp = if (root.has("bottomTouchAreaDp")) {
            val raw = root.get("bottomTouchAreaDp")
            require(raw is Number)
            raw.toFloat().also { require(!it.isNaN() && !it.isInfinite() &&
                it in 0f..GestureHandleTouchArea.MAX_DP) }
        } else GestureHandleTouchArea.DEFAULT_DP
        Preset(preset, apps, touchReveal, swipeMotion, bottomTouchAreaDp)
    }.getOrNull()

    private fun optionalBoolean(root: JSONObject, key: String): Boolean =
        if (root.has(key)) root.get(key) as? Boolean ?: error("Invalid $key") else false
}
