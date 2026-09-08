package com.aritxonly.myhypermodifier

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.compositeOver
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.scheme.DynamicScheme
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The same stable blue Miuix preset family used by Deadliner; no system/dynamic wallpaper colors. */
object MhmPresetColors {
    private val lightSeed = Color(0xFF3382FF)
    private val darkSeed = Color(0xFF277AF7)

    fun material(dark: Boolean): ColorScheme {
        val seed = if (dark) darkSeed else lightSeed
        val preset = dynamicColorScheme(
            primary = seed,
            isDark = dark,
            isAmoled = false,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
            platform = DynamicScheme.Platform.PHONE,
        ).copy(primary = seed, onPrimary = Color.White)
        // This is Deadliner's MiuixHyperOs background strategy: preset accent, HyperOS neutrals.
        val miuixDefaults = if (dark) darkColorScheme() else lightColorScheme()
        val background = if (dark) Color(0xFF000000) else Color(0xFFF3F3F3)
        val surfaceVariant = if (dark) Color(0xFF333333) else Color(0xFFECECEC)
        val onSurface = if (dark) Color(0xFFF2F2F2) else Color(0xFF191919)
        val onSurfaceVariant = if (dark) Color(0xFFA6A6A6) else Color(0xFF6F6F6F)
        return preset.copy(
            background = background,
            onBackground = onSurface,
            surface = background,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = background,
            surfaceContainerLow = background,
            surfaceContainer = miuixDefaults.surfaceContainer,
            surfaceContainerHigh = miuixDefaults.surfaceContainerHigh,
            surfaceContainerHighest = miuixDefaults.surfaceContainerHighest,
            surfaceBright = miuixDefaults.surfaceVariant,
            surfaceDim = miuixDefaults.surfaceContainerHigh,
            outline = miuixDefaults.outline,
            outlineVariant = miuixDefaults.dividerLine,
            scrim = miuixDefaults.windowDimming,
        )
    }

