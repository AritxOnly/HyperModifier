package com.aritxonly.myhypermodifier

import android.content.Context

internal object MediaLayoutPresets {
    const val SYSTEM = "system"
    const val COMPACT = "compact"
    const val STANDARD = "standard"
    const val CUSTOM = "custom"
    val all = listOf(SYSTEM, COMPACT, STANDARD, CUSTOM)

    fun selected(settings: ModifierSettings): String =
        settings.mediaLayoutPreset.takeIf { it in all }
            ?: when {
                settings.customMediaConstraintSetEnabled || settings.customMediaIslandConstraintSetEnabled -> CUSTOM
                settings.mediaEnabled || settings.islandEnabled -> STANDARD
                else -> SYSTEM
            }

    fun label(preset: String): String = when (preset) {
        SYSTEM -> "系统默认"
        COMPACT -> "紧凑"
        STANDARD -> "标准"
        else -> "自定义"
    }

    fun height(settings: ModifierSettings, preset: String): Float = when (preset) {
        SYSTEM -> settings.systemMediaHeight
        COMPACT -> settings.compactMediaHeight
        STANDARD -> settings.standardMediaHeight
        else -> settings.customMediaHeight
    }

    fun withHeight(settings: ModifierSettings, preset: String, height: Float): ModifierSettings =
        when (preset) {
            SYSTEM -> settings.copy(systemMediaHeight = height)
            COMPACT -> settings.copy(compactMediaHeight = height)
            STANDARD -> settings.copy(standardMediaHeight = height)
            else -> settings.copy(customMediaHeight = height)
        }

    fun xml(settings: ModifierSettings, preset: String, island: Boolean): String = when (preset) {
        SYSTEM -> if (island) settings.systemMediaIslandXml else settings.systemMediaXml
        COMPACT -> if (island) settings.compactMediaIslandXml else settings.compactMediaXml
        STANDARD -> if (island) settings.standardMediaIslandXml else settings.standardMediaXml
        else -> if (island) settings.customMediaIslandConstraintSetXml else settings.customMediaConstraintSetXml
    }

    fun withXml(settings: ModifierSettings, preset: String, island: Boolean, xml: String): ModifierSettings =
        when (preset) {
            SYSTEM -> if (island) settings.copy(systemMediaIslandXml = xml)
                else settings.copy(systemMediaXml = xml)
            COMPACT -> if (island) settings.copy(compactMediaIslandXml = xml)
                else settings.copy(compactMediaXml = xml)
            STANDARD -> if (island) settings.copy(standardMediaIslandXml = xml)
                else settings.copy(standardMediaXml = xml)
            else -> if (island) settings.copy(customMediaIslandConstraintSetXml = xml)
                else settings.copy(customMediaConstraintSetXml = xml)
        }

    fun compactSource(context: Context): String =
        context.assets.open("media_compact.xml").bufferedReader().use { it.readText() }

    fun select(settings: ModifierSettings, preset: String, context: Context): ModifierSettings {
        require(preset in all)
        var result = settings.copy(
            mediaLayoutPreset = preset,
            mediaEnabled = preset != SYSTEM,
            islandEnabled = preset != SYSTEM,
            customMediaConstraintSetEnabled = preset == CUSTOM,
            customMediaIslandConstraintSetEnabled = preset == CUSTOM,
        )
        if (preset == COMPACT && (result.compactMediaXml.isBlank()
                || result.compactMediaIslandXml.isBlank())) {
            val source = compactSource(context)
            result = result.copy(
                compactMediaXml = result.compactMediaXml.ifBlank { source },
                compactMediaIslandXml = result.compactMediaIslandXml.ifBlank {
                    source.replace("@id/media_bg\"", "@id/media_bg_view\"")
                },
            )
        }
        return result
    }
}
