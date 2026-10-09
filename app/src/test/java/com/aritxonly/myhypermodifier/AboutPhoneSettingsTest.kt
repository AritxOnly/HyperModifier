package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class AboutPhoneSettingsTest {
    @Test fun moduleDefaultsEnableCardsAndSystemDefaultsKeepTheStockPage() {
        for (settings in listOf(ModifierSettings(), ModifierSettingsPresets.moduleDefault())) {
            assertTrue(settings.aboutPhoneCardsEnabled)
        }
        assertFalse(ModifierSettingsPresets.systemDefault().aboutPhoneCardsEnabled)
    }

    @Test fun homepageEntriesAreOptIn() {
        val defaults = ModifierSettings()
        assertFalse(defaults.settingsHomeEntryEnabled)
        assertFalse(defaults.settingsModulesEntryEnabled)
        assertEquals("bottom", defaults.settingsHomeEntryPosition)
        val hubOnly = defaults.copy(settingsHomeEntryEnabled = false, settingsModulesEntryEnabled = true)
        assertFalse(hubOnly.settingsHomeEntryEnabled)
        assertTrue(hubOnly.settingsModulesEntryEnabled)
    }

    @Test fun layoutCanBeEnabledIndependently() {
        val layout = ModifierSettings().copy(aboutPhoneCardsEnabled = true)
        assertTrue(layout.aboutPhoneCardsEnabled)
        assertTrue(ModuleScopePackage.PACKAGES.contains("com.android.settings"))
    }
    @Test fun builtinEntryPositionsCanBeChangedIndependently() {
        val initial = ModifierSettings(settingsHomeEntryPosition = "top")
        assertEquals("device", initial.settingsManagerEntryPosition)
        val separated = initial.copy(settingsManagerEntryPosition = "bottom")
        assertEquals("top", separated.settingsHomeEntryPosition)
        assertEquals("bottom", separated.settingsManagerEntryPosition)
        val changedModule = separated.copy(settingsHomeEntryPosition = "device")
        assertEquals("device", changedModule.settingsHomeEntryPosition)
        assertEquals("bottom", changedModule.settingsManagerEntryPosition)
    }

}