    fun miuix(material: ColorScheme, dark: Boolean): Colors {
        val defaults = if (dark) darkColorScheme() else lightColorScheme()
        val sliderBackground = material.outlineVariant.copy(alpha = if (dark) 0.48f else 0.28f)
            .compositeOver(material.surfaceContainerHigh)
        return if (dark) {
            darkColorScheme(
                primary = material.primary,
                onPrimary = material.onPrimary,
                primaryVariant = material.inversePrimary,
                onPrimaryVariant = material.onPrimaryContainer,
                error = material.error,
                onError = material.onError,
                errorContainer = material.errorContainer,
                onErrorContainer = material.onErrorContainer,
                disabledPrimary = material.primary.copy(alpha = 0.45f),
                disabledOnPrimary = material.onPrimary.copy(alpha = 0.6f),
                disabledPrimaryButton = material.primary.copy(alpha = 0.45f),
                disabledOnPrimaryButton = material.onPrimary.copy(alpha = 0.6f),
                disabledPrimarySlider = material.primary.copy(alpha = 0.55f),
                primaryContainer = material.primaryContainer,
                onPrimaryContainer = material.onPrimaryContainer,
                secondary = defaults.secondary,
                onSecondary = defaults.onSecondary,
                secondaryVariant = defaults.secondaryVariant,
                onSecondaryVariant = defaults.onSecondaryVariant,
                disabledSecondary = defaults.disabledSecondary,
                disabledOnSecondary = defaults.disabledOnSecondary,
                disabledSecondaryVariant = defaults.disabledSecondaryVariant,
                disabledOnSecondaryVariant = defaults.disabledOnSecondaryVariant,
                secondaryContainer = material.secondaryContainer,
                onSecondaryContainer = material.onSecondaryContainer,
                secondaryContainerVariant = material.surfaceContainerHigh,
                onSecondaryContainerVariant = material.onSurfaceVariant,
                tertiaryContainer = material.tertiaryContainer,
                onTertiaryContainer = material.onTertiaryContainer,
                tertiaryContainerVariant = material.surfaceContainerHighest,
                background = material.background,
                onBackground = material.onBackground,
                onBackgroundVariant = material.onSurfaceVariant,
                surface = material.surface,
                onSurface = material.onSurface,
                surfaceVariant = material.surfaceVariant,
                onSurfaceSecondary = material.onSurface.copy(alpha = 0.8f),
                onSurfaceVariantSummary = material.onSurfaceVariant,
                onSurfaceVariantActions = material.onSurfaceVariant,
                disabledOnSurface = material.onSurfaceVariant.copy(alpha = 0.5f),
                surfaceContainer = material.surfaceContainer,
                onSurfaceContainer = material.onSurface,
                onSurfaceContainerVariant = material.onSurfaceVariant,
                surfaceContainerHigh = material.surfaceContainerHigh,
                onSurfaceContainerHigh = material.onSurface,
                surfaceContainerHighest = material.surfaceContainerHighest,
                onSurfaceContainerHighest = material.onSurface,
                outline = material.outline,
                dividerLine = material.outlineVariant,
                windowDimming = Color.Black.copy(alpha = if (dark) 0.44f else 0.28f),
                sliderKeyPoint = material.outlineVariant.copy(alpha = 0.35f),
                sliderKeyPointForeground = material.primary,
                sliderBackground = sliderBackground,
            )
        } else {
            lightColorScheme(
                primary = material.primary,
                onPrimary = material.onPrimary,
                primaryVariant = material.inversePrimary,
                onPrimaryVariant = material.onPrimaryContainer,
                error = material.error,
                onError = material.onError,
                errorContainer = material.errorContainer,
                onErrorContainer = material.onErrorContainer,
                disabledPrimary = material.primary.copy(alpha = 0.45f),
                disabledOnPrimary = material.onPrimary.copy(alpha = 0.6f),
                disabledPrimaryButton = material.primary.copy(alpha = 0.45f),
                disabledOnPrimaryButton = material.onPrimary.copy(alpha = 0.6f),
                disabledPrimarySlider = material.primary.copy(alpha = 0.55f),
                primaryContainer = material.primaryContainer,
                onPrimaryContainer = material.onPrimaryContainer,
                secondary = defaults.secondary,
                onSecondary = defaults.onSecondary,
                secondaryVariant = defaults.secondaryVariant,
                onSecondaryVariant = defaults.onSecondaryVariant,
                disabledSecondary = defaults.disabledSecondary,
                disabledOnSecondary = defaults.disabledOnSecondary,
                disabledSecondaryVariant = defaults.disabledSecondaryVariant,
                disabledOnSecondaryVariant = defaults.disabledOnSecondaryVariant,
                secondaryContainer = material.secondaryContainer,
                onSecondaryContainer = material.onSecondaryContainer,
                secondaryContainerVariant = material.surfaceContainerHigh,
                onSecondaryContainerVariant = material.onSurfaceVariant,
                tertiaryContainer = material.tertiaryContainer,
                onTertiaryContainer = material.onTertiaryContainer,
                tertiaryContainerVariant = material.surfaceContainerHighest,
                background = material.background,
                onBackground = material.onBackground,
                onBackgroundVariant = material.onSurfaceVariant,
                surface = material.surface,
                onSurface = material.onSurface,
                surfaceVariant = material.surfaceVariant,
                onSurfaceSecondary = material.onSurface.copy(alpha = 0.8f),
                onSurfaceVariantSummary = material.onSurfaceVariant,
                onSurfaceVariantActions = material.onSurfaceVariant,
                disabledOnSurface = material.onSurfaceVariant.copy(alpha = 0.5f),
                surfaceContainer = material.surfaceContainer,
                onSurfaceContainer = material.onSurface,
                onSurfaceContainerVariant = material.onSurfaceVariant,
                surfaceContainerHigh = material.surfaceContainerHigh,
                onSurfaceContainerHigh = material.onSurface,
                surfaceContainerHighest = material.surfaceContainerHighest,
                onSurfaceContainerHighest = material.onSurface,
                outline = material.outline,
                dividerLine = material.outlineVariant,
                windowDimming = Color.Black.copy(alpha = if (dark) 0.44f else 0.28f),
                sliderKeyPoint = material.outlineVariant.copy(alpha = 0.35f),
                sliderKeyPointForeground = material.primary,
                sliderBackground = sliderBackground,
            )
        }
    }
}
