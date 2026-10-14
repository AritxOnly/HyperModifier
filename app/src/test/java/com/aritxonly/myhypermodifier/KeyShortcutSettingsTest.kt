package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyShortcutSettingsTest {
    @Test fun freshInstallAndSystemRestorePreserveBothNativeShortcuts() {
        for (settings in listOf(ModifierSettings(), ModifierSettingsPresets.systemDefault())) {
            assertEquals(KeyShortcutActions.STOCK, settings.volumeUpPowerAction)
            assertEquals(KeyShortcutActions.STOCK, settings.doublePowerAction)
        }
    }
}
