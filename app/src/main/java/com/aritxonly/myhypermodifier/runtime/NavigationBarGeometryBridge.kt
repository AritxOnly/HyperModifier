package com.aritxonly.myhypermodifier

import com.aritxonly.deadliner.ui.navigation.FloatingNavigationGeometryOverride
import com.aritxonly.deadliner.ui.navigation.FloatingNavigationGeometryState
import com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarGeometry

internal object NavigationBarGeometryBridge {
    @JvmStatic
    fun applyRaw(raw: String?) {
        val value = NavigationBarGeometryParameters.decode(raw.orEmpty())
        MiuixFloatingTabBarGeometry.update(
            FloatingNavigationGeometryState(
                syncAll = value.syncAll,
                global = value.global.toComposeOverride(),
                targets = value.targets.mapValues { (_, geometry) ->
                    geometry.toComposeOverride()
                },
            ),
        )
    }
}

private fun NavigationBarGeometry.toComposeOverride() = FloatingNavigationGeometryOverride(
    leftMarginDp = leftMarginDp,
    maximumWidthDp = maximumWidthDp.takeIf { it > 0f },
    heightDp = heightDp.takeIf { it > 0f },
)
