package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopStatusBarPolicyTest {
    @Test public void launcherOverrideStopsAtAppLockscreenAndShadeBoundaries() {
        for (int mode : new int[]{DesktopStatusBarPolicy.LIGHT, DesktopStatusBarPolicy.DARK}) {
            assertTrue(DesktopStatusBarPolicy.applies(mode, true, true, false, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, false, false, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, true, false));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, false, true));
            assertFalse(DesktopStatusBarPolicy.applies(mode, false, true, false, false));
        }
    }

    @Test public void systemAndInvalidImportedModesPreserveStock() {
        for (int mode : new int[]{0, -1, 3, Integer.MAX_VALUE}) {
            assertEquals(DesktopStatusBarPolicy.SYSTEM, DesktopStatusBarPolicy.normalize(mode));
            assertFalse(DesktopStatusBarPolicy.applies(mode, true, true, false, false));
        }
    }
}
