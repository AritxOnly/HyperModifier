package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBarGeometryParametersTest {
    @Test
    fun roundTripPreservesGlobalAndPerTargetValues() {
        val value = NavigationBarGeometryParameters(
            syncAll = false,
            selectedTarget = "QQ",
            global = NavigationBarGeometry(12f, 320f, 56f),
            targets = mapOf(
                "QQ" to NavigationBarGeometry(8f, 296f, 48f),
                "微信" to NavigationBarGeometry(16f, 0f, 52f),
            ),
        )

        assertEquals(value, NavigationBarGeometryParameters.decode(value.encode()))
    }

    @Test
    fun syncAllSelectsGlobalProfileOtherwiseSelectedTargetWins() {
        val value = NavigationBarGeometryParameters(
            syncAll = true,
            selectedTarget = "QQ",
            global = NavigationBarGeometry(10f, 340f, 58f),
            targets = mapOf("QQ" to NavigationBarGeometry(0f, 296f, 48f)),
        )
        assertEquals(NavigationBarGeometry(10f, 340f, 58f), value.activeGeometry())

        val perTarget = value.copy(syncAll = false)
        assertEquals(NavigationBarGeometry(0f, 296f, 48f), perTarget.activeGeometry())
    }

    @Test
    fun zeroKeepsAutomaticGeometryAndValuesAreBounded() {
        val value = NavigationBarGeometry(
            leftMarginDp = -10f,
            maximumWidthDp = 20f,
            heightDp = 120f,
        ).normalized()
        assertEquals(0f, value.leftMarginDp)
        assertEquals(160f, value.maximumWidthDp)
        assertEquals(80f, value.heightDp)
        assertFalse(NavigationBarGeometry(0f, 0f, 0f).let { !it.isEmpty() })
    }

    @Test
    fun bridgePublishesPerTargetGeometry() {
        val value = NavigationBarGeometryParameters(
            syncAll = false,
            selectedTarget = "QQ",
            targets = mapOf("QQ" to NavigationBarGeometry(6f, 300f, 50f)),
        )
        NavigationBarGeometryBridge.applyRaw(value.encode())
        val qq = com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarGeometry
            .resolve("QQ")
        assertEquals(6f, qq.leftMarginDp)
        assertEquals(300f, qq.maximumWidthDp)
        assertEquals(50f, qq.heightDp)
        assertTrue(
            com.aritxonly.deadliner.ui.navigation.MiuixFloatingTabBarGeometry
                .resolve("微信").let {
                    it.leftMarginDp == 0f && it.maximumWidthDp == null && it.heightDp == null
                },
        )
        NavigationBarGeometryBridge.applyRaw("")
    }
}
