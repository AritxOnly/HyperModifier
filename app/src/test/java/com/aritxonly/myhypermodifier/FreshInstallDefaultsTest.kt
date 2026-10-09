package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class FreshInstallDefaultsTest {
    @Test fun freshInstallUsesTheImportedModulePreset() {
        for (settings in listOf(ModifierSettings(), ModifierSettingsPresets.moduleDefault())) {
            assertTrue(settings.moduleHooksEnabled)
            assertTrue(settings.notificationsEnabled)
            assertTrue(settings.controlCenterEnabled)
            assertTrue(settings.controlCenterFollowMiLinkBackgroundMaterial)
            assertFalse(settings.disableShadeGlassHooks)
            assertFalse(settings.settingsHomeEntryEnabled)
            assertFalse(settings.gestureHandleEnabled)
            assertTrue(settings.xiaomiHealthFloatingNavigationEnabled)
            assertTrue(settings.marketFloatingNavigationEnabled)
            assertTrue(settings.miHomeFloatingNavigationEnabled)
            assertTrue(settings.amapFloatingNavigationEnabled)
            assertTrue(settings.amapHideLongPressVoiceTabEnabled)
            assertTrue(settings.xiaomiCommunityFloatingNavigationEnabled)
            assertTrue(settings.bilibiliFloatingNavigationEnabled)
            assertTrue(settings.spotifyFloatingNavigationEnabled)
            assertFalse(settings.superIslandWhitelistDisabled)
            assertFalse(settings.lowerLockscreenPasswordPage)
            assertFalse(settings.progressiveLockscreenClockAvoidance)
        }
    }
}
