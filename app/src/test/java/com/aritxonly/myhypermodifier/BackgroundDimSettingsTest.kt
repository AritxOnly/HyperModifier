package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class BackgroundDimSettingsTest {
    @Test fun dimCustomizationIsOptInAndIndependentOfBlur() {
        val defaults = ModifierSettings()
        assertFalse(defaults.globalBackgroundDimEnabled)
        assertEquals(20f, defaults.globalBackgroundDimPercent, 0f)
        val custom = defaults.copy(globalBackgroundDimEnabled = true, globalBackgroundDimPercent = 40f)
        assertEquals(defaults.globalBackgroundBlurPercent, custom.globalBackgroundBlurPercent, 0f)
        assertTrue(custom.controlCenterFollowMiLinkBackgroundMaterial)
    }

    @Test fun systemDefaultRestoresNativeMixing() {
        assertFalse(ModifierSettingsPresets.systemDefault().globalBackgroundDimEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().globalBackgroundDimEnabled)
    }
}
