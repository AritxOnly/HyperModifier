package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class CoverAdaptationSettingsTest {
    @Test fun optInSurvivesRemoteSettingsAndBackupButDefaultsStayOff() {
        val context = RuntimeEnvironment.getApplication()
        val chosen = ModifierSettings(coverAdaptationEnabled = true)
        ModifierSettingsStore.save(context, chosen)
        assertTrue(ModifierSettingsStore.load(context).coverAdaptationEnabled)
        assertTrue(ModifierSettingsStore.toBundle(chosen).getBoolean(CoverAdaptationContract.KEY))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).coverAdaptationEnabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).coverAdaptationEnabled)
        assertFalse(ModifierSettings().coverAdaptationEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().coverAdaptationEnabled)
    }
}
