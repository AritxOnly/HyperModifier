package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class StatusBarMarginPolicyTest {
    @Test public void sidesAreIndependentAndRepeatedUpdatesDoNotAccumulate() {
        var policy = new StatusBarMarginPolicy(4, 6);
        assertArrayEquals(new int[]{7, 0}, policy.resolve(4, 6, 1f, -2f, 3f));
        assertArrayEquals(new int[]{7, 0}, policy.resolve(7, 0, 1f, -2f, 3f));
        assertArrayEquals(new int[]{4, 6}, policy.resolve(7, 0, 0f, 0f, 3f));
    }

    @Test public void nativePaddingAndDensityChangesArePreserved() {
        var policy = new StatusBarMarginPolicy(4, 6);
        policy.resolve(4, 6, 1f, 1f, 3f);
        assertArrayEquals(new int[]{13, 23}, policy.resolve(10, 20, 1f, 1f, 3f));
        assertArrayEquals(new int[]{12, 22}, policy.resolve(13, 23, 1f, 1f, 2f));
        assertArrayEquals(new int[]{10, 20}, policy.resolve(12, 22, 0f, 0f, 2f));
    }

    @Test public void invalidAndOutOfRangeValuesAreSafe() {
        var policy = new StatusBarMarginPolicy(0, 0);
        assertArrayEquals(new int[]{6, -6}, policy.resolve(0, 0, 20f, -20f, 3f));
        assertArrayEquals(new int[]{0, 0}, policy.resolve(6, -6, Float.NaN, Float.POSITIVE_INFINITY, 3f));
    }
}
