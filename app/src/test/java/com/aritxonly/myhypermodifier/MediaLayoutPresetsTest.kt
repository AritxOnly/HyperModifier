package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLayoutPresetsTest {
    @Test fun newInstallationAndLegacySettingsSelectExpectedPreset() {
        assertEquals(MediaLayoutPresets.STANDARD,
            MediaLayoutPresets.selected(ModifierSettingsPresets.moduleDefault()))
        assertEquals(MediaLayoutPresets.STANDARD,
            MediaLayoutPresets.selected(ModifierSettings(mediaLayoutPreset = "", mediaEnabled = true)))
        assertEquals(MediaLayoutPresets.CUSTOM,
            MediaLayoutPresets.selected(ModifierSettings(mediaLayoutPreset = "", customMediaConstraintSetEnabled = true)))
    }

    @Test fun defaultHeightsAreIndependent() {
        val settings = ModifierSettings()
        assertEquals(168f, MediaLayoutPresets.height(settings, MediaLayoutPresets.SYSTEM))
        assertEquals(80f, MediaLayoutPresets.height(settings, MediaLayoutPresets.COMPACT))
        assertEquals(132f, MediaLayoutPresets.height(settings, MediaLayoutPresets.LARGE_COVER))
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
    @Test fun customizedCompactXmlAndHeightSurviveMigration() {
        assertEquals(80f, MediaPresetDefaults.migrateCompactHeight(84f, "", ""))
        assertEquals(96f, MediaPresetDefaults.migrateCompactHeight(96f, "", ""))
        assertEquals(84f, MediaPresetDefaults.migrateCompactHeight(84f, "<custom />", ""))
        assertEquals("<custom />", MediaPresetDefaults.migrateCompactXml("<custom />"))
        val settings = MediaLayoutPresets.withXml(ModifierSettings(), MediaLayoutPresets.LARGE_COVER, true, "<large />")
        assertEquals("<large />", MediaLayoutPresets.xml(settings, MediaLayoutPresets.LARGE_COVER, true))
        assertEquals("", MediaLayoutPresets.xml(settings, MediaLayoutPresets.COMPACT, true))
    }

}
