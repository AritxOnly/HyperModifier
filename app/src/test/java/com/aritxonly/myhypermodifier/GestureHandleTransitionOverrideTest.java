package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureHandleTransitionOverrideTest {
    @Test public void gestureModeOverridesDisabledOverlay() {
        assertTrue(GestureHandleTransitionOverride.resolve(false, 2, true));
    }
    @Test public void disablingRestoresOverlayEvenAfterAnEnabledDecision() {
        assertTrue(GestureHandleTransitionOverride.resolve(false, 2, true));
        assertFalse(GestureHandleTransitionOverride.resolve(false, 2, false));
        assertTrue(GestureHandleTransitionOverride.resolve(true, 2, false));
    }
    @Test public void buttonModesKeepTheirNativeOverlay() {
        for (int mode : new int[] {0, 1}) {
            assertFalse(GestureHandleTransitionOverride.resolve(false, mode, true));
            assertTrue(GestureHandleTransitionOverride.resolve(true, mode, true));
        }
    }
}
