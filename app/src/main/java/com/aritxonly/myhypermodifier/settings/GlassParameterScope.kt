package com.aritxonly.myhypermodifier

/** One editor, with separate persisted recipes and JSON formats for each target surface. */
internal enum class GlassParameterScope(val presetFormat: String) {
    HeadsUp("myhypermodifier-heads-up-glass"),
    ShadeCards("myhypermodifier-shade-card-glass");

    fun defaults(dark: Boolean): FloatArray = when (this) {
        HeadsUp -> if (dark) HeadsUpGlassParameters.darkDefault else HeadsUpGlassParameters.regularDefault
        ShadeCards -> ShadeCardGlassPolicy.defaults()
    }

    fun enabled(settings: ModifierSettings): Boolean = when (this) {
        HeadsUp -> settings.headsUpGlassParametersEnabled
        ShadeCards -> settings.shadeCardGlassParametersEnabled
    }

    fun serialized(settings: ModifierSettings, dark: Boolean): String = when (this) {
        HeadsUp -> if (dark) settings.headsUpGlassDarkParameters else settings.headsUpGlassParameters
        ShadeCards -> settings.shadeCardGlassParameters
    }

    fun values(settings: ModifierSettings, dark: Boolean): FloatArray =
        HeadsUpGlassParameters.parseSerializedOrDefault(serialized(settings, dark), defaults(dark))

    fun setEnabled(settings: ModifierSettings, enabled: Boolean): ModifierSettings = when (this) {
        HeadsUp -> settings.copy(headsUpGlassParametersEnabled = enabled)
        ShadeCards -> settings.copy(shadeCardGlassParametersEnabled = enabled)
    }

    fun setParameters(settings: ModifierSettings, dark: Boolean, values: FloatArray): ModifierSettings {
        val serialized = HeadsUpGlassParameters.serialize(values)
        return when (this) {
            HeadsUp -> if (dark) settings.copy(headsUpGlassDarkParameters = serialized)
                else settings.copy(headsUpGlassParameters = serialized)
            ShadeCards -> settings.copy(shadeCardGlassParameters = serialized)
        }
    }

    fun moduleDefault(settings: ModifierSettings): ModifierSettings = when (this) {
        HeadsUp -> ModifierSettingsPresets.headsUpGlassModuleDefault(settings)
        ShadeCards -> setEnabled(setParameters(settings, false, defaults(false)), true)
    }

    fun systemDefault(settings: ModifierSettings): ModifierSettings = when (this) {
        HeadsUp -> ModifierSettingsPresets.headsUpGlassSystemDefault(settings)
        ShadeCards -> setEnabled(settings, false)
    }

    fun importPreset(settings: ModifierSettings, preset: HeadsUpGlassPresetJson.Preset): ModifierSettings {
        val configured = if (this == HeadsUp) setParameters(setParameters(settings, false, preset.regular), true, preset.dark)
            else setParameters(settings, false, preset.regular)
        val next = setEnabled(configured, true)
        // Shared blur belongs to its own page. Legacy presets still parse, but their blur
        // fields must not change other surfaces while importing a material recipe.
        return next
    }
}
