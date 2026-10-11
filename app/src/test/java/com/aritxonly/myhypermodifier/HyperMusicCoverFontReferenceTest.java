package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverFontReferenceTest {
    @Test public void aodAndWakeOemChangesCannotReplaceHeldReference() {
        HyperMusicCoverExtensionPolicy.FontReference r = new HyperMusicCoverExtensionPolicy.FontReference();
        r.observe(28, 50, 700, 200, false);
        r.observe(28, 50, 700, 200, true);
        assertFalse(r.observe(10, 100, 100, 100, true));
        assertFalse(r.observe(40, 90, 400, 300, true));
        assertEquals(28, r.height, 0); assertEquals(50, r.width, 0);
        assertEquals(700, r.weight, 0); assertEquals(200, r.size, 0);
    }
    @Test public void releasingSceneAllowsNextClockConfigurationToBecomeReference() {
        HyperMusicCoverExtensionPolicy.FontReference r = new HyperMusicCoverExtensionPolicy.FontReference();
        r.observe(28, 50, 700, 200, true);
        r.observe(40, 70, 600, 250, false);
        r.observe(40, 70, 600, 250, true);
        r.observe(10, 100, 100, 100, true);
        assertEquals(40, r.height, 0); assertEquals(70, r.width, 0);
        assertEquals(600, r.weight, 0); assertEquals(250, r.size, 0);
    }
    @Test public void weightArrivesWithCoverProgressEvenWhenOemIsStillAnimating() {
        float start = 700, target = 100;
        assertEquals(700, HyperMusicCoverExtensionPolicy.axisHeight(start, target, 0), 0);
        assertEquals(400, HyperMusicCoverExtensionPolicy.axisHeight(start, target, .5f), 0);
        // OEM's pending intermediate weight does not participate in the rendered endpoint.
        assertEquals(100, HyperMusicCoverExtensionPolicy.axisHeight(start, target, 1), 0);
    }
}
