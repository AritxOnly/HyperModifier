package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class GestureHandlePresetsTest {
    @Test fun pagePresetsClearOverridesAndPreserveOtherSettings() {
        val custom = ModifierSettings(
            notificationsEnabled = true,
            gestureHandleAppModes = mapOf("app" to "show"),
        )
        for (preset in listOf(GestureHandlePresets.MODULE, GestureHandlePresets.IMMERSIVE,
            GestureHandlePresets.HIDE, GestureHandlePresets.SHOW)) {
            val selected = ModifierSettingsPresets.gestureHandlePreset(custom, preset)
            assertEquals(preset, selected.gestureHandlePreset)
            val module = preset == GestureHandlePresets.MODULE
            assertTrue(selected.gestureHandleAppModes.isEmpty())
            assertEquals(module, selected.gestureHandleTouchReveal)
            assertEquals(module, selected.gestureHandleSwipeMotion)
            assertEquals(GestureHandleTouchArea.DEFAULT_DP, selected.gestureHandleTouchAreaDp, 0f)
            assertEquals(preset, ModifierSettingsPresets.selectedGestureHandlePreset(selected))
            assertTrue(selected.notificationsEnabled)
        }
    }

    @Test fun editsMakePresetCustomUntilItsContentsMatchAgain() {
        val module = ModifierSettingsPresets.gestureHandlePreset(ModifierSettings(), GestureHandlePresets.MODULE)
        assertEquals(19, GestureHandlePresets.MODULE_APPS.size)
        val exported = requireNotNull(GestureHandlePresetJson.parse(GestureHandlePresetJson.export(module)))
        assertEquals(GestureHandlePresets.MODULE_APPS, exported.apps)
        assertEquals(GestureHandlePresets.MODULE,
            ModifierSettingsPresets.selectedGestureHandlePreset(exported.applyTo(module)))
        assertNull(ModifierSettingsPresets.selectedGestureHandlePreset(
            module.copy(gestureHandleAppModes = module.gestureHandleAppModes + ("extra.app" to "show"))))
        assertNull(ModifierSettingsPresets.selectedGestureHandlePreset(
            module.copy(gestureHandleSwipeMotion = false)))
        assertNull(ModifierSettingsPresets.selectedGestureHandlePreset(
            module.copy(gestureHandleTouchAreaDp = 24f)))
        assertEquals(GestureHandlePresets.MODULE,
            ModifierSettingsPresets.selectedGestureHandlePreset(module))
    }

    @Test fun globalDefaultsUseMatchingGestureHandlePreset() {
        assertEquals(GestureHandlePresets.STOCK, ModifierSettingsPresets.moduleDefault().gestureHandlePreset)
        assertTrue(ModifierSettingsPresets.moduleDefault().gestureHandleAppModes.isEmpty())
        assertFalse(ModifierSettingsPresets.moduleDefault().gestureHandleTouchReveal)
        assertFalse(ModifierSettingsPresets.moduleDefault().gestureHandleSwipeMotion)
        assertEquals(GestureHandlePresets.STOCK, ModifierSettingsPresets.systemDefault().gestureHandlePreset)
        assertTrue(ModifierSettingsPresets.systemDefault().gestureHandleAppModes.isEmpty())
        assertFalse(ModifierSettingsPresets.systemDefault().gestureHandleTouchReveal)
        assertFalse(ModifierSettingsPresets.systemDefault().gestureHandleSwipeMotion)
        assertEquals(GestureHandleTouchArea.DEFAULT_DP,
            ModifierSettingsPresets.systemDefault().gestureHandleTouchAreaDp, 0f)
        assertEquals(GestureHandlePresets.STOCK,
            ModifierSettingsPresets.selectedGestureHandlePreset(ModifierSettingsPresets.systemDefault()))
        assertNull(ModifierSettingsPresets.selectedGestureHandlePreset(
            ModifierSettingsPresets.systemDefault().copy(gestureHandleTouchReveal = true)))
    }

    @Test fun storedPresetSurvivesReloadAndMigratesLegacyChoices() {
        listOf(GestureHandlePresets.MODULE, GestureHandlePresets.IMMERSIVE,
            GestureHandlePresets.HIDE, GestureHandlePresets.SHOW).forEach { preset ->
            assertEquals(preset, GestureHandlePresets.fromStored(preset, preset != GestureHandlePresets.MODULE))
        }
        assertEquals(GestureHandlePresets.MODULE, GestureHandlePresets.fromStored(null, true))
        assertEquals(GestureHandlePresets.SHOW, GestureHandlePresets.fromStored(null, false))
        assertEquals(GestureHandlePresets.SHOW, GestureHandlePresets.fromStored("system", true))
        assertEquals(GestureHandlePresets.STOCK, GestureHandlePresets.fromStored("stock", true))
    }
}
