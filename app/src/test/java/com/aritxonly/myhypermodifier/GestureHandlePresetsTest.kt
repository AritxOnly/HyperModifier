package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class GestureHandlePresetsTest {
    @Test fun pagePresetsClearOverridesAndPreserveOtherSettings() {
        val custom = ModifierSettings(
            notificationsEnabled = true,
            gestureHandleAppModes = mapOf("app" to "show"),
        )
        val system = ModifierSettingsPresets.gestureHandleSystemDefault(custom)
        assertFalse(system.gestureHandleModulePreset)
        assertTrue(system.gestureHandleAppModes.isEmpty())
        assertTrue(system.notificationsEnabled)
        val module = ModifierSettingsPresets.gestureHandleModuleDefault(system)
        assertTrue(module.gestureHandleModulePreset)
        assertTrue(module.gestureHandleAppModes.isEmpty())
        assertTrue(module.notificationsEnabled)
    }

    @Test fun globalDefaultsUseMatchingGestureHandlePreset() {
        assertTrue(ModifierSettingsPresets.moduleDefault().gestureHandleModulePreset)
        assertFalse(ModifierSettingsPresets.systemDefault().gestureHandleModulePreset)
        assertTrue(ModifierSettingsPresets.systemDefault().gestureHandleAppModes.isEmpty())
    }
}
