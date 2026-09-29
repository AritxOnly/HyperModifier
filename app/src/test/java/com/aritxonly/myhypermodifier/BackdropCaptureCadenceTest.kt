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
}
