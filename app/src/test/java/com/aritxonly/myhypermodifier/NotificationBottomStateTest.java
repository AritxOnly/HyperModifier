package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationBottomStateTest {
    @Test public void lateAdoptionUsesNativeOutputWithoutInventingRawInput() {
        NotificationBottomState state = new NotificationBottomState();
        state.adopt(900);
        assertTrue(Float.isNaN(state.input));
        assertEquals(900, state.baseline(900), 0);
        state.applied(900, 700);
        assertTrue(state.overridden);
        assertEquals(900, state.baseline(700), 0);
        // Turning the feature off restores the output actually present when it was adopted.
        state.applied(900, 900);
        assertFalse(state.overridden);
    }
    @Test public void futureNativeCallbackReplacesBothInputAndBaseline() {
        NotificationBottomState state = new NotificationBottomState();
        state.adopt(900);
        state.applied(900, 700);
        state.nativeResult(1100, 1050);
        assertEquals(1100, state.input, 0);
        assertEquals(1050, state.baseline(1050), 0);
        assertFalse(state.overridden);
    }
    @Test public void laterModuleWriteIsNotOverwrittenByAnOldRestorationSnapshot() {
        NotificationBottomState state = new NotificationBottomState();
        state.adopt(900);
        state.applied(900, 700);
        assertEquals(820, state.baseline(820), 0);
        state.applied(820, 820);
        assertFalse(state.overridden);
    }
    @Test public void failureWhileCalculatingNewBoundaryKeepsOldOverrideRestorable() {
        NotificationBottomState state = new NotificationBottomState();
        state.adopt(900);
        state.applied(900, 700);
        state.baseline(700); // A scene read could fail after this, before committing any new value.
        assertTrue(state.overridden);
        assertEquals(900, state.baseline(700), 0);
        state.applied(900, 900);
        assertFalse(state.overridden);
    }
}
