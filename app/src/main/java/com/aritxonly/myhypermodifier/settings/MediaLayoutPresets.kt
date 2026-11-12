package com.aritxonly.myhypermodifier

import android.content.Context

internal object MediaLayoutPresets {
    const val SYSTEM = "system"
    const val COMPACT = "compact"
    const val LARGE_COVER = "large_cover"
    const val STANDARD = "standard"
    const val CUSTOM = "custom"
    val all = listOf(SYSTEM, COMPACT, LARGE_COVER, STANDARD, CUSTOM)

    fun selected(settings: ModifierSettings, island: Boolean = false): String =
        (if (island) settings.mediaIslandLayoutPreset.ifBlank { settings.mediaLayoutPreset }
            else settings.mediaLayoutPreset).takeIf { it in all }
            ?: when {
                (if (island) settings.customMediaIslandConstraintSetEnabled else settings.customMediaConstraintSetEnabled) -> CUSTOM
                (if (island) settings.islandEnabled else settings.mediaEnabled) -> STANDARD
                else -> SYSTEM
            }

    fun label(preset: String): String = when (preset) {
        SYSTEM -> "系统默认"
        COMPACT -> "紧凑"
        LARGE_COVER -> "大封面"
        STANDARD -> "标准"
        else -> "自定义"
    }

    fun height(settings: ModifierSettings, preset: String, island: Boolean = false): Float {
        if (island) {
            val value = when (preset) {
                SYSTEM -> settings.systemMediaIslandHeight
                COMPACT -> settings.compactMediaIslandHeight
                LARGE_COVER -> settings.largeCoverMediaIslandHeight
                STANDARD -> settings.standardMediaIslandHeight
                else -> settings.customMediaIslandHeight
            }
            if (value > 0f && value.isFinite()) return value
        }
        return when (preset) {
        SYSTEM -> settings.systemMediaHeight
        COMPACT -> settings.compactMediaHeight
        LARGE_COVER -> settings.largeCoverMediaHeight
        STANDARD -> settings.standardMediaHeight
        else -> settings.customMediaHeight
        }
    }

    fun withHeight(settings: ModifierSettings, preset: String, height: Float, island: Boolean = false): ModifierSettings {
        if (island) return when (preset) {
            SYSTEM -> settings.copy(systemMediaIslandHeight = height)
            COMPACT -> settings.copy(compactMediaIslandHeight = height)
            LARGE_COVER -> settings.copy(largeCoverMediaIslandHeight = height)
            STANDARD -> settings.copy(standardMediaIslandHeight = height)
            else -> settings.copy(customMediaIslandHeight = height)
        }
        // Freeze the other surface before editing a migrated shared height.
        val independent = withHeight(settings, preset, height(settings, preset, true), true)
        return when (preset) {
            SYSTEM -> independent.copy(systemMediaHeight = height)
            COMPACT -> independent.copy(compactMediaHeight = height)
            LARGE_COVER -> independent.copy(largeCoverMediaHeight = height)
            STANDARD -> independent.copy(standardMediaHeight = height)
            else -> independent.copy(customMediaHeight = height)
        }
    }

    fun xml(settings: ModifierSettings, preset: String, island: Boolean): String = when (preset) {
        SYSTEM -> if (island) settings.systemMediaIslandXml else settings.systemMediaXml
        COMPACT -> if (island) settings.compactMediaIslandXml else settings.compactMediaXml
        LARGE_COVER -> if (island) settings.largeCoverMediaIslandXml else settings.largeCoverMediaXml
        STANDARD -> if (island) settings.standardMediaIslandXml else settings.standardMediaXml
        else -> if (island) settings.customMediaIslandConstraintSetXml else settings.customMediaConstraintSetXml
    }

    fun withXml(settings: ModifierSettings, preset: String, island: Boolean, xml: String): ModifierSettings =
        when (preset) {
            SYSTEM -> if (island) settings.copy(systemMediaIslandXml = xml)
                else settings.copy(systemMediaXml = xml)
            COMPACT -> if (island) settings.copy(compactMediaIslandXml = xml)
                else settings.copy(compactMediaXml = xml)
            LARGE_COVER -> if (island) settings.copy(largeCoverMediaIslandXml = xml)
                else settings.copy(largeCoverMediaXml = xml)
            STANDARD -> if (island) settings.copy(standardMediaIslandXml = xml)
                else settings.copy(standardMediaXml = xml)
            else -> if (island) settings.copy(customMediaIslandConstraintSetXml = xml)
                else settings.copy(customMediaConstraintSetXml = xml)
        }

    fun bundledXml(preset: String, island: Boolean): String {
        val source = when (preset) {
            COMPACT -> MediaPresetDefaults.COMPACT_XML
            LARGE_COVER -> if (island) MediaPresetDefaults.LARGE_COVER_ISLAND_XML else MediaPresetDefaults.LARGE_COVER_XML
            else -> return ""
        }
        return if (island) MediaPresetDefaults.islandXml(source) else source
    }

    fun select(settings: ModifierSettings, preset: String, @Suppress("UNUSED_PARAMETER") context: Context, island: Boolean = false): ModifierSettings {
        require(preset in all)
        return if (island) settings.copy(
            mediaIslandLayoutPreset = preset,
            islandEnabled = preset != SYSTEM,
            customMediaIslandConstraintSetEnabled = preset == CUSTOM,
        ) else settings.copy(
            mediaIslandLayoutPreset = selected(settings, true),
            mediaLayoutPreset = preset,
            mediaEnabled = preset != SYSTEM,
            customMediaConstraintSetEnabled = preset == CUSTOM,
        )
    }
}
