package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class LockscreenClockAvoidancePolicyTest {
    private LockscreenClockAvoidancePolicy.Result at(float notificationTop) {
        return LockscreenClockAvoidancePolicy.calculate(200, 2000, .25f, .338f,
                703, 1048, 342, 824, 296, 0, 2, true, notificationTop, 3.25f);
    }

    @Test public void naturallyLargeGapDoesNotMoveOrEnlargeClock() {
        var result = at(1800);
        assertEquals(0, result.translation(), 0);
        assertEquals(703, result.height());
        assertEquals(1048, result.width());
        assertEquals(296, result.weight());
    }

    @Test public void centeredClockMovesOnlyByActualIntrusion() {
        // Natural bottom 1524px; 32dp=104px. A short card at 1592 needs only 36px.
        var result = at(1592);
        assertEquals(-36, result.translation(), 0);
        assertEquals(703, result.height());
        assertEquals(104, 1592 - (1524 + result.translation()), 0);
    }

    @Test public void crossingGapBoundaryIsContinuous() {
        assertEquals(0, at(1629).translation(), 0);
        assertEquals(0, at(1628).translation(), 0);
        assertEquals(-1, at(1627).translation(), 0);
    }

    @Test public void usesTranslationBeforeShrinkingAndReturnsSmoothly() {
        assertEquals(703, at(1128).height());
        assertEquals(-500, at(1128).translation(), 0);
        assertEquals(702, at(1127).height());
        assertEquals(-500, at(1127).translation(), 0);
        assertEquals(703, at(1592).height());
        assertEquals(-36, at(1592).translation(), 0);
    }

    @Test public void fontRespectsMinimumAndMagazineTracksHeightReduction() {
        var result = at(700);
        assertEquals(342, result.height());
        assertEquals(684, result.width());
        assertEquals(-567, result.translation(), 0);
        assertEquals(result.translation() - 361, result.magazineTranslation(), 0);
        assertEquals(104, 700 - (1524 + result.translation() - 361), 0);
    }

    @Test public void progressivelyApproachingRowsNeverCauseAnEarlyJump() {
        var previous = at(1700);
        for (int top = 1699; top >= 650; top--) {
            var current = at(top);
            assertTrue(current.translation() <= previous.translation());
            assertTrue(current.translation() >= previous.translation() - 1.01f);
            assertTrue(current.height() <= previous.height());
            assertTrue(current.height() >= previous.height() - 1);
            previous = current;
        }
    }

    @Test public void noVisibleRowsKeepsNaturalLayoutDuringFirstLayout() {
        assertEquals(0, at(Float.POSITIVE_INFINITY).translation(), 0);
        assertEquals(703, at(Float.POSITIVE_INFINITY).height());
        assertNull(at(Float.NaN));
    }

    @Test public void preservesNativeMagazineOnlyDisplacement() {
        var result = LockscreenClockAvoidancePolicy.calculate(200, 2000, .25f, .25f,
                703, 1048, 342, 824, 296, 100, 2, false, 1900, 3.25f);
        assertEquals(-50, result.translation(), 0);
        assertEquals(703, result.height());
    }
}
