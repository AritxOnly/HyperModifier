package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class HookDiagnosticPolicyTest {
    @Test fun expiresAtSevenDaysAndKeepsNewRecords() {
        val now = HookDiagnosticPolicy.RETENTION_MS + 10_000L
        assertTrue(HookDiagnosticPolicy.expired(10_000L, now))
        assertFalse(HookDiagnosticPolicy.expired(10_001L, now))
        assertFalse(HookDiagnosticPolicy.expired(now, now))
        assertTrue(HookDiagnosticPolicy.expired(0L, now))
    }

    @Test fun recoverySupersedesOldFailureWithoutHidingOtherPackages() {
        fun event(pkg: String, feature: String, warning: Boolean) =
            HookDiagnostic(1L, pkg, feature, "", warning)
        val installed = event("systemui", "glass", false)
        val otherPackage = event("plugin", "glass", true)
        assertEquals(listOf(installed, otherPackage), HookDiagnosticPolicy.latestFeatures(listOf(
            event("systemui", "模块注入", false), installed, otherPackage,
            event("systemui", "glass", true),
        )))
        assertEquals(listOf(otherPackage), HookDiagnosticPolicy.latestFeatures(listOf(
            otherPackage, event("plugin", "glass", false),
        )))
    }
    @Test fun errorsDeduplicateByPackageFeatureAndDetailAndTrackLatestRecovery() {
        val newest = HookDiagnostic(9, "systemui", "glass", "missing field", true)
        val old = newest.copy(time = 1)
        val other = newest.copy(packageName = "plugin", time = 2)
        val distinct = newest.copy(detail = "missing method", time = 3)
        val recovered = HookDiagnostic(10, "plugin", "glass", "installed", false)
        val (active, history) = HookDiagnosticPolicy.errorGroups(listOf(old, recovered, distinct, other, newest))
        assertEquals(listOf(newest, distinct), active)
        assertEquals(listOf(other), history)
        val failedAgain = other.copy(time = 11)
        val regrouped = HookDiagnosticPolicy.errorGroups(listOf(other, recovered, failedAgain))
        assertEquals(listOf(failedAgain), regrouped.first)
        assertTrue(regrouped.second.isEmpty())
    }
}
