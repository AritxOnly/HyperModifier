package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyShortcutActionsTest {
    @Test public void unsupportedOrMissingValuesPreserveStockBehavior() {
        assertEquals(KeyShortcutActions.STOCK, KeyShortcutActions.normalize(null));
        assertEquals(KeyShortcutActions.STOCK, KeyShortcutActions.normalize(""));
        assertEquals(KeyShortcutActions.STOCK, KeyShortcutActions.normalize("unknown"));
    }
    @Test public void disabledShortcutIsDistinctFromStock() {
        assertEquals(KeyShortcutActions.NONE, KeyShortcutActions.normalize("none"));
        assertNotEquals(KeyShortcutActions.STOCK, KeyShortcutActions.normalize("none"));
        for (String action : new String[]{"wallet", "memory", "screen"}) {
            assertEquals(action, KeyShortcutActions.normalize(action));
        }
    }
    @Test public void onlyVolumeUpPowerPairIsOverriddenRegardlessOfKeyOrder() {
        assertTrue(KeyShortcutActions.isVolumePowerPair(24, 26));
        assertTrue(KeyShortcutActions.isVolumePowerPair(26, 24));
        assertFalse(KeyShortcutActions.isVolumePowerPair(25, 26)); // Screenshot remains native.
        assertFalse(KeyShortcutActions.isVolumePowerPair(24, 25)); // Accessibility remains native.
        assertFalse(KeyShortcutActions.isVolumePowerPair(26, 26));
    }
}
