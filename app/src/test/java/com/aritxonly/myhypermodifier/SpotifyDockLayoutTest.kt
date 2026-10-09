package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyDockLayoutTest {
    @Test fun nativeMarginsStillLeaveTheRequestedGap() {
        for (margin in listOf(0, 8, 16)) {
            val height = SpotifyDockLayout.reserveHeight(1000, 900, 880, 880 - margin, 6)
            val resultingPlayerBottom = 1000 - height - margin
            assertEquals(6, 900 - resultingPlayerBottom)
        }
    }

    @Test fun layoutIsStableAfterRelayoutAndWindowOffset() {
        assertEquals(98, SpotifyDockLayout.reserveHeight(1000, 900, 880, 872, 6))
        assertEquals(98, SpotifyDockLayout.reserveHeight(1000, 900, 902, 894, 6))
        assertEquals(98, SpotifyDockLayout.reserveHeight(1200, 1100, 1102, 1094, 6))
    }

    @Test fun absentPlayerReservesOnlyTheTabRegion() {
        assertEquals(100, SpotifyDockLayout.reserveHeight(1000, 900, 880, null, 6))
    }
}
