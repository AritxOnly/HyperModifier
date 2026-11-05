package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class FreshInstallDefaultsTest {
    @Test fun freshInstallUsesTheImportedModulePreset() {
        for (settings in listOf(ModifierSettings(), ModifierSettingsPresets.moduleDefault())) {
            assertTrue(settings.moduleHooksEnabled)
            assertEquals(LargeScreenDevicePolicy.AUTO, settings.deviceModeOverride)
            assertTrue(settings.notificationsEnabled)
            assertTrue(settings.controlCenterEnabled)
            assertTrue(settings.controlCenterFollowMiLinkBackgroundMaterial)
            assertFalse(settings.disableShadeGlassHooks)
            assertFalse(settings.settingsHomeEntryEnabled)
            assertFalse(settings.gestureHandleEnabled)
            assertTrue(settings.xiaomiHealthFloatingNavigationEnabled)
            assertTrue(settings.miMusicFloatingNavigationEnabled)
            assertFalse(settings.marketFloatingNavigationEnabled)
            assertTrue(settings.miHomeFloatingNavigationEnabled)
            assertTrue(settings.amapFloatingNavigationEnabled)
            assertTrue(settings.amapHideLongPressVoiceTabEnabled)
            assertTrue(settings.nioFloatingNavigationEnabled)
            assertTrue(settings.alipayFloatingNavigationEnabled)
            assertTrue(settings.douyinLiteFloatingNavigationEnabled)
            assertTrue(settings.kuaishouFloatingNavigationEnabled)
            assertTrue(settings.kuaishouLiteFloatingNavigationEnabled)
            assertTrue(settings.wechatFloatingNavigationEnabled)
            assertTrue(settings.wechatNavigationBadgesEnabled)
            assertTrue(settings.qqFloatingNavigationEnabled)
            assertTrue(settings.qqNavigationBadgesEnabled)
            assertTrue(settings.qqMusicFloatingNavigationEnabled)
            assertTrue(settings.xiaomiCommunityFloatingNavigationEnabled)
            assertTrue(settings.bilibiliFloatingNavigationEnabled)
            assertTrue(settings.spotifyFloatingNavigationEnabled)
            assertFalse(settings.superIslandWhitelistDisabled)
            assertFalse(settings.statusBarShowSeconds)
            assertFalse(settings.statusBarSolidBatteryFrame)
            assertFalse(settings.roundedNetworkIcons)
            assertFalse(settings.controlCenterShowBatteryPercent)
            assertFalse(settings.notificationClockShowSeconds)
            assertFalse(settings.statusBarHideClockLeadingZero)
            assertFalse(settings.lowerLockscreenPasswordPage)
            assertFalse(settings.progressiveLockscreenClockAvoidance)
        }
    }

    @Test fun systemPresetRestoresTheNativeAlipayDock() {
        assertFalse(ModifierSettingsPresets.systemDefault().nioFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().alipayFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().miMusicFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().wechatFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().qqFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().douyinLiteFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().kuaishouFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().kuaishouLiteFloatingNavigationEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().qqMusicFloatingNavigationEnabled)
    }
}
