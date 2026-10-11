package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverClockLayoutPolicyTest {
    @Test public void widerHoursMoveColonAndMinutesTogetherAndKeepRowCentered() {
        float[] nativeLayout = HyperMusicCoverClockLayoutPolicy.positions(0, 1000, 200, 40, 200, false);
        float[] shaped = HyperMusicCoverClockLayoutPolicy.positions(0, 1000, 300, 30, 300, false);
        assertTrue(nativeLayout[2] < shaped[0] + 300); // old colon intrudes into shaped hour
        assertEquals(shaped[0] + 300, shaped[2], 0);
        assertEquals(shaped[2], shaped[1], 0); // minute string carries the colon prefix
        assertEquals(500, (shaped[0] + shaped[1] + 30 + 300) / 2f, 0);
    }
    @Test public void hiddenColonDoesNotReserveAnExtraGap() {
        float[] p = HyperMusicCoverClockLayoutPolicy.positions(100, 600, 200, 0, 180, false);
        assertEquals(210, p[0], 0); assertEquals(410, p[1], 0);
    }
    @Test public void TwoRowsCenterEachPairIndependently() {
        float[] p = HyperMusicCoverClockLayoutPolicy.positions(100, 600, 200, 0, 180, true);
        assertEquals(300, p[0], 0); assertEquals(310, p[1], 0);
    }
}
