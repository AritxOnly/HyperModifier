package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureHandleTouchAreaTest {
    @Test public void defaultBandIsSixteenDpFromScreenBottom() {
        assertTrue(GestureHandleTouchArea.contains(500f, 1_968f, 1_000, 2_000,
                2_000f, 2f, GestureHandleTouchArea.DEFAULT_DP));
        assertFalse(GestureHandleTouchArea.contains(500f, 1_967f, 1_000, 2_000,
                2_000f, 2f, GestureHandleTouchArea.DEFAULT_DP));
    }

    @Test public void customDistanceChangesOnlyTheBottomBand() {
        assertTrue(GestureHandleTouchArea.contains(500f, 1_936f, 1_000, 2_000,
                2_000f, 2f, 32f));
        assertFalse(GestureHandleTouchArea.contains(500f, 1_936f, 1_000, 2_000,
                2_000f, 2f, 16f));
        assertFalse(GestureHandleTouchArea.contains(500f, 2_000f, 1_000, 2_000,
                2_000f, 2f, 0f));
        assertFalse(GestureHandleTouchArea.contains(500f, 1_968f, 1_000, 2_000,
                1_800f, 2f, 16f));
    }

    @Test public void invalidDistancesAreBounded() {
        assertEquals(16f, GestureHandleTouchArea.normalize(Float.NaN), 0f);
        assertEquals(32f, GestureHandleTouchArea.normalize(500f), 0f);
        assertEquals(0f, GestureHandleTouchArea.normalize(-1f), 0f);
    }
}
