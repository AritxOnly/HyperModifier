package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopStatusBarPolicyTest {
    @Test public void launcherOverrideStopsAtAppLockscreenAndShadeBoundaries() {
        for (int mode : new int[]{DesktopStatusBarPolicy.LIGHT, DesktopStatusBarPolicy.DARK}) {
            assertTrue(DesktopStatusBarPolicy.applies(mode, true, true, false, false, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, false, false, false, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, true, false, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, false, true, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, false, false, true));
            assertFalse(DesktopStatusBarPolicy.applies(mode, false, true, false, false, false));
        }
    }

    @Test public void systemAndInvalidImportedModesPreserveStock() {
        for (int mode : new int[]{0, -1, 3, Integer.MAX_VALUE}) {
            assertEquals(DesktopStatusBarPolicy.SYSTEM, DesktopStatusBarPolicy.normalize(mode));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, false, false, false));
        }
    }

    @Test public void recentsTemporarilyLightensIconsOnlyInLightMode() {
        assertTrue(DesktopStatusBarPolicy.shouldLightenRecentsIcons(true, true, false));
        assertFalse(DesktopStatusBarPolicy.shouldLightenRecentsIcons(true, false, false));
        assertFalse(DesktopStatusBarPolicy.shouldLightenRecentsIcons(true, true, true));
        assertFalse(DesktopStatusBarPolicy.shouldLightenRecentsIcons(false, true, false));
    }

    @Test public void recentsRepairRequiresKnownLightLauncherInLightMode() {
        assertTrue(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                true, true, false, true, 0));
        assertFalse(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                true, false, false, true, 0));
        assertFalse(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                true, true, true, true, 0));
        assertFalse(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                true, true, false, false, 0));
        assertFalse(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                false, true, false, true, 0));
        assertFalse(DesktopStatusBarPolicy.canRepairRecentsAppearance(
                true, true, false, true, DesktopStatusBarPolicy.APPEARANCE_LIGHT_STATUS_BARS));
    }
}
