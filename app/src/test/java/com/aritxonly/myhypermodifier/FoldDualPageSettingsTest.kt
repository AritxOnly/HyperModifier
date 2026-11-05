package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class FoldDualPageSettingsTest {
    @Test fun savedAndRestoredSwitchKeepsIconChoice() {
        val context = RuntimeEnvironment.getApplication()
        val selected = ModifierSettings(foldPortraitDualPageEnabled = true, foldPortraitIconPercent = 85)
        ModifierSettingsStore.save(context, selected)
        assertTrue(ModifierSettingsStore.load(context).foldPortraitDualPageEnabled)
        val backup = ModifierSettingsStore.backupValues(context)
        assertEquals(true, backup["fold_portrait_dual_page_enabled"])
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).foldPortraitDualPageEnabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        val restored = ModifierSettingsStore.load(context)
        assertTrue(restored.foldPortraitDualPageEnabled)
        assertEquals(85, restored.foldPortraitIconPercent)
        assertTrue(ModifierSettingsStore.toBundle(restored).getBoolean("fold_portrait_dual_page_enabled"))
    }

    @Test fun dualPageIsOptInAndIndependentOfIconScaling() {
        assertFalse(ModifierSettings().foldPortraitDualPageEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().foldPortraitDualPageEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().foldPortraitDualPageEnabled)
        val scaled = ModifierSettings(foldPortraitIconPercent = 80)
        assertFalse(scaled.foldPortraitDualPageEnabled)
        assertEquals(80, scaled.copy(foldPortraitDualPageEnabled = true).foldPortraitIconPercent)
        assertEquals(false, ModifierSettingsStore.backupSchema()["fold_portrait_dual_page_enabled"])
    }
}
