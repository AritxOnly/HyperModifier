package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyDockBackdropTest {
    @Test fun bothSurfacesDrawTheSameWindowAlignedSource() {
        val tab = ViewBackdropBounds(16, 950, 368, 54)
        val player = ViewBackdropBounds(0, 894, 400, 50)
        val region = SpotifyDockBackdrop.union(tab, player)
        assertEquals(ViewBackdropBounds(0, 894, 400, 110), region)
        val sourceWidth = region.width + 48
        val sourceHeight = region.height + 48
        for (target in listOf(tab, player)) {
            val (x, y) = SpotifyDockBackdrop.offset(region, target)
            assertEquals(region.left - 24, target.left + (target.width - sourceWidth) / 2 + x)
            assertEquals(region.top - 24, target.top + (target.height - sourceHeight) / 2 + y)
        }
    }

    @Test fun absentPlayerAndWindowMovementPreserveAlignment() {
        val tab = ViewBackdropBounds(16, 950, 368, 54)
        assertEquals(tab, SpotifyDockBackdrop.union(tab, null))
        assertEquals(0 to 0, SpotifyDockBackdrop.offset(tab, tab))
        val player = ViewBackdropBounds(0, 894, 400, 50)
        val shiftedTab = tab.copy(left = tab.left + 200, top = tab.top + 300)
        val shiftedPlayer = player.copy(left = player.left + 200, top = player.top + 300)
        assertEquals(SpotifyDockBackdrop.offset(SpotifyDockBackdrop.union(tab, player), tab),
            SpotifyDockBackdrop.offset(SpotifyDockBackdrop.union(shiftedTab, shiftedPlayer), shiftedTab))
    }
}
