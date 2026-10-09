package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class HyperMusicCoverClockPolicyTest {
    static class CompatibleMain {
        static Float sHoldY;
        static boolean sSelfDriving;
        static boolean sRoomEasing;
        static float sRoomShown, sRoomRaw, sRoomSoft;
        static float roomForRows(float y) { return y; }
        static float roomForRowsNow(float y) { return y; }
        static void reassertClockRoom() {}
    }

    @Test public void bypassAppliesOnlyToNormalSystemDrivenClock() throws Exception {
        HyperMusicCoverClockPolicy policy = new HyperMusicCoverClockPolicy(CompatibleMain.class);
        CompatibleMain.sHoldY = null;
        CompatibleMain.sSelfDriving = false;
        assertFalse(policy.shouldBypass(false));
        assertTrue(policy.shouldBypass(true));
        CompatibleMain.sHoldY = 700f;
        assertFalse(policy.shouldBypass(true));
        CompatibleMain.sHoldY = null;
        CompatibleMain.sSelfDriving = true;
        assertFalse(policy.shouldBypass(true));
        CompatibleMain.sSelfDriving = false;
    }

    @Test public void enablingStopsPendingEaseWithoutChangingCoverOwnership() throws Exception {
        HyperMusicCoverClockPolicy policy = new HyperMusicCoverClockPolicy(CompatibleMain.class);
        CompatibleMain.sHoldY = 800f;
        CompatibleMain.sRoomEasing = true;
        CompatibleMain.sRoomShown = 1200f;
        CompatibleMain.sRoomRaw = 1500f;
        CompatibleMain.sRoomSoft = 60f;
        policy.clearRoomEase();
        assertFalse(CompatibleMain.sRoomEasing);
        assertTrue(Float.isNaN(CompatibleMain.sRoomShown));
        assertTrue(Float.isNaN(CompatibleMain.sRoomRaw));
        assertEquals(0f, CompatibleMain.sRoomSoft, 0f);
        assertEquals(Float.valueOf(800f), CompatibleMain.sHoldY);
        CompatibleMain.sHoldY = null;
    }

    @Test public void incompatibleModuleIsRejectedBeforeInstallingHooks() {
        assertThrows(NoSuchMethodException.class,
                () -> new HyperMusicCoverClockPolicy(String.class));
    }
}
