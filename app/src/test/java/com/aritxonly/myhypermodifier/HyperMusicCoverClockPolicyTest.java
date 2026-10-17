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
    static class RenamedRoom {
        static boolean e = true;
        static float shown = 123, raw = 456, soft = 10;
        static float x(float y) { return y; }
        static float y(float y) { return y; }
        static void z() { }
    }
    @Test public void splitRoomPolicyClearsMovedEasingWithoutChangingMainOwnership() throws Exception {
        HyperMusicCoverDexSymbols s = new HyperMusicCoverDexSymbols();
        String owner = "L" + RenamedRoom.class.getName().replace('.', '/') + ";";
        for (String[] role : new String[][]{{"roomForRows", "x", "(F)F"}, {"roomForRowsNow", "y", "(F)F"},
                {"reassertClockRoom", "z", "()V"}, {"roomEasing", "e", "Z"}, {"roomShown", "shown", "F"},
                {"roomRaw", "raw", "F"}, {"roomSoft", "soft", "F"}})
            s.members.put(role[0], new HyperMusicCoverDexSymbols.Member(owner, role[1], role[2]));
        HyperMusicCoverClockPolicy p = new HyperMusicCoverClockPolicy(CompatibleMain.class, s);
        CompatibleMain.sHoldY = 800f;
        assertEquals(RenamedRoom.class, p.roomForRows.getDeclaringClass());
        p.clearRoomEase();
        assertFalse(RenamedRoom.e); assertTrue(Float.isNaN(RenamedRoom.shown));
        assertTrue(Float.isNaN(RenamedRoom.raw)); assertEquals(0f, RenamedRoom.soft, 0f);
        assertEquals(Float.valueOf(800), CompatibleMain.sHoldY);
        CompatibleMain.sHoldY = null;
    }
}
