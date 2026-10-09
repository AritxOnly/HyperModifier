package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class PowerStatsSummaryTest {
    private fun summary(screen: String, battery: String = "1h 0m 0s 0ms", start: String = "2026-10-08-10-00-00") =
        PowerStatsSummary.parse("""
            Start clock time: $start
            Time on battery: $battery (100.0%) realtime, 20m 0s 0ms (33.3%) uptime
            Screen on: $screen (10.0%) 3x
        """.trimIndent())

    @Test fun extractsSummaryDurationsWithoutUnitConfusion() {
        assertEquals(86_400_000L + 3_600_000L + 120_000L + 3_000L + 4L,
            PowerStatsSummary.duration("1d 1h 2m 3s 4ms"))
        assertEquals(0L, PowerStatsSummary.duration("0ms"))
        assertNull(PowerStatsSummary.duration("not available"))
    }

    @Test fun computesScreenOnIncrementWithinSameStatisticsPeriod() {
        assertEquals(12_000L, summary("6m 0s 0ms").screenOnDelta(summary("6m 12s 0ms", "1h 15m 0s 0ms")))
    }

    @Test fun rejectsResetAndMissingIdentity() {
        assertNull(summary("6m 0s 0ms").screenOnDelta(summary("1m 0s 0ms", start = "2026-10-08-11-00-00")))
        assertNull(summary("6m 0s 0ms").screenOnDelta(summary("7m 0s 0ms", battery = "5m 0s 0ms")))
        assertNull(PowerStatsSummary.parse("Screen on: 6m 0s 0ms").screenOnDelta(summary("7m 0s 0ms")))
    }

    @Test fun sleepClockAccountsForDeepSleepAndRejectsReboot() {
        assertEquals(178_093L, PowerAnalysisPolicy.sleepMs(506_820, 328_727))
        assertNull(PowerAnalysisPolicy.sleepMs(10_000, -1))
        assertNull(PowerAnalysisPolicy.sleepMs(10_000, 11_000))
    }
}
