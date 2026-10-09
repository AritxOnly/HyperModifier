package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class PhoneImageSettingsTest {
    @Test fun xiaomi17FixedCalibrationOverridesTemporaryValuesAndSurvivesMalformedLegacySettings() {
        val expected = floatArrayOf(0f, 22f, 1.4f)
        val old = PhoneImageSettings.withTransform(ModifierSettings(), "xiaomi-17", -2f, 3f, .8f)
        assertArrayEquals(expected, PhoneImageSettings.transform(old, "xiaomi-17"), 0f)
        assertArrayEquals(expected, PhoneImageProfile.read("invalid", "xiaomi-17"), 0f)
        val autoKey = PhoneImageSettings.profileKey("auto", "xiaomi-17")
        assertArrayEquals(expected, PhoneImageSettings.transform(ModifierSettings(), autoKey), 0f)
    }
    @Test fun otherCalibrationsAndCustomImageFitRemainIndependent() {
        assertArrayEquals(floatArrayOf(0f, 15f, .95f), PhoneImageSettings.transform(ModifierSettings(), "xiaomi-17-pro"), 0f)
        val custom = PhoneImageSettings.withTransform(ModifierSettings(), "custom", 0f, 0f, 1f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), PhoneImageSettings.transform(custom, "custom"), 0f)
        assertEquals("custom", PhoneImageSettings.profileKey("custom", "xiaomi-17"))
    }
    @Test fun dynamicPresetSelectionAndUnknownHardwareKeepTheirFallback() {
        assertEquals("new-model", PhoneImageSettings.profileKey("new-model", null))
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), PhoneImageSettings.transform(ModifierSettings(), "auto"), 0f)
        assertEquals(SettingsDestination.PhoneImage, SettingsDestination.fromKey("phone-image"))
    }
}
