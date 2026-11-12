package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class QQFoldImmersionTest {
    @Test fun independentOptInDefaultsOffAndSurvivesBackupWithoutFloatingNavigation() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        assertFalse(ModifierSettings().qqFoldNavigationImmersionEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().qqFoldNavigationImmersionEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().qqFoldNavigationImmersionEnabled)
        val chosen = ModifierSettings(qqFoldNavigationImmersionEnabled = true,
            qqFoldSplitRatioEnabled = false, qqFloatingNavigationEnabled = false)
        ModifierSettingsStore.save(context, chosen)
        assertTrue(ModifierSettingsStore.load(context).qqFoldNavigationImmersionEnabled)
        assertFalse(ModifierSettingsStore.load(context).qqFoldSplitRatioEnabled)
        assertFalse(ModifierSettingsStore.load(context).qqFloatingNavigationEnabled)
        assertTrue(ModifierSettingsStore.toBundle(chosen).getBoolean("qq_fold_navigation_immersion_enabled"))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).qqFoldNavigationImmersionEnabled)
    }

    private fun source(vararg entries: Pair<String, Int>) = lazy { arrayListOf(*entries) }.apply { value }
    private fun adapter(source: Lazy<ArrayList<Pair<String, Int>>>) =
        QQImmersionSceneOverride(source, Pair::class.java)

    @Test fun missingCommonSceneUsesNativeSplitCompatibleModeAndRestoresExactList() {
        val layer = "layer" to 2
        val source = source(layer)
        val original = source.value
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        assertEquals(listOf(layer, "common" to 2), source.value)
        assertFalse(treatment.enable()) // No repeated writes/recreation.
        assertTrue(treatment.restore())
        assertSame(original, source.value)
        assertEquals(listOf(layer), source.value)
        assertFalse(treatment.restore())
    }

    @Test fun restoresOriginalSceneByIdentityAndPreservesOtherScenes() {
        val original = "common" to 1
        val source = source("layer" to 2, original, "disabled" to 0)
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        assertEquals("common" to 2, source.value[1])
        assertTrue(treatment.restore())
        assertSame(original, source.value[1])
        assertEquals("disabled" to 0, source.value[2])
    }

    @Test fun cloudOffAndAlreadyNativeScenesAreNeverOverridden() {
        for (mode in listOf(0, 2)) {
            val original = "common" to mode
            val source = source(original)
            val treatment = adapter(source)
            assertFalse(treatment.enable())
            assertSame(original, source.value.single())
            assertFalse(treatment.restore())
        }
    }

    @Test fun disablingDoesNotUndoAnotherOwnersReplacement() {
        val source = source("common" to 1)
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        val other = "common" to 0
        source.value[0] = other
        assertFalse(treatment.restore())
        assertSame(other, source.value[0])
    }

    @Test fun publishingAndRestoringNeverInvalidatesNativeReadersExistingIterator() {
        val layer = "layer" to 2
        val source = source(layer)
        val before = source.value.iterator()
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        assertEquals(listOf(layer), before.asSequence().toList())
        val during = source.value.iterator()
        assertTrue(treatment.restore())
        assertEquals(listOf(layer, "common" to 2), during.asSequence().toList())
        assertEquals(listOf(layer), source.value)
    }

    @Test fun restoringCommonScenePreservesOtherOwnersChangesToOtherScenes() {
        val source = source("layer" to 2)
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        source.value[0] = "layer" to 0
        assertTrue(treatment.restore())
        assertEquals(listOf("layer" to 0), source.value)
    }

    @Test fun restoringDoesNotReplaceAnotherOwnersPublishedSnapshot() {
        val source = source("common" to 1)
        val treatment = adapter(source)
        assertTrue(treatment.enable())
        val other = arrayListOf("common" to 0)
        source.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(source, other)
        assertFalse(treatment.restore())
        assertSame(other, source.value)
    }
}
