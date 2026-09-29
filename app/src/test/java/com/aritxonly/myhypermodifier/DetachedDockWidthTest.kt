package com.aritxonly.myhypermodifier

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DetachedDockWidthTest {
    @Test fun aSingleRemainingTabKeepsTheStandardEightyDpItemCap() {
        assertEquals(158.dp, detachedDockMaxWidth(1))
        assertEquals(238.dp, detachedDockMaxWidth(2))
        assertEquals(318.dp, detachedDockMaxWidth(3))
    }

    @Test fun manyTabsStillRespectTheOverallDockWidth() {
        assertEquals(398.dp, detachedDockMaxWidth(4))
        assertEquals(442.dp, detachedDockMaxWidth(5))
    }
}
