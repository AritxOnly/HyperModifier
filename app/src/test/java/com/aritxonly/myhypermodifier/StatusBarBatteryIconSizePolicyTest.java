package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatusBarBatteryIconSizePolicyTest {
    @Test public void clampsAndNormalizesTheSupportedScale() {
        assertEquals(.5f, StatusBarBatteryIconSizePolicy.normalize(.1f), .0001f);
        assertEquals(1f, StatusBarBatteryIconSizePolicy.normalize(1f), .0001f);
        assertEquals(2f, StatusBarBatteryIconSizePolicy.normalize(3f), .0001f);
        assertEquals(1f, StatusBarBatteryIconSizePolicy.normalize(Float.NaN), .0001f);
        assertEquals(1f, StatusBarBatteryIconSizePolicy.normalize(
                Float.POSITIVE_INFINITY), .0001f);
    }

    @Test public void composesAgainstTheStockScaleOnce() {
        assertEquals(1.5f, StatusBarBatteryIconSizePolicy.scaledValue(1f, 1.5f), .0001f);
        assertEquals(.6f, StatusBarBatteryIconSizePolicy.scaledValue(.8f, .75f), .0001f);
        assertEquals(.4f, StatusBarBatteryIconSizePolicy.scaledValue(.8f, .1f), .0001f);
    }
}
