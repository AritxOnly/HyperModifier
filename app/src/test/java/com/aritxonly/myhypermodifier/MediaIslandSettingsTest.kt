package com.aritxonly.myhypermodifier

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class MediaIslandSettingsTest {
    @Test fun disabledFeaturesRetainSavedValuesAndRuntimeGateWinsOverThem() {
        val context = RuntimeEnvironment.getApplication()
        val saved = ModifierSettings().copy(disableMediaIslandHooks = true, mediaLayoutPreset = "compact",
            mediaEnabled = true, islandEnabled = true, superIslandWhitelistDisabled = true,
            dynamicIslandOpacityEnabled = true, dynamicIslandNotificationOpacityEnabled = true,
            statusBarWifiMarginLeft = .35f, statusBarWifiMarginRight = -.45f)
        ModifierSettingsStore.save(context, saved)
        val loaded = ModifierSettingsStore.load(context)
        assertTrue(loaded.disableMediaIslandHooks)
        assertTrue(loaded.mediaEnabled)
        assertEquals("compact", loaded.mediaLayoutPreset)
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(ModifierSettingsStore.backupValues(context)),
            ModifierSettingsStore.backupSchema())
        assertEquals(true, decoded["disable_media_island_hooks"])
        assertEquals(.35f, decoded["status_bar_wifi_margin_left"])
        assertEquals(-.45f, decoded["status_bar_wifi_margin_right"])
        val bundle = ModifierSettingsStore.toBundle(loaded)
        assertTrue(bundle.getBoolean("disable_media_island_hooks"))
        assertEquals(.35f, bundle.getFloat("status_bar_wifi_margin_left"), 0f)

        val field = ModuleSettings::class.java.getDeclaredField("remotePreferences").apply { isAccessible = true }
        val load = ModuleSettings::class.java.getDeclaredMethod("load").apply { isAccessible = true }
        val previous = field.get(null)
        try {
            field.set(null, context.getSharedPreferences("modifier_settings", Context.MODE_PRIVATE))
            assertEquals(true, load.invoke(null))
            assertTrue(ModuleSettings.disableMediaIslandHooks)
            assertFalse(ModuleSettings.mediaEnabled)
            assertFalse(ModuleSettings.islandEnabled)
            assertFalse(ModuleSettings.islandProgressBar)
            assertFalse(ModuleSettings.superIslandWhitelistDisabled)
            assertFalse(ModuleSettings.dynamicIslandOpacityEnabled)
            assertFalse(ModuleSettings.dynamicIslandNotificationOpacityEnabled)
            assertEquals(0, ModuleSettings.activeMediaPresetHeight())
            assertEquals(.35f, ModuleSettings.statusBarWifiMarginLeft, 0f)
            ModifierSettingsStore.save(context, loaded.copy(disableMediaIslandHooks = false))
            assertEquals(true, load.invoke(null))
            assertTrue(ModuleSettings.mediaEnabled)
            assertTrue(ModuleSettings.superIslandWhitelistDisabled)
            assertEquals(80, ModuleSettings.activeMediaPresetHeight())
        } finally {
            ModifierSettingsStore.save(context, ModifierSettings())
            load.invoke(null)
            field.set(null, previous)
        }
    }
    @Test fun largeCoverSurvivesPreferencesBackupAndRuntimeBundle() {
        val context = RuntimeEnvironment.getApplication()
        val selected = MediaLayoutPresets.select(ModifierSettings(), MediaLayoutPresets.LARGE_COVER, context)
        val saved = selected.copy(largeCoverMediaHeight = 164f, largeCoverMediaXml = "<normal />",
            largeCoverMediaIslandXml = "<island />")
        ModifierSettingsStore.save(context, saved)
        val loaded = ModifierSettingsStore.load(context)
        assertEquals(saved.largeCoverMediaHeight, loaded.largeCoverMediaHeight)
        assertEquals("<normal />", loaded.largeCoverMediaXml)
        assertEquals("<island />", loaded.largeCoverMediaIslandXml)
        assertEquals("大封面", MediaLayoutPresets.label(MediaLayoutPresets.selected(loaded)))
        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(ModifierSettingsStore.backupValues(context)),
            ModifierSettingsStore.backupSchema())
        assertEquals(164f, decoded["large_cover_media_height"])
        assertEquals("<island />", decoded["large_cover_media_island_xml"])
        val bundle = ModifierSettingsStore.toBundle(loaded)
        assertEquals("large_cover", bundle.getString("media_layout_preset"))
        assertEquals(164f, bundle.getFloat("large_cover_media_height"))
        assertEquals("<normal />", bundle.getString("large_cover_media_xml"))
        ModifierSettingsStore.save(context, ModifierSettings())
    }

    @Test fun oldBundledCompactMigratesWithoutOverwritingEditedLayouts() {
        val context = RuntimeEnvironment.getApplication()
        val legacy = javaClass.getResourceAsStream("/media_compact_legacy.xml")!!.bufferedReader().use { it.readText() }
        val legacyIsland = legacy.replace("@id/media_bg\"", "@id/media_bg_view\"")
        ModifierSettingsStore.save(context, ModifierSettings(mediaLayoutPreset = "compact", compactMediaHeight = 84f,
            compactMediaXml = legacy, compactMediaIslandXml = legacyIsland))
        context.getSharedPreferences("modifier_settings", Context.MODE_PRIVATE).edit().remove("media_layout_version").commit()
        val migrated = ModifierSettingsStore.load(context)
        assertEquals(80f, migrated.compactMediaHeight)
        assertEquals("", migrated.compactMediaXml)
        assertEquals("", migrated.compactMediaIslandXml)
        ModifierSettingsStore.save(context, migrated.copy(compactMediaXml = "<edited />", compactMediaHeight = 84f))
        val edited = ModifierSettingsStore.load(context)
        assertEquals(84f, edited.compactMediaHeight)
        assertEquals("<edited />", edited.compactMediaXml)
        ModifierSettingsStore.save(context, ModifierSettings())
    }

    @Test fun newCompactCanSave84dpWithoutBeingMigratedAgain() {
        val context = RuntimeEnvironment.getApplication()
        ModifierSettingsStore.save(context, ModifierSettings(mediaLayoutPreset = "compact", compactMediaHeight = 84f))
        assertEquals(84f, ModifierSettingsStore.load(context).compactMediaHeight)
        ModifierSettingsStore.restoreBackup(context, mapOf("compact_media_height" to 84f, "compact_media_xml" to "", "compact_media_island_xml" to ""))
        assertEquals(80f, ModifierSettingsStore.load(context).compactMediaHeight)
        ModifierSettingsStore.save(context, ModifierSettings())
    }

    @Test fun oldLargeCoverDefaultMigratesOnceAndCustomHeightsRemainIndependent() {
        assertEquals(132f, MediaPresetDefaults.migrateLargeCoverHeight(1, 148f, "", ""))
        assertEquals(132f, MediaPresetDefaults.migrateLargeCoverHeight(2, 136f, "", ""))
        assertEquals(136f, MediaPresetDefaults.migrateLargeCoverHeight(3, 136f, "", ""))
        assertEquals(132f, MediaPresetDefaults.migrateLargeCoverHeight(4, 140f, "", ""))
        assertEquals(140f, MediaPresetDefaults.migrateLargeCoverHeight(5, 140f, "", ""))
        assertEquals(148f, MediaPresetDefaults.migrateLargeCoverHeight(2, 148f, "", ""))
        assertEquals(144f, MediaPresetDefaults.migrateLargeCoverIslandHeight(3, 136f, "", ""))
        assertEquals(144f, MediaPresetDefaults.migrateLargeCoverIslandHeight(4, 168f, "", ""))
        assertEquals(168f, MediaPresetDefaults.migrateLargeCoverIslandHeight(5, 168f, "", ""))
        assertEquals(168f, MediaPresetDefaults.migrateLargeCoverIslandHeight(4, 168f, "", "<edited />"))
        assertEquals(136f, MediaPresetDefaults.migrateLargeCoverIslandHeight(4, 136f, "", ""))
        assertEquals(136f, MediaPresetDefaults.migrateLargeCoverIslandHeight(3, 136f, "", "<edited />"))
        assertEquals(164f, MediaPresetDefaults.migrateLargeCoverHeight(1, 164f, "", ""))
        assertEquals(148f, MediaPresetDefaults.migrateLargeCoverHeight(1, 148f, "<edited />", ""))
    }

    @Test fun surfaceSelectorsAndHeightsRemainIndependentThroughSaveAndRuntime() {
        val context = RuntimeEnvironment.getApplication()
        var settings = ModifierSettings(mediaLayoutPreset = "large_cover", largeCoverMediaHeight = 136f)
        settings = MediaLayoutPresets.select(settings, "standard", context, true)
        settings = MediaLayoutPresets.withHeight(settings, "standard", 158f, true)
        assertEquals("large_cover", MediaLayoutPresets.selected(settings))
        assertEquals("standard", MediaLayoutPresets.selected(settings, true))
        settings = MediaLayoutPresets.withHeight(settings, "large_cover", 144f)
        assertEquals(144f, MediaLayoutPresets.height(settings, "large_cover", true))
        ModifierSettingsStore.save(context, settings)
        val loaded = ModifierSettingsStore.load(context)
        assertEquals("standard", loaded.mediaIslandLayoutPreset)
        assertEquals(158f, MediaLayoutPresets.height(loaded, "standard", true))
        val backup = SettingsBackupCodec.decode(SettingsBackupCodec.encode(ModifierSettingsStore.backupValues(context)), ModifierSettingsStore.backupSchema())
        assertEquals("standard", backup["media_island_layout_preset"])
        assertEquals(158f, backup["standard_media_island_height"])
        val remote = ModuleSettings::class.java.getDeclaredField("remotePreferences").apply { isAccessible = true }
        val load = ModuleSettings::class.java.getDeclaredMethod("load").apply { isAccessible = true }
        val previous = remote.get(null)
        try {
            remote.set(null, context.getSharedPreferences("modifier_settings", Context.MODE_PRIVATE))
            assertEquals(true, load.invoke(null))
            assertEquals("large_cover", ModuleSettings.activeMediaPreset(false))
            assertEquals("standard", ModuleSettings.activeMediaPreset(true))
            assertEquals(144, ModuleSettings.activeMediaPresetHeight(false))
            assertEquals(158, ModuleSettings.activeMediaPresetHeight(true))
            // Old stored settings without surface-specific keys retain their previous layout.
            context.getSharedPreferences("modifier_settings", Context.MODE_PRIVATE).edit()
                .remove("media_island_layout_preset").remove("large_cover_media_island_height").commit()
            assertEquals(true, load.invoke(null))
            assertEquals("large_cover", ModuleSettings.activeMediaPreset(true))
            assertEquals(144, ModuleSettings.activeMediaPresetHeight(true))
            val independent = MediaLayoutPresets.select(ModifierSettingsStore.load(context), "compact", context)
            assertEquals("large_cover", MediaLayoutPresets.selected(independent, true))
        } finally {
            ModifierSettingsStore.save(context, ModifierSettings())
            load.invoke(null)
            remote.set(null, previous)
        }
    }

}
