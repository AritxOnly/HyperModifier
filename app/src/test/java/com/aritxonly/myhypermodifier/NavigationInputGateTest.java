package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class NavigationInputGateTest {
    @Test public void firstTapWaitsForColdStartSettlement() {
        NavigationInputGate gate = new NavigationInputGate();
        gate.markVisible(1_000L);
        assertFalse(gate.begin(1_001L, true, true));
        assertFalse(gate.samplingReady(1_349L));
        assertTrue(gate.samplingReady(1_350L));
        assertTrue(gate.begin(1_350L, true, true));
    }

    @Test public void repeatedTapsAreSerializedAndRateLimited() {
        NavigationInputGate gate = new NavigationInputGate();
        gate.markVisible(0L);
        assertTrue(gate.begin(350L, true, true));
        assertFalse(gate.begin(351L, true, true));
        gate.finish();
        assertFalse(gate.begin(500L, true, true));
        assertTrue(gate.begin(700L, true, true));
    }

    @Test public void invisibleOrNotReadyHostCannotRoute() {
        NavigationInputGate gate = new NavigationInputGate();
        gate.markVisible(0L);
        assertFalse(gate.begin(1_000L, false, true));
        assertFalse(gate.begin(1_000L, true, false));
        assertFalse(gate.isRouting());
    }

    @Test public void repeatedVisibilityDoesNotExtendSettlement() {
        NavigationInputGate gate = new NavigationInputGate();
        gate.markVisible(0L);
        gate.markVisible(500L);
        assertTrue(gate.begin(700L, true, true));
    }
}
