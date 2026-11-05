package com.aritxonly.myhypermodifier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LargeScreenDevicePolicyTest {
    @org.junit.Test public void lhasaIsFoldableRegardlessOfModelName() {
        org.junit.Assert.assertEquals(LargeScreenDevicePolicy.FOLDABLE,
                LargeScreenDevicePolicy.detectMode("lhasa", "unknown"));
    }

    @Test public void testedTabletCodeSelectsLargeScreenBehavior() {
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("piano", "25053RP5CC"));
    }

    @Test public void knownFoldableCodeSelectsLargeScreenBehavior() {
        assertEquals(LargeScreenDevicePolicy.FOLDABLE,
                LargeScreenDevicePolicy.detectMode("zizhan", "22061218C"));
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("zizhan", "22061218C"));
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("ruyi", "2405CPX3DC"));
    }

    @Test public void marketNamesCoverNewTabletsAndFoldables() {
        assertEquals(LargeScreenDevicePolicy.TABLET,
                LargeScreenDevicePolicy.detectMode("futurepad", "Xiaomi Pad 9 Pro"));
        assertEquals(LargeScreenDevicePolicy.FOLDABLE,
                LargeScreenDevicePolicy.detectMode("futurefold", "Xiaomi MIX Fold 5"));
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("futurepad", "Xiaomi Pad 9 Pro"));
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("futurefold", "Xiaomi MIX Fold 5"));
        assertTrue(LargeScreenDevicePolicy.isLargeScreenDevice("futureflip", "Xiaomi MIX Flip 2"));
    }

    @Test public void manualOverrideWinsOnlyWhenAutoIsNotSelected() {
        assertEquals(LargeScreenDevicePolicy.TABLET,
                LargeScreenDevicePolicy.resolveMode(
                        LargeScreenDevicePolicy.AUTO, LargeScreenDevicePolicy.TABLET));
        assertEquals(LargeScreenDevicePolicy.PHONE,
                LargeScreenDevicePolicy.resolveMode(
                        LargeScreenDevicePolicy.PHONE, LargeScreenDevicePolicy.TABLET));
        assertEquals(LargeScreenDevicePolicy.TABLET,
                LargeScreenDevicePolicy.resolveMode(
                        LargeScreenDevicePolicy.TABLET, LargeScreenDevicePolicy.PHONE));
        assertEquals(LargeScreenDevicePolicy.AUTO,
                LargeScreenDevicePolicy.normalizeOverride(99));
    }

    @Test public void ordinaryPhonesKeepTheMobileBehavior() {
        assertFalse(LargeScreenDevicePolicy.isLargeScreenDevice("pudding", "Xiaomi 17"));
        assertFalse(LargeScreenDevicePolicy.isLargeScreenDevice("dali", "Xiaomi 18 Pro"));
    }
}
