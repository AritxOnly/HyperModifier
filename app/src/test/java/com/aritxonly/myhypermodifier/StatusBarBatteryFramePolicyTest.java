package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatusBarBatteryFramePolicyTest {
    @Test public void zeroIsEmptyAndFullCoversTheWholeBodyForBothDirections() {
        assertEquals(-46f, StatusBarBatteryFramePolicy.maskShift(0, true), .0001f);
        assertEquals(46f, StatusBarBatteryFramePolicy.maskShift(0, false), .0001f);
        assertEquals(0f, StatusBarBatteryFramePolicy.maskShift(100, true), .0001f);
        assertEquals(0f, StatusBarBatteryFramePolicy.maskShift(100, false), .0001f);
        assertEquals(-11.5f, StatusBarBatteryFramePolicy.maskShift(50, true), .0001f);
        assertEquals(-46f, StatusBarBatteryFramePolicy.maskShift(-10, true), .0001f);
        assertEquals(0f, StatusBarBatteryFramePolicy.maskShift(150, true), .0001f);
        for (int level = 1; level <= 100; level++) {
            assertTrue(StatusBarBatteryFramePolicy.maskShift(level, true)
                    >= StatusBarBatteryFramePolicy.maskShift(level - 1, true));
        }
    }

    @Test public void quickChargeWinsAndPerformanceCoexistsWithBothChargingMarks() {
        assertEquals(0, StatusBarBatteryFramePolicy.shapeIndex(false, false, false));
        assertEquals(1, StatusBarBatteryFramePolicy.shapeIndex(true, false, false));
        assertEquals(2, StatusBarBatteryFramePolicy.shapeIndex(true, true, false));
        assertEquals(2, StatusBarBatteryFramePolicy.shapeIndex(false, true, false));
        assertEquals(3, StatusBarBatteryFramePolicy.shapeIndex(false, false, true));
        assertEquals(4, StatusBarBatteryFramePolicy.shapeIndex(true, false, true));
        assertEquals(5, StatusBarBatteryFramePolicy.shapeIndex(true, true, true));
        assertEquals(5, StatusBarBatteryFramePolicy.shapeIndex(false, true, true));
    }
}
