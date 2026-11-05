package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverClockFramePolicyTest {
    @Test public void geometryProbeAndAllThreeDrawsUseOnePreparedFrame() {
        HyperMusicCoverClockFramePolicy.Gate g = new HyperMusicCoverClockFramePolicy.Gate();
        assertTrue(g.begin(100)); // HMC measures before draw
        assertFalse(g.begin(100)); // hour draw
        assertFalse(g.begin(100)); // HMC after-hour measurement
        assertFalse(g.begin(100)); // minute draw
        assertFalse(g.begin(100)); // colon draw
        assertTrue(g.begin(108)); // next frame
    }
    @Test public void aodWakeFrameUsesNewGeometryOnce() {
        HyperMusicCoverClockFramePolicy.Gate g = new HyperMusicCoverClockFramePolicy.Gate();
        assertTrue(g.begin(100)); assertTrue(g.begin(1100));
        assertFalse(g.begin(1100));
    }
    @Test public void failedPreparationCanRetryWithinSameFrame() {
        HyperMusicCoverClockFramePolicy.Gate g = new HyperMusicCoverClockFramePolicy.Gate();
        assertTrue(g.begin(100)); g.abort(); assertTrue(g.begin(100));
    }
}
