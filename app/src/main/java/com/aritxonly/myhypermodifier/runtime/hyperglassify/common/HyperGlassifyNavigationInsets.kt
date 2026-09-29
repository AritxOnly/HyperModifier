package com.aritxonly.myhypermodifier

import android.content.res.Configuration
import android.view.View
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Always adds the configured visual clearance after the system navigation-bar inset. */
@Composable
internal fun hyperGlassifyHiddenNavigationLift(): Dp {
    return ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f).dp
}

/** Keeps a panel at the same height when returning from a page briefly clears its insets. */
internal class StableNavigationBarInset {
    private var lastBottomPx = 0
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    fun bottomPx(panel: View, decor: View): Int {
        val orientation = decor.resources.configuration.orientation
        if (lastOrientation != orientation) {
            lastBottomPx = 0
            lastOrientation = orientation
        }
        val navigationBars = WindowInsets.Type.navigationBars()
        val panelBottom = panel.rootWindowInsets
            ?.getInsetsIgnoringVisibility(navigationBars)?.bottom ?: 0
        val decorBottom = decor.rootWindowInsets
            ?.getInsetsIgnoringVisibility(navigationBars)?.bottom ?: 0
        val current = maxOf(panelBottom, decorBottom)
        if (current > 0) lastBottomPx = current
        return lastBottomPx
    }
}
