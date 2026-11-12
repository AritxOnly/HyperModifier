package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class LockscreenBottomPolicyTest {
    @Test public void screenAndShortcutAnchorsUseStackLayoutCoordinates() {
        assertEquals(900, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, 1000, 200, 0, true), 0);
        assertEquals(700, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, 1000, 200, 1, true), 0);
        // A larger disc raises its upper edge, leaving the same physical gap above it.
        assertEquals(680, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, 980, 200, 1, true), 0);
    }
    @Test public void missingDiscsPreserveNativeBoundsAndScreenModeDoesNotRequireDiscs() {
        assertEquals(850, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, Float.NaN, 200, 1, true), 0);
        assertEquals(850, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, 1000, 200, 1, false), 0);
        assertEquals(900, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, Float.NaN, 200, 0, false), 0);
    }
    @Test public void malformedGeometryFailsClosedAndCannotEscapeContainer() {
        assertEquals(850, LockscreenBottomPolicy.bottom(850, 0, Float.NaN, 1100, 1000, 200, 0, true), 0);
        assertEquals(850, LockscreenBottomPolicy.bottom(850, 1200, 100, 0, 1000, 200, 0, true), 0);
        assertEquals(0, LockscreenBottomPolicy.bottom(850, 1200, 100, 1100, 1000, 9000, 0, true), 0);
        assertEquals(900, LockscreenBottomPolicy.bottom(850, 1200, 100, 900, 1000, 0, 0, true), 0);
    }
    @Test public void settingValuesHaveSafeDefaultsAndRanges() {
        assertEquals(54, LockscreenBottomPolicy.size(Float.NaN), 0);
        assertEquals(32, LockscreenBottomPolicy.size(-1), 0);
        assertEquals(96, LockscreenBottomPolicy.size(999), 0);
        assertEquals(24, LockscreenBottomPolicy.gap(Float.POSITIVE_INFINITY), 0);
        assertEquals(480, LockscreenBottomPolicy.gap(999), 0);
        assertEquals(0, LockscreenBottomPolicy.anchor(99));
    }
}
