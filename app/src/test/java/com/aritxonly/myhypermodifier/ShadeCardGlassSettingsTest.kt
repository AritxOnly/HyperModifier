package com.aritxonly.myhypermodifier

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShadeCardGlassSettingsTest {
    private val scope = GlassParameterScope.ShadeCards

    @Test fun disabledByDefaultAndPresetsDoNotChangeOtherSurfaces() {
        val initial = ModifierSettings(headsUpGlassParametersEnabled = true, globalBackgroundDimEnabled = true)
        assertFalse(scope.enabled(initial))
        val custom = scope.moduleDefault(initial)
        assertTrue(custom.shadeCardGlassParametersEnabled)
        assertEquals(initial.headsUpGlassParameters, custom.headsUpGlassParameters)
        assertTrue(custom.headsUpGlassParametersEnabled)
        assertTrue(custom.globalBackgroundDimEnabled)
        assertEquals(custom.shadeCardGlassParameters, scope.systemDefault(custom).shadeCardGlassParameters)
        assertFalse(scope.systemDefault(custom).shadeCardGlassParametersEnabled)
    }

    @Test fun cardValuesAreCommonButHeadsUpModesStayIndependent() {
        val initial = ModifierSettings()
        val values = scope.values(initial, false).apply { this[6] = .4f }
        val edited = scope.setParameters(initial, false, values)
        assertEquals(.4f, scope.values(edited, false)[6], 0f)
        assertEquals(.4f, scope.values(edited, true)[6], 0f)
        assertEquals(initial.headsUpGlassParameters, edited.headsUpGlassParameters)
        val headsUp = GlassParameterScope.HeadsUp.setParameters(initial, true, values)
        assertEquals(initial.headsUpGlassParameters, headsUp.headsUpGlassParameters)
        assertNotEquals(initial.headsUpGlassDarkParameters, headsUp.headsUpGlassDarkParameters)
    }

    @Test fun jsonRoundTripsAndNeverAcceptsHeadsUpPresetsInCardEditor() {
        val initial = scope.moduleDefault(ModifierSettings()).copy(
            shadeCardBackgroundBlurPercent = 70f, shadeCardGlassBlurEnabled = true, shadeCardGlassBlurRadius = 14f,
        )
        val serialized = HeadsUpGlassPresetJson.export(initial, scope)
        val preset = requireNotNull(HeadsUpGlassPresetJson.import(serialized, scope))
        assertArrayEquals(scope.values(initial, false), preset.regular, 0f)
        assertNull(HeadsUpGlassPresetJson.import(serialized))
        assertNull(HeadsUpGlassPresetJson.import(HeadsUpGlassPresetJson.export(initial), scope))
        val edited = scope.importPreset(initial.copy(headsUpBackgroundBlurRadiusEnabled = true), preset)
        assertTrue(edited.headsUpBackgroundBlurRadiusEnabled)
        assertEquals(70f, edited.shadeCardBackgroundBlurPercent, 0f)
        assertTrue(edited.shadeCardGlassBlurEnabled)
        assertEquals(14f, edited.shadeCardGlassBlurRadius, 0f)
        assertFalse(JSONObject(serialized).has("dark"))
    }

    @Test fun malformedAndUnsupportedPresetsAreRejected() {
        val root = JSONObject(HeadsUpGlassPresetJson.export(ModifierSettings(), scope))
        root.put("mode", "absolute")
        assertNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
        root.put("mode", "relative-to-system").put("version", 99)
        assertNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
    }

    @Test fun oldCardJsonUsesRegularAndKeepsCurrentBlurSettings() {
        val root = JSONObject(HeadsUpGlassPresetJson.export(ModifierSettings()))
        root.put("format", scope.presetFormat).put("mode", "relative-to-system")
        val preset = requireNotNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
        val settings = scope.importPreset(ModifierSettings(shadeCardBackgroundBlurPercent = 75f), preset)
        assertArrayEquals(preset.regular, scope.values(settings, true), 0f)
        assertEquals(75f, settings.shadeCardBackgroundBlurPercent, 0f)
    }

    @Test fun legacyBlurRangesAreValidatedButRecipeDefaultsKeepGlobalBlur() {
        val custom = ModifierSettings(shadeCardBackgroundBlurPercent = 70f, shadeCardGlassBlurEnabled = true)
        val baseline = scope.moduleDefault(custom)
        assertEquals(70f, baseline.shadeCardBackgroundBlurPercent, 0f)
        assertTrue(baseline.shadeCardGlassBlurEnabled)
        val root = JSONObject(HeadsUpGlassPresetJson.export(custom, scope))
        root.put("version", 2).put("backgroundBlurPercent", 70).put("glassBlurEnabled", true)
        root.put("glassBlurRadius", ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS + 1)
        assertNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
        root.put("glassBlurRadius", 20).put("backgroundBlurPercent", 201)
        assertNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
    }

    @Test fun sharedGlassRangeIsPreservedWhenImportingMaterialRecipes() {
        for (radius in listOf(0f, 40f, 100f)) {
            val settings = ModifierSettings(shadeCardGlassBlurEnabled = true, shadeCardGlassBlurRadius = radius)
            val preset = requireNotNull(HeadsUpGlassPresetJson.import(HeadsUpGlassPresetJson.export(settings, scope), scope))
            assertNull(preset.glassBlurRadius)
            assertEquals(radius, scope.importPreset(settings, preset).shadeCardGlassBlurRadius, 0f)
        }
    }

    @Test fun obsoleteHeadsUpRadiusFieldsAreNotExportedOrApplied() {
        val headsUp = GlassParameterScope.HeadsUp
        val settings = ModifierSettings(headsUpBackgroundBlurRadiusEnabled = true, headsUpBackgroundBlurRadius = 700f,
            shadeCardGlassBlurRadius = 300f)
        val json = HeadsUpGlassPresetJson.export(settings)
        assertFalse(JSONObject(json).has("glassBlurRadius"))
        assertFalse(JSONObject(json).has("glassBlurEnabled"))
        val preset = requireNotNull(HeadsUpGlassPresetJson.import(json))
        val imported = headsUp.importPreset(settings, preset)
        assertTrue(imported.headsUpBackgroundBlurRadiusEnabled)
        assertEquals(700f, imported.headsUpBackgroundBlurRadius, 0f)
        assertEquals(300f, imported.shadeCardGlassBlurRadius, 0f)
        val old = JSONObject(json).apply { remove("glassBlurRadius"); remove("glassBlurEnabled") }
        val oldPreset = requireNotNull(HeadsUpGlassPresetJson.import(old.toString()))
        assertTrue(headsUp.importPreset(settings, oldPreset).headsUpBackgroundBlurRadiusEnabled)
        assertEquals(700f, headsUp.importPreset(settings, oldPreset).headsUpBackgroundBlurRadius, 0f)
        assertFalse(headsUp.systemDefault(settings).headsUpBackgroundBlurRadiusEnabled)
        assertNull(HeadsUpGlassPresetJson.import(JSONObject(json).put("glassBlurRadius", 1001).toString()))
    }

    @Test fun presetsForBothMaterialPagesNeverChangeSharedBlur() {
        val settings = ModifierSettings(globalGlassBlurEnabled = true,
            shadeCardGlassBlurEnabled = true, shadeCardGlassBlurRadius = 500f,
            shadeCardBackgroundBlurPercent = 60f, globalBackgroundBlurPercent = 80f,
            globalBackgroundDimEnabled = true, globalBackgroundDimPercent = 35f)
        for (editor in GlassParameterScope.entries) {
            val preset = requireNotNull(HeadsUpGlassPresetJson.import(HeadsUpGlassPresetJson.export(settings, editor), editor))
            for (changed in listOf(editor.moduleDefault(settings), editor.systemDefault(settings), editor.importPreset(settings, preset))) {
                assertTrue(changed.globalGlassBlurEnabled)
                assertTrue(changed.shadeCardGlassBlurEnabled)
                assertEquals(500f, changed.shadeCardGlassBlurRadius, 0f)
                assertEquals(60f, changed.shadeCardBackgroundBlurPercent, 0f)
                assertEquals(80f, changed.globalBackgroundBlurPercent, 0f)
                assertTrue(changed.globalBackgroundDimEnabled)
                assertEquals(35f, changed.globalBackgroundDimPercent, 0f)
            }
        }
    }

    @Test fun oldVersionTwoRecipeStillLoadsWithoutOverwritingGlobalBlur() {
        val settings = ModifierSettings(globalGlassBlurEnabled = true, shadeCardGlassBlurRadius = 700f,
            shadeCardBackgroundBlurPercent = 150f, shadeCardGlassBlurEnabled = true)
        val root = JSONObject(HeadsUpGlassPresetJson.export(settings, scope))
            .put("version", 2).put("backgroundBlurPercent", 40)
            .put("glassBlurEnabled", false).put("glassBlurRadius", 20)
        val preset = requireNotNull(HeadsUpGlassPresetJson.import(root.toString(), scope))
        val imported = scope.importPreset(settings, preset)
        assertTrue(imported.globalGlassBlurEnabled)
        assertTrue(imported.shadeCardGlassBlurEnabled)
        assertEquals(700f, imported.shadeCardGlassBlurRadius, 0f)
        assertEquals(150f, imported.shadeCardBackgroundBlurPercent, 0f)
    }
}
