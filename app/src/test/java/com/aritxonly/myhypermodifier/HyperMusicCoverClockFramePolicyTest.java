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
    @Test public void visibleUnlockRetainsClockUntilLastFadeFrame() {
        assertTrue(HyperMusicCoverClockFramePolicy.retainExit(true, true, true, 1));
        assertTrue(HyperMusicCoverClockFramePolicy.retainExit(true, true, true, .001f));
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(true, true, true, 0));
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(true, true, false, 1));
    }
    @Test public void unlockWithoutCapturedFontDoesNotTakeOverNativeClock() {
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(false, true, true, 1));
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(true, false, true, 1));
    }
    @Test public void invalidAlphaCannotKeepAnExitPoseAlive() {
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(true, true, true, Float.NaN));
        assertFalse(HyperMusicCoverClockFramePolicy.retainExit(true, true, true, Float.POSITIVE_INFINITY));
    }
    @Test public void nativeScaleResetIsDeferredUntilUnlockIsInvisible() {
        HyperMusicCoverClockFramePolicy.ExitPose p = new HyperMusicCoverClockFramePolicy.ExitPose(
                new float[]{.5f, -20}, new float[]{.5f, -20});
        assertEquals(.5f, p.request(0, 1f, true), 0);
        assertEquals(-20, p.request(1, 0f, true), 0);
        assertArrayEquals(new float[]{1, 0}, p.nativeValues, 0);
        assertEquals(1, p.request(0, 1f, false), 0);
    }
    @Test public void captureAfterEarlyNativeResetKeepsVisiblePoseAndNativeCleanupSeparate() {
        HyperMusicCoverClockFramePolicy.ExitPose p = new HyperMusicCoverClockFramePolicy.ExitPose(
                new float[]{.5f, -20}, new float[]{1, 0});
        assertArrayEquals(new float[]{.5f, -20}, p.held, 0);
        assertArrayEquals(new float[]{1, 0}, p.nativeValues, 0);
    }
    @Test public void exitSnapshotIsIndependentOfMutableSourcePose() {
        float[] held = {.5f}, nativePose = {1};
        HyperMusicCoverClockFramePolicy.ExitPose p = new HyperMusicCoverClockFramePolicy.ExitPose(held, nativePose);
        held[0] = 2; nativePose[0] = 3;
        assertEquals(.5f, p.held[0], 0); assertEquals(1, p.nativeValues[0], 0);
    }
}
