package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class NavigationRefreshPolicyTest {
    @Test fun highRefreshDisplaysDoNotIncreaseNativeTreeWork() {
        val policy = NavigationRefreshPolicy(100)
        val refreshes = (0L until 1_000L step 8L).filter { policy.shouldRefresh(it, false) }
        assertEquals(10, refreshes.size)
        assertTrue(refreshes.zipWithNext().all { (before, after) -> after - before >= 100 })
    }

    @Test fun clickAndVisibilityChangesCanRefreshImmediately() {
        val policy = NavigationRefreshPolicy(100)
        assertTrue(policy.shouldRefresh(0, false))
        assertFalse(policy.shouldRefresh(16, false))
        assertTrue(policy.shouldRefresh(20, true))
        assertFalse(policy.shouldRefresh(100, false))
        assertTrue(policy.shouldRefresh(120, false))
    }

    @Test fun resettingClockDoesNotStallNavigation() {
        val policy = NavigationRefreshPolicy(100)
        assertTrue(policy.shouldRefresh(1_000, false))
        assertTrue(policy.shouldRefresh(0, false))
        assertFalse(policy.shouldRefresh(99, false))
        assertTrue(policy.shouldRefresh(100, false))
    }

    @Test fun otherAdaptersRetainUnthrottledDefaults() {
        val policy = NavigationRefreshPolicy(0)
        assertTrue(policy.shouldRefresh(0, false))
        assertTrue(policy.shouldRefresh(0, false))
        assertTrue(policy.shouldRefresh(8, false))
    }
}
