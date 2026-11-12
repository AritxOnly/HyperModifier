package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class NotificationWidthSettingsTest {
    @Test fun independentValuesSurviveStorageRemoteAndBackup() {
        val context = RuntimeEnvironment.getApplication()
        val settings = ModifierSettings(notificationMaxWidthEnabled = true, notificationMaxWidth = 440f,
            notificationWidthMode = NotificationWidthPolicy.MARGINS, notificationSideMargin = 32f,
            lockscreenNotificationWidthMode = NotificationWidthPolicy.FIXED, lockscreenNotificationSideMargin = 8f,
            lockscreenNotificationMaxWidthEnabled = true, lockscreenNotificationMaxWidth = 320f)
        ModifierSettingsStore.save(context, settings)
        val stored = ModifierSettingsStore.load(context)
        assertTrue(stored.notificationMaxWidthEnabled)
        assertTrue(stored.lockscreenNotificationMaxWidthEnabled)
        assertEquals(NotificationWidthPolicy.MARGINS, stored.notificationWidthMode)
        assertEquals(32f, stored.notificationSideMargin)
        assertEquals(NotificationWidthPolicy.FIXED, stored.lockscreenNotificationWidthMode)
        assertEquals(8f, stored.lockscreenNotificationSideMargin)
        assertEquals(440f, stored.notificationMaxWidth)
        assertEquals(320f, stored.lockscreenNotificationMaxWidth)
        val bundle = ModifierSettingsStore.toBundle(settings)
        assertTrue(bundle.getBoolean("notification_max_width_enabled"))
        assertTrue(bundle.getBoolean("lockscreen_notification_max_width_enabled"))
        assertEquals(1, bundle.getInt("notification_width_mode"))
        assertEquals(32f, bundle.getFloat("notification_side_margin"))
        assertEquals(0, bundle.getInt("lockscreen_notification_width_mode"))
        assertEquals(8f, bundle.getFloat("lockscreen_notification_side_margin"))
        assertEquals(440f, bundle.getFloat("notification_max_width"))
        assertEquals(320f, bundle.getFloat("lockscreen_notification_max_width"))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).notificationMaxWidthEnabled)
        assertFalse(ModifierSettingsStore.load(context).lockscreenNotificationMaxWidthEnabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        assertEquals(440f, ModifierSettingsStore.load(context).notificationMaxWidth)
        assertEquals(320f, ModifierSettingsStore.load(context).lockscreenNotificationMaxWidth)
        assertEquals(1, ModifierSettingsStore.load(context).notificationWidthMode)
        assertEquals(32f, ModifierSettingsStore.load(context).notificationSideMargin)
        assertEquals(8f, ModifierSettingsStore.load(context).lockscreenNotificationSideMargin)
        assertFalse(ModifierSettingsPresets.moduleDefault().notificationMaxWidthEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().lockscreenNotificationMaxWidthEnabled)
    }
    @Test fun legacyWidthOnlyPreferencesKeepFixedModeAndOriginalWidth() {
        val context = RuntimeEnvironment.getApplication()
        ModifierSettingsStore.save(context, ModifierSettings(notificationMaxWidthEnabled = true, notificationMaxWidth = 450f,
            lockscreenNotificationMaxWidthEnabled = true, lockscreenNotificationMaxWidth = 350f))
        context.getSharedPreferences(ModifierSettingsStore.PREFS, android.content.Context.MODE_PRIVATE).edit()
            .remove("notification_width_mode").remove("notification_side_margin")
            .remove("lockscreen_notification_width_mode").remove("lockscreen_notification_side_margin").commit()
        val loaded = ModifierSettingsStore.load(context)
        assertEquals(NotificationWidthPolicy.FIXED, loaded.notificationWidthMode)
        assertEquals(NotificationWidthPolicy.FIXED, loaded.lockscreenNotificationWidthMode)
        assertEquals(450f, loaded.notificationMaxWidth)
        assertEquals(350f, loaded.lockscreenNotificationMaxWidth)
        assertEquals(14f, loaded.notificationSideMargin)
        assertTrue(loaded.notificationMaxWidthEnabled)
    }
    @Test fun modeSwitchPreservesBothSlidersValues() {
        val context = RuntimeEnvironment.getApplication()
        val selected = ModifierSettings(notificationWidthMode = NotificationWidthPolicy.FIXED,
            notificationMaxWidth = 430f, notificationSideMargin = 26f)
        ModifierSettingsStore.save(context, selected.copy(notificationWidthMode = NotificationWidthPolicy.MARGINS))
        val margins = ModifierSettingsStore.load(context)
        assertEquals(430f, margins.notificationMaxWidth)
        ModifierSettingsStore.save(context, margins.copy(notificationWidthMode = NotificationWidthPolicy.FIXED))
        assertEquals(26f, ModifierSettingsStore.load(context).notificationSideMargin)
    }

}
