package com.aritxonly.myhypermodifier

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostThemePolicyTest {
    @Test
    fun darkComesOnlyFromTheAppsResolvedConfiguration() {
        assertTrue(
            HostThemePolicy.darkFromUiMode(
                Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
        assertFalse(
            HostThemePolicy.darkFromUiMode(
                Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
    }

    @Test
    fun undefinedNightModeFallsBackToLight() {
        assertFalse(HostThemePolicy.darkFromUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED))
        assertFalse(HostThemePolicy.darkFromUiMode(0))
    }

    @Test
    fun unrelatedUiModeBitsDoNotChangeTheNightMode() {
        assertTrue(
            HostThemePolicy.darkFromUiMode(
                Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR,
            ),
        )
        assertFalse(
            HostThemePolicy.darkFromUiMode(
                Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_TELEVISION,
            ),
        )
    }

    @Test
    fun explicitAppSettingWinsOverThePlatformConfiguration() {
        assertTrue(
            HostThemePolicy.resolve(
                appSettingDark = true,
                uiMode = Configuration.UI_MODE_NIGHT_NO,
            ),
        )
        assertFalse(
            HostThemePolicy.resolve(
                appSettingDark = false,
                uiMode = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertTrue(
            HostThemePolicy.resolve(
                appSettingDark = null,
                uiMode = Configuration.UI_MODE_NIGHT_YES,
            ),
        )
    }
}
