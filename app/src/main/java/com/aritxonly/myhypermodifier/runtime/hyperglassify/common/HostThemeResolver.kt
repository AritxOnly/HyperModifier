package com.aritxonly.myhypermodifier

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat

/** Pure color voting keeps host-theme detection independent from configuration-only assumptions. */
internal object HostThemePolicy {
    fun colorDark(color: Int): Boolean? {
        if (color ushr 24 < 0x40) return null
        val luminance = (
            (color shr 16 and 0xFF) * 0.2126f +
                (color shr 8 and 0xFF) * 0.7152f +
                (color and 0xFF) * 0.0722f
            ) / 255f
        return when {
            luminance <= 0.42f -> false
            luminance >= 0.62f -> true
            else -> null
        }
    }

    /** A light/dark background votes opposite to light/dark foreground text and icons. */
    fun backgroundColorDark(color: Int): Boolean? = when (colorDark(color)) {
        true -> false
        false -> true
        null -> null
    }

    fun resolve(
        colors: List<Int>,
        lightSystemBars: Boolean?,
        lightTheme: Boolean?,
        configurationDark: Boolean,
        preferHostWindow: Boolean = false,
        nativeBackgroundDark: Boolean? = null,
        preferNativeBackground: Boolean = false,
    ): Boolean {
        if (preferNativeBackground) {
            nativeBackgroundDark?.let { return it }
        }
        if (preferHostWindow) {
            // Fixed AppCompat.Light activities (notably Xianyu) keep isLightTheme=true even when
            // their effective presentation is dark. Vote over independent host-owned signals:
            // current resources configuration, status/navigation-bar icon appearance and theme.
            // This also keeps a transient light/dark page header from flipping the whole dock.
            var hostDarkVotes = if (configurationDark) 1 else 0
            var hostLightVotes = if (configurationDark) 0 else 1
            lightSystemBars?.let { light ->
                if (light) hostLightVotes++ else hostDarkVotes++
            }
            lightTheme?.let { light ->
                if (light) hostLightVotes++ else hostDarkVotes++
            }
            if (hostDarkVotes != hostLightVotes) return hostDarkVotes > hostLightVotes
            return configurationDark
        }
        var darkVotes = 0
        var lightVotes = 0
        colors.forEach { color ->
            when (colorDark(color)) {
                true -> darkVotes++
                false -> lightVotes++
                null -> Unit
            }
        }
        if (darkVotes != lightVotes) return darkVotes > lightVotes
        nativeBackgroundDark?.let { return it }
        lightSystemBars?.let { return !it }
        lightTheme?.let { return !it }
        return configurationDark
    }
}

internal object HostThemeResolver {
    fun resolve(
        activity: Activity,
        nativeBar: View?,
        tabs: List<View>,
        selectedIndex: Int,
        explicitDark: Boolean? = null,
        preferHostWindow: Boolean = false,
        preferNativeBackground: Boolean = false,
    ): Boolean {
        explicitDark?.let { return it }
        val colors = ArrayList<Int>(8)
        tabs.getOrNull(selectedIndex)?.let { selected ->
            selected.collectThemeColors(colors)
        }
        if (colors.isEmpty()) {
            nativeBar?.collectThemeColors(colors, recursive = false)
        }
        val nativeBackgroundDark = (nativeBar?.background as? ColorDrawable)?.color?.let {
            backgroundColor -> if (Color.alpha(backgroundColor) >= 0xB0) {
                HostThemePolicy.backgroundColorDark(backgroundColor)
            } else {
                null
            }
        }
        return HostThemePolicy.resolve(
            colors = colors.take(6),
            lightSystemBars = lightSystemBars(activity),
            lightTheme = lightTheme(activity),
            configurationDark = (
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES,
            preferHostWindow = preferHostWindow,
            nativeBackgroundDark = nativeBackgroundDark,
            preferNativeBackground = preferNativeBackground,
        )
    }

    private fun View.collectThemeColors(target: MutableList<Int>, recursive: Boolean = true) {
        when (this) {
            is TextView -> currentTextColor.let { if (Color.alpha(it) >= 0x80) target += it }
            is ImageView -> imageTintList?.defaultColor?.let {
                if (Color.alpha(it) >= 0x80) target += it
            }
        }
        if (recursive && this is ViewGroup) {
            repeat(childCount) { getChildAt(it).collectThemeColors(target) }
        }
    }

    private fun lightSystemBars(activity: Activity): Boolean? = runCatching {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        lightStatus.takeIf { it == lightNavigation }
    }.getOrNull()

    private fun lightTheme(activity: Activity): Boolean? = runCatching {
        val value = TypedValue()
        if (!activity.theme.resolveAttribute(android.R.attr.isLightTheme, value, true) ||
            value.type != TypedValue.TYPE_INT_BOOLEAN
        ) {
            return@runCatching null
        }
        value.data != 0
    }.getOrNull()
}
