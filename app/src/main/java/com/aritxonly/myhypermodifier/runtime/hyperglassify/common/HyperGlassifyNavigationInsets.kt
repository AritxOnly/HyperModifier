package com.aritxonly.myhypermodifier

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Adds a configurable fallback only when the system reports no visible navigation-bar inset. */
@Composable
internal fun hyperGlassifyHiddenNavigationLift(): Dp {
    val density = LocalDensity.current
    val navigationBottomInset = WindowInsets.navigationBars.getBottom(density)
    return if (navigationBottomInset == 0) {
        ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f).dp
    } else {
        0.dp
    }
}
