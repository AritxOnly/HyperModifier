package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class GestureHandlePresetJsonTest {
    private fun json(preset: String = "module", apps: String = "{}") =
        """{"format":"myhypermodifier-gesture-handle","version":1,"preset":"$preset","apps":$apps}"""

    @Test fun exportedPresetsRoundTripEveryOverrideAndAllDefaults() {
        val modes = mapOf("a.app" to "show", "b.app" to "hide", "c.app" to "immersive", "d.app" to "system")
        for (preset in listOf(GestureHandlePresets.MODULE, GestureHandlePresets.IMMERSIVE,
            GestureHandlePresets.HIDE, GestureHandlePresets.SHOW)) {
            val settings = ModifierSettings(gestureHandlePreset = preset, gestureHandleAppModes = modes,
                gestureHandleTouchReveal = true, gestureHandleSwipeMotion = true,
                gestureHandleTouchAreaDp = 32f)
            val restored = requireNotNull(GestureHandlePresetJson.parse(GestureHandlePresetJson.export(settings)))
            assertEquals(preset, restored.preset)
            assertEquals(if (preset == GestureHandlePresets.MODULE)
                GestureHandlePresets.MODULE_APPS + modes else modes, restored.apps)
            assertTrue(restored.touchReveal)
            assertTrue(restored.swipeMotion)
            assertEquals(32f, restored.bottomTouchAreaDp, 0f)
        }
    }

    @Test fun exportIncludesOnlyGestureSettingsAndSupportsEmptyOverrides() {
        val settings = ModifierSettingsPresets.gestureHandlePreset(
            ModifierSettings(notificationsEnabled = true), GestureHandlePresets.SHOW)
        val json = GestureHandlePresetJson.export(settings)
        assertFalse(json.contains("notifications"))
        assertTrue(requireNotNull(GestureHandlePresetJson.parse(json)).apps.isEmpty())
    }

    @Test fun importsPresetsAndAllModesIncludingUninstalledApps() {
        val preset = requireNotNull(GestureHandlePresetJson.parse(json(apps = """{
            "app.show":"show", "app.hide":"hide", "app.immersive":"immersive", "app.stock":"system"
        }""")))
        assertEquals(GestureHandlePresets.MODULE, preset.preset)
        assertEquals(4, preset.apps.size)
        assertEquals("system", preset.apps["app.stock"])
        assertEquals(GestureHandlePresets.SHOW,
            requireNotNull(GestureHandlePresetJson.parse(json("system"))).preset)
        assertEquals(GestureHandlePresets.STOCK,
            requireNotNull(GestureHandlePresetJson.parse(json("stock"))).preset)
        assertFalse(preset.touchReveal)
        assertFalse(preset.swipeMotion)
        assertEquals(16f, preset.bottomTouchAreaDp, 0f)
    }

    @Test fun importReplacesGestureSettingsAndPreservesOtherSettings() {
        val original = ModifierSettings(
            notificationsEnabled = true,
            gestureHandlePreset = GestureHandlePresets.MODULE,
            gestureHandleAppModes = mapOf("old.app" to "hide"),
        )
        val imported = requireNotNull(GestureHandlePresetJson.parse(json("system", """{"new.app":"show"}""")))
        val result = imported.applyTo(original)
        assertTrue(result.notificationsEnabled)
        assertEquals(GestureHandlePresets.SHOW, result.gestureHandlePreset)
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
            json().dropLast(1) + ",\"touchReveal\":1}",
            json().dropLast(1) + ",\"swipeMotion\":\"true\"}",
            json().dropLast(1) + ",\"bottomTouchAreaDp\":-1}",
            json().dropLast(1) + ",\"bottomTouchAreaDp\":33}",
            json().dropLast(1) + ",\"bottomTouchAreaDp\":\"24\"}",
        ).forEach { assertNull(it, GestureHandlePresetJson.parse(it)) }
    }

    @Test fun acceptsUtf8BomAndRejectsOversizedInput() {
        assertNotNull(GestureHandlePresetJson.parse("\uFEFF" + json()))
        assertNull(GestureHandlePresetJson.parse(json() + " ".repeat(GestureHandlePresetJson.MAX_LENGTH)))
    }
}
