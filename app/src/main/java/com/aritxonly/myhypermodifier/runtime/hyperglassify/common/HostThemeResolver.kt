package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration

/**
 * The app's resolved night mode already combines its in-app light/dark choice with
 * "follow system". Page backgrounds and native tab palettes are not theme settings.
 */
internal object HostThemePolicy {
    fun darkFromUiMode(uiMode: Int): Boolean =
        uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun resolve(appSettingDark: Boolean?, uiMode: Int): Boolean =
        appSettingDark ?: darkFromUiMode(uiMode)
}

internal object HostThemeResolver {
    fun resolve(activity: Activity, appSettingDark: Boolean? = null): Boolean =
        HostThemePolicy.resolve(
            appSettingDark = appSettingDark,
            uiMode = activity.resources.configuration.uiMode,
        )
}
