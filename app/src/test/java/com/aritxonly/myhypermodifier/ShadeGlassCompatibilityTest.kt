package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class ShadeGlassCompatibilityTest {
    @Test fun compatibilityGateIsOptIn() {
        assertFalse(ModifierSettingsPresets.moduleDefault().disableShadeGlassHooks)
        assertTrue(GlassParameterScope.ShadeCards.enabled(
            ModifierSettings(shadeCardGlassParametersEnabled = true)))
    }

    @Test fun disablingGlassPreservesOtherFeaturesAndSavedParameters() {
        val configured = ModifierSettings(shadeCardGlassParametersEnabled = true,
            globalGlassBlurEnabled = true, shadeCardGlassBlurEnabled = true,
            shadeCardGlassBlurRadius = 37f, shadeCardBackgroundBlurPercent = 75f,
            headsUpGlassParametersEnabled = true, notificationsEnabled = true,
            controlCenterEnabled = true, globalBackgroundBlurPercent = 80f,
            globalBackgroundDimEnabled = true, globalBackgroundDimPercent = 30f)
        val disabled = configured.copy(disableShadeGlassHooks = true)
        assertFalse(GlassParameterScope.ShadeCards.enabled(disabled))
        assertTrue(GlassParameterScope.HeadsUp.enabled(disabled))
        // Only the compatibility flag changes; disabling/re-enabling never resets values.
        assertEquals(configured, disabled.copy(disableShadeGlassHooks = false))
        assertTrue(disabled.notificationsEnabled)
        assertTrue(disabled.controlCenterEnabled)
        assertEquals(80f, disabled.globalBackgroundBlurPercent, 0f)
        assertEquals(30f, disabled.globalBackgroundDimPercent, 0f)
    }

    @Test fun presetsAndImportCannotBypassCompatibilityGate() {
        val scope = GlassParameterScope.ShadeCards
        val disabled = ModifierSettings(disableShadeGlassHooks = true,
            globalGlassBlurEnabled = true, shadeCardGlassBlurRadius = 37f)
        val preset = requireNotNull(HeadsUpGlassPresetJson.import(
            HeadsUpGlassPresetJson.export(disabled, scope), scope))
        for (result in listOf(scope.moduleDefault(disabled), scope.systemDefault(disabled),
            scope.setEnabled(disabled, true), scope.importPreset(disabled, preset))) {
            assertTrue(result.disableShadeGlassHooks)
            assertFalse(scope.enabled(result))
            assertTrue(result.globalGlassBlurEnabled)
            assertEquals(37f, result.shadeCardGlassBlurRadius, 0f)
        }
    }
}
