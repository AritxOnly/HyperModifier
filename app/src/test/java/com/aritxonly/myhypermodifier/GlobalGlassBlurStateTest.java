package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class GlobalGlassBlurStateTest {
    private static void assertRadii(int small, int big, GlobalGlassBlurState state, boolean enabled, int radius) {
        assertEquals(small, state.smallRadius(enabled, radius));
        assertEquals(big, state.bigRadius(enabled, radius));
    }

    @Test public void sharedRadiusOverridesBothNativeRadiiAndRestoresThemAfterEdits() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(60, 180);
        assertRadii(20, 20, state, true, 20);
        state.recordApplied(20, 20);
        assertFalse(state.needsRefresh(true, 20));
        assertTrue(state.needsRefresh(true, 37));
        state.recordApplied(37, 37);
        assertRadii(60, 180, state, false, 37);
        assertTrue(state.needsRefresh(false, 37));
        state.recordApplied(60, 180);
        assertFalse(state.needsRefresh(false, 37));
    }

    @Test public void zeroDisablesBlurAndNativeZeroCanBeOverridden() {
        assertRadii(0, 0, new GlobalGlassBlurState(60, 180), true, 0);
        assertRadii(20, 20, new GlobalGlassBlurState(0, 0), true, 20);
    }

    @Test public void customRadiusIsBoundedButStockValuesArePreserved() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(200, 600);
        assertRadii(100, 100, state, true, 900);
        assertRadii(0, 0, state, true, -10);
        assertRadii(200, 600, state, false, 900);
    }

    @Test public void reusedStateRestoresLatestSystemValuesAfterAnimationUpdates() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(60, 180);
        state.recordApplied(20, 20);
        state.recordStock(30, 90);
        state.recordApplied(20, 20);
        assertFalse(state.needsRefresh(true, 20));
        assertRadii(30, 90, state, false, 20);
        assertTrue(state.needsRefresh(false, 20));
    }

    @Test public void disabledUpdatesRemainAvailableForLiveEnabling() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(60, 180);
        state.recordStock(40, 120);
        state.recordApplied(40, 120);
        assertFalse(state.needsRefresh(false, 99));
        assertTrue(state.needsRefresh(true, 20));
        assertRadii(20, 20, state, true, 20);
        assertRadii(40, 120, state, false, 20);
    }

    @Test public void stockMatchingConfiguredRadiusDoesNotNeedAReplay() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(20, 20);
        assertFalse(state.needsRefresh(true, 20));
        assertFalse(state.needsRefresh(false, 20));
    }
    @Test public void supplementalMaterialRestoresNativeDefaultsWithoutRecordingInjectedValuesAsStock() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(0, 0);
        assertTrue(state.needsRefresh(true, 37));
        state.recordApplied(37, 37);
        assertFalse(state.needsRefresh(true, 37));
        assertTrue(state.needsRefresh(false, 37));
        assertRadii(0, 0, state, false, 37);
        // If the system later supplies a radius, that observed value replaces the fallback.
        state.recordStock(50, 500);
        state.recordApplied(37, 37);
        assertRadii(50, 500, state, false, 37);
    }
    @Test public void sharedIslandOwnerRemainsStockWhileCollapsedAndRestoresAfterClosing() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(50, 500, false);
        assertRadii(50, 500, state, true, 48);
        assertFalse(state.needsRefresh(true, 48));
        state.active = true;
        assertRadii(48, 48, state, true, 48);
        state.recordApplied(48, 48);
        state.active = false;
        assertTrue(state.needsRefresh(true, 48));
        assertRadii(50, 500, state, true, 100);
        state.recordStock(60, 450);
        assertRadii(60, 450, state, true, 0);
        state.recordApplied(60, 450);
        assertFalse(state.needsRefresh(true, 100));
    }
}
