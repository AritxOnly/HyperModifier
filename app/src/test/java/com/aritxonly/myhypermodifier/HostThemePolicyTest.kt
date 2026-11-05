package com.aritxonly.myhypermodifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostThemePolicyTest {
    @Test
    fun selectedLightTextWinsOverSystemConfiguration() {
        assertTrue(
            HostThemePolicy.resolve(
                colors = listOf(0xFFFFFFFF.toInt()),
                lightSystemBars = true,
                lightTheme = true,
                configurationDark = false,
            ),
        )
    }

    @Test
    fun selectedDarkTextIdentifiesALightHostTheme() {
        assertFalse(
            HostThemePolicy.resolve(
                colors = listOf(0xFF111111.toInt()),
                lightSystemBars = false,
                lightTheme = false,
                configurationDark = true,
            ),
        )
    }

    @Test
    fun translucentAndMidToneColorsFallBackToWindowEvidence() {
        assertNull(HostThemePolicy.colorDark(0x20FFFFFF))
        assertNull(HostThemePolicy.colorDark(0xFF808080u.toInt()))
        assertTrue(
            HostThemePolicy.resolve(
                colors = listOf(0xFF808080u.toInt()),
                lightSystemBars = false,
                lightTheme = true,
                configurationDark = false,
            ),
        )
    }

    @Test
    fun configurationIsUsedOnlyWhenNoHostEvidenceExists() {
        assertTrue(
            HostThemePolicy.resolve(
                colors = emptyList(),
                lightSystemBars = null,
                lightTheme = null,
                configurationDark = true,
            ),
        )
    }

    @Test
    fun hostWindowEvidenceOverridesPaletteColorsWhenRequested() {
        assertTrue(
            HostThemePolicy.resolve(
                colors = listOf(0xFF111111.toInt()),
                lightSystemBars = false,
                lightTheme = true,
                configurationDark = true,
                preferHostWindow = true,
            ),
        )
        assertFalse(
            HostThemePolicy.resolve(
                colors = listOf(0xFFFFFFFF.toInt()),
                lightSystemBars = true,
                lightTheme = true,
                configurationDark = true,
                preferHostWindow = true,
            ),
        )
    }

    @Test
    fun fixedLightThemeDoesNotOverrideDarkConfigurationAndSystemBars() {
        assertTrue(
            HostThemePolicy.resolve(
                colors = listOf(0xFF111111.toInt()),
                lightSystemBars = false,
                lightTheme = true,
                configurationDark = true,
                preferHostWindow = true,
            ),
        )
    }

    @Test
    fun darkSystemConfigurationDoesNotOverrideAnExplicitLightHostWindow() {
        assertFalse(
            HostThemePolicy.resolve(
                colors = emptyList(),
                lightSystemBars = true,
                lightTheme = true,
                configurationDark = true,
                preferHostWindow = true,
            ),
        )
    }

    @Test
    fun opaqueBackgroundVotesOppositeToForegroundColors() {
        assertEquals(true, HostThemePolicy.backgroundColorDark(0xFF111111.toInt()))
        assertEquals(false, HostThemePolicy.backgroundColorDark(0xFFFFFFFF.toInt()))
        assertNull(HostThemePolicy.backgroundColorDark(0x20FFFFFF))
    }

    @Test
    fun opaqueNativeBackgroundWinsForCainiao() {
        assertTrue(
            HostThemePolicy.resolve(
                colors = listOf(0xFF111111.toInt()),
                lightSystemBars = true,
                lightTheme = true,
                configurationDark = false,
                nativeBackgroundDark = true,
                preferNativeBackground = true,
            ),
        )
        assertFalse(
            HostThemePolicy.resolve(
                colors = listOf(0xFFFFFFFF.toInt()),
                lightSystemBars = false,
                lightTheme = false,
                configurationDark = true,
                nativeBackgroundDark = false,
                preferNativeBackground = true,
            ),
        )
    }
}
