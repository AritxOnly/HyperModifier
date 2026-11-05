package com.aritxonly.myhypermodifier

import org.junit.Assert.assertEquals
import org.junit.Test

class BackdropCaptureCadenceTest {
    @Test fun samplingTracksTheDisplayInsteadOfAFixedSixtyFourMillisecondDelay() {
        assertEquals(16L, BackdropCaptureCadence.intervalMs(60f))
        assertEquals(8L, BackdropCaptureCadence.intervalMs(120f))
    }

    @Test fun unsupportedRatesHaveSafeDefaultsAndCaps() {
        assertEquals(16L, BackdropCaptureCadence.intervalMs(Float.NaN))
        assertEquals(16L, BackdropCaptureCadence.intervalMs(0f))
        assertEquals(8L, BackdropCaptureCadence.intervalMs(144f))
    }

    @Test fun pixelCopyDoesNotScaleItsReadbackCostWithHighRefreshDisplays() {
        assertEquals(32L, BackdropCaptureCadence.pixelCopyIntervalMs(60f, 0L))
        assertEquals(32L, BackdropCaptureCadence.pixelCopyIntervalMs(120f, 0L))
        assertEquals(40L, BackdropCaptureCadence.pixelCopyIntervalMs(120f, 40L))
        assertEquals(41L, BackdropCaptureCadence.pixelCopyIntervalMs(24f, 0L))
    }
}
