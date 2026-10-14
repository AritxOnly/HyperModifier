package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStatusBarSettingsTest {
    @Test fun freshInstallAndSystemPresetKeepAutomaticColor() {
        assertEquals(DesktopStatusBarPolicy.SYSTEM, ModifierSettings().desktopStatusBarIconMode)
        assertEquals(DesktopStatusBarPolicy.SYSTEM, ModifierSettingsPresets.systemDefault().desktopStatusBarIconMode)
    }
}
