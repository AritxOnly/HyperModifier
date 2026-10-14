package com.aritxonly.myhypermodifier;
import org.junit.Test;
import static org.junit.Assert.*;
public class ShortcutServiceSessionTest {
    @Test public void onlyLatestPrivateStartCanBeStopped() {
        ShortcutServiceSession state = new ShortcutServiceSession();
        state.privateStart(1);
        assertTrue(state.canStop(1));
        state.privateStart(2);
        assertFalse(state.canStop(1));
        assertTrue(state.canStop(2));
    }
    @Test public void nativeStartRevokesCleanupOwnership() {
        ShortcutServiceSession state = new ShortcutServiceSession();
        state.privateStart(1);
        state.nativeUse();
        assertFalse(state.canStop(1));
        state.privateStart(2);
        assertFalse(state.canStop(2));
    }
}
