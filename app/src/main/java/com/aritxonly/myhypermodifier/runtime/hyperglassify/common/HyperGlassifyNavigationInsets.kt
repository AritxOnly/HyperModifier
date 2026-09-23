package com.aritxonly.myhypermodifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Always adds the configured visual clearance after the system navigation-bar inset. */
@Composable
internal fun hyperGlassifyHiddenNavigationLift(): Dp {
    return ModuleSettings.hyperGlassifyHiddenNavigationLift.coerceIn(0f, 48f).dp
}
