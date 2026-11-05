package com.aritxonly.deadliner.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Immutable
data class FloatingNavigationGeometryOverride(
    val leftMarginDp: Float = 0f,
    val maximumWidthDp: Float? = null,
    val heightDp: Float? = null,
) {
    fun normalized(): FloatingNavigationGeometryOverride = copy(
        leftMarginDp = leftMarginDp.coerceIn(0f, 160f),
        maximumWidthDp = maximumWidthDp?.coerceIn(160f, 600f),
        heightDp = heightDp?.coerceIn(36f, 80f),
    )
}

@Immutable
data class FloatingNavigationGeometryState(
    val syncAll: Boolean = false,
    val global: FloatingNavigationGeometryOverride = FloatingNavigationGeometryOverride(),
    val targets: Map<String, FloatingNavigationGeometryOverride> = emptyMap(),
)

/**
 * Shared geometry for injected floating navigation bars. The module publishes a new immutable
 * snapshot after remote settings change, which triggers only bars that are currently composed.
 */
object MiuixFloatingTabBarGeometry {
    private var state by mutableStateOf(FloatingNavigationGeometryState())

    fun update(value: FloatingNavigationGeometryState) {
        state = value
    }

    fun resolve(targetKey: String?): FloatingNavigationGeometryOverride {
        val snapshot = state
        if (snapshot.syncAll) return snapshot.global
        return targetKey?.let(snapshot.targets::get) ?: FloatingNavigationGeometryOverride()
    }
}
