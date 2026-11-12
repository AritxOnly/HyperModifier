package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class SystemBarCompatibilitySettingsTest {
    @Test fun switchSurvivesRemotePayloadAndBackupRestore() {
        val context = RuntimeEnvironment.getApplication()
        val selected = ModifierSettings(systemBarCompatibilityScrimDisabled = true)
        ModifierSettingsStore.save(context, selected)
        assertTrue(ModifierSettingsStore.load(context).systemBarCompatibilityScrimDisabled)
        assertTrue(ModifierSettingsStore.toBundle(selected).getBoolean(SystemBarCompatibilityHooks.KEY))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).systemBarCompatibilityScrimDisabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).systemBarCompatibilityScrimDisabled)
    }

    @Test fun existingInstallAndDefaultPresetsKeepNativeSystemBarPolicy() {
        assertFalse(ModifierSettings().systemBarCompatibilityScrimDisabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().systemBarCompatibilityScrimDisabled)
        assertFalse(ModifierSettingsPresets.systemDefault().systemBarCompatibilityScrimDisabled)
    }
}
