package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class PowerAnalysisPolicyTest {
    private val first = PowerSample(1_000_000, 100_000, 0, 4_000_000, false)
    private val next = PowerSample(1_900_000, 1_000_000, 0, 3_990_000, false)

    @Test fun endpointIsCollectedEvenWhenJobIsDelayed() {
        assertFalse(PowerAnalysisPolicy.shouldFinish(100, 100, false, 200, 199, false))
        assertTrue(PowerAnalysisPolicy.shouldFinish(100, 100, false, 200, 200, false))
        assertTrue(PowerAnalysisPolicy.shouldFinish(100, 100, false, 200, 300, false))
        assertTrue(PowerAnalysisPolicy.shouldFinish(100, 100, false, 200, 150, true))
        assertFalse(PowerAnalysisPolicy.shouldFinish(100, 100, true, 200, 300, false))
        assertFalse(PowerAnalysisPolicy.shouldFinish(100, 99, false, 200, 300, false))
    }

    @Test fun convertsMicroampHoursToMilliampHours() {
        assertEquals(10.0, PowerAnalysisPolicy.dischargedMah(first, next)!!, 0.001)
    }

    @Test fun excludesChargingMissingCountersAndRecalibration() {
        assertNull(PowerAnalysisPolicy.dischargedMah(first.copy(plugged = 1), next))
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(plugged = 1)))
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(chargeUah = null)))
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(chargeUah = 4_010_000)))
    }

    @Test fun excludesRebootsLongGapsAndWallClockChanges() {
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(uptime = 50)))
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(time = 5_000_000, uptime = 4_100_000)))
        assertNull(PowerAnalysisPolicy.dischargedMah(first, next.copy(time = 2_900_000)))
    }
}
