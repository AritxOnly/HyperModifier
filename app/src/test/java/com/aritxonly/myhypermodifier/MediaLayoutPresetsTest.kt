package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLayoutPresetsTest {
    @Test fun newInstallationAndLegacySettingsSelectExpectedPreset() {
        assertEquals(MediaLayoutPresets.STANDARD,
            MediaLayoutPresets.selected(ModifierSettingsPresets.moduleDefault()))
        assertEquals(MediaLayoutPresets.STANDARD,
            MediaLayoutPresets.selected(ModifierSettings(mediaLayoutPreset = "", islandEnabled = true)))
        assertEquals(MediaLayoutPresets.CUSTOM,
            MediaLayoutPresets.selected(ModifierSettings(mediaLayoutPreset = "", customMediaIslandConstraintSetEnabled = true)))
    }

    @Test fun defaultHeightsAreIndependent() {
        val settings = ModifierSettings()
        assertEquals(168f, MediaLayoutPresets.height(settings, MediaLayoutPresets.SYSTEM))
        assertEquals(84f, MediaLayoutPresets.height(settings, MediaLayoutPresets.COMPACT))
        assertEquals(150f, MediaLayoutPresets.height(settings, MediaLayoutPresets.STANDARD))
        val adjusted = MediaLayoutPresets.withHeight(settings, MediaLayoutPresets.COMPACT, 102f)
        assertEquals(102f, MediaLayoutPresets.height(adjusted, MediaLayoutPresets.COMPACT))
        assertEquals(150f, MediaLayoutPresets.height(adjusted, MediaLayoutPresets.STANDARD))
    }

    @Test fun xmlEditsStayWithinTheirPresetAndSurface() {
        val settings = ModifierSettings()
        val edited = MediaLayoutPresets.withXml(settings, MediaLayoutPresets.COMPACT,
            island = true, xml = "<island />")
        assertEquals("<island />", MediaLayoutPresets.xml(edited, MediaLayoutPresets.COMPACT, true))
        assertEquals("", MediaLayoutPresets.xml(edited, MediaLayoutPresets.COMPACT, false))
        assertEquals("", MediaLayoutPresets.xml(edited, MediaLayoutPresets.STANDARD, true))
    }
}
