package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class GestureHandlePresetJsonTest {
    private fun json(preset: String = "module", apps: String = "{}") =
        """{"format":"myhypermodifier-gesture-handle","version":1,"preset":"$preset","apps":$apps}"""

    @Test fun exportedPresetsRoundTripEveryOverrideAndBothDefaults() {
        val modes = mapOf("a.app" to "show", "b.app" to "hide", "c.app" to "immersive", "d.app" to "system")
        for (module in listOf(true, false)) {
            val settings = ModifierSettings(gestureHandleModulePreset = module, gestureHandleAppModes = modes)
            val restored = requireNotNull(GestureHandlePresetJson.parse(GestureHandlePresetJson.export(settings)))
            assertEquals(module, restored.modulePreset)
            assertEquals(modes, restored.apps)
        }
    }

    @Test fun exportIncludesOnlyGestureSettingsAndSupportsEmptyOverrides() {
        val settings = ModifierSettings(notificationsEnabled = true, gestureHandleModulePreset = false)
        val json = GestureHandlePresetJson.export(settings)
        assertFalse(json.contains("notifications"))
        assertTrue(requireNotNull(GestureHandlePresetJson.parse(json)).apps.isEmpty())
    }

    @Test fun importsBothPresetsAndAllModesIncludingUninstalledApps() {
        val preset = requireNotNull(GestureHandlePresetJson.parse(json(apps = """{
            "app.show":"show", "app.hide":"hide", "app.immersive":"immersive", "app.stock":"system"
        }""")))
        assertTrue(preset.modulePreset)
        assertEquals(4, preset.apps.size)
        assertEquals("system", preset.apps["app.stock"])
        assertFalse(requireNotNull(GestureHandlePresetJson.parse(json("system"))).modulePreset)
    }

    @Test fun importReplacesGestureSettingsAndPreservesOtherSettings() {
        val original = ModifierSettings(
            notificationsEnabled = true,
            gestureHandleModulePreset = true,
            gestureHandleAppModes = mapOf("old.app" to "hide"),
        )
        val imported = requireNotNull(GestureHandlePresetJson.parse(json("system", """{"new.app":"show"}""")))
        val result = imported.applyTo(original)
        assertTrue(result.notificationsEnabled)
        assertFalse(result.gestureHandleModulePreset)
        assertEquals(mapOf("new.app" to "show"), result.gestureHandleAppModes)
        assertEquals(mapOf("old.app" to "hide"), original.gestureHandleAppModes)
    }

    @Test fun rejectsInvalidOrPartialPresetsRatherThanSilentlyDroppingRules() {
        listOf(
            "", "[]", "{}", json("unknown"), json(apps = "[]"),
            json(apps = """{"app":"unknown"}"""),
            json(apps = """{"app":null}"""),
            json(apps = """{"app":true}"""),
            json(apps = """{"":"show"}"""),
            json(apps = """{"bad package":"show"}"""),
            json().replace("\"version\":1", "\"version\":2"),
            json().replace("\"version\":1", "\"version\":\"1\""),
            json().replace("myhypermodifier-gesture-handle", "other-format"),
            json().replace("\"apps\":{}", "\"typo\":{}"),
            json() + " trailing data",
            json(apps = """{"valid":"show","invalid":"invalid"}"""),
        ).forEach { assertNull(it, GestureHandlePresetJson.parse(it)) }
    }

    @Test fun acceptsUtf8BomAndRejectsOversizedInput() {
        assertNotNull(GestureHandlePresetJson.parse("\uFEFF" + json()))
        assertNull(GestureHandlePresetJson.parse(json() + " ".repeat(GestureHandlePresetJson.MAX_LENGTH)))
    }
}
