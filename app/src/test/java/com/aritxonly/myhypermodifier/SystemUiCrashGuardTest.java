package com.aritxonly.myhypermodifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SystemUiCrashGuardTest {
    @Test public void countsOnlyConsecutiveUnstableStartsWithinWindow() {
        assertEquals(1, SystemUiCrashGuard.nextAttemptCount(100_000L, -1L, 0));
        assertEquals(2, SystemUiCrashGuard.nextAttemptCount(110_000L, 100_000L, 1));
        assertEquals(3, SystemUiCrashGuard.nextAttemptCount(120_000L, 100_000L, 2));
        assertEquals(1, SystemUiCrashGuard.nextAttemptCount(120_001L, 100_000L, 2));
        assertEquals(1, SystemUiCrashGuard.nextAttemptCount(1_000L, 100_000L, 2));
    }

    @Test public void automaticModeStaysLatchedUntilExplicitRetry() {
        assertFalse(SystemUiCrashGuard.shouldUseCompatibility(false, false, 2));
        assertTrue(SystemUiCrashGuard.shouldUseCompatibility(false, false, 3));
        assertTrue(SystemUiCrashGuard.shouldUseCompatibility(true, false, 1));
        assertFalse(SystemUiCrashGuard.shouldUseCompatibility(true, true, 1));
    }

    @Test public void pendingStartCountDoesNotCrossADeviceReboot() {
        assertTrue(SystemUiCrashGuard.isSameBoot(1_000_000L, 1_004_000L));
        assertFalse(SystemUiCrashGuard.isSameBoot(1_000_000L, 1_040_000L));
        assertFalse(SystemUiCrashGuard.isSameBoot(0L, 1_000_000L));
    }
}
