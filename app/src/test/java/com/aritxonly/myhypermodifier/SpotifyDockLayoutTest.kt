package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyDockLayoutTest {
    @Test fun rotationPreservesGapEvenWhenTheNativeBarEndsAboveTheWindowBottom() {
        for (barBottom in listOf(1000, 936, 640, 1000)) {
            val tabTop = barBottom - 100
            for (margin in listOf(-4, 0, 8, 16)) {
                var height = 124
                repeat(2) {
                    val top = barBottom - height
                    height = requireNotNull(SpotifyDockLayout.reserveHeight(
                        top, height, tabTop, 132, top - margin, 6,
                    ))
                    assertEquals(6, tabTop - (barBottom - height - margin))
                }
            }
        }
    }

    @Test fun staleRotationAndAnimatingPlayerMeasurementsDoNotChangeTheReservation() {
        assertNull(SpotifyDockLayout.reserveHeight(900, 100, 400, 132, 892, 6))
        assertNull(SpotifyDockLayout.reserveHeight(400, 100, 900, 132, 392, 6))
        assertNull(SpotifyDockLayout.reserveHeight(900, 100, 900, 132, 960, 6))
    }

    @Test fun absentPlayerReservesOnlyTheTabRegion() {
        assertEquals(100, SpotifyDockLayout.reserveHeight(880, 120, 900, 132, null, 6))
    }
}
