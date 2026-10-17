package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverBindingGateTest {
    @Test public void missingStartupSourceWaitsForAttachmentInsteadOfFailingOrPolling() {
        HyperMusicCoverBindingGate gate = new HyperMusicCoverBindingGate();
        assertTrue(gate.begin()); assertFalse(gate.defer());
        for (int frame = 0; frame < 120; frame++) assertFalse(gate.begin());
        gate.sourceReady(); assertTrue(gate.begin());
    }
    @Test public void attachmentDuringWorkerLookupIsNotLost() {
        HyperMusicCoverBindingGate gate = new HyperMusicCoverBindingGate();
        assertTrue(gate.begin()); gate.sourceReady(); assertFalse(gate.begin());
        assertTrue(gate.defer()); assertTrue(gate.begin());
    }
    @Test public void successfulBindingDoesNotRepeatParsingOnLaterAttachments() {
        HyperMusicCoverBindingGate gate = new HyperMusicCoverBindingGate();
        assertTrue(gate.begin()); gate.complete();
        gate.sourceReady(); assertFalse(gate.begin());
    }
    @Test public void realCompatibilityFailureDoesNotRetryOnEveryAttachment() {
        HyperMusicCoverBindingGate gate = new HyperMusicCoverBindingGate();
        assertTrue(gate.begin()); gate.fail();
        gate.sourceReady(); assertFalse(gate.begin());
    }
}
