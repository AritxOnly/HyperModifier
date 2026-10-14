package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class BackgroundDimPolicyTest {
    @Test public void transparentAndBlackEndpoints() {
        assertEquals(0, BackgroundDimPolicy.blackColor(0f, 1f));
        assertEquals(0xff000000, BackgroundDimPolicy.blackColor(100f, 1f));
        assertEquals(0x33000000, BackgroundDimPolicy.blackColor(20f, 1f));
    }

    @Test public void tintFollowsAnimationWithoutChangingBlur() {
        assertEquals(0, BackgroundDimPolicy.blackColor(100f, 0f));
        assertEquals(0x7f000000, BackgroundDimPolicy.blackColor(100f, .5f));
        assertEquals(0x19000000, BackgroundDimPolicy.blackColor(20f, .5f));
        assertEquals(0xff000000, BackgroundDimPolicy.blackColor(100f, 2f));
        assertEquals(0, BackgroundDimPolicy.blackColor(20f, -1f));
    }

    @Test public void corruptPreferencesAreClamped() {
        assertEquals(0f, BackgroundDimPolicy.normalizePercent(-10f), 0f);
        assertEquals(100f, BackgroundDimPolicy.normalizePercent(150f), 0f);
        assertEquals(20f, BackgroundDimPolicy.normalizePercent(Float.NaN), 0f);
        assertEquals(20f, BackgroundDimPolicy.normalizePercent(Float.POSITIVE_INFINITY), 0f);
        assertEquals(0, BackgroundDimPolicy.blackColor(20f, Float.NaN));
    }

    @Test public void replacesRatherThanAppendsNativeBlendPairs() {
        assertArrayEquals(new int[]{0x33000000, 3}, BackgroundDimPolicy.blendColors(20f));
    }

    @Test public void bionicsLightnessCompressionIsRemovedAtEveryDimLevel() {
        int[] stock = {0xff999999, 21, 0xff0d8000, 106};
        assertArrayEquals(new int[]{0x33000000, 3},
                BackgroundDimPolicy.blendColors(stock, 20f));
        assertArrayEquals(new int[]{0xff999999, 21, 0xff0d8000, 106}, stock);
        assertArrayEquals(new int[]{0, 3},
                BackgroundDimPolicy.blendColors(stock, 0f));
        assertArrayEquals(new int[]{0xff000000, 3},
                BackgroundDimPolicy.blendColors(stock, 100f));
    }

    @Test public void actualSaturationModesSurviveWithoutStockLightnessMapping() {
        int[] stock = {0xff0d8000, 106, 0xff808080, 26, 0xff800000, 201};
        assertArrayEquals(new int[]{0xff808080, 26, 0xff800000, 201, 0, 3},
                BackgroundDimPolicy.blendColors(stock, 0f));
        assertArrayEquals(new int[]{0xff808080, 26, 0xff800000, 201, 0x33000000, 3},
                BackgroundDimPolicy.blendColors(stock, 20f));
    }

    @Test public void classicRecipeReplacesOnlyTintWithoutAddingSaturation() {
        assertArrayEquals(new int[]{0x33000000, 3},
                BackgroundDimPolicy.blendColors(new int[]{0x52808080, 28}, 20f));
        assertArrayEquals(new int[]{0x33000000, 3},
                BackgroundDimPolicy.blendColors(null, 20f));
    }

    @Test public void windowDimIsTransparentAtZeroIncludingAnimatedFrames() {
        assertEquals(0, BackgroundDimPolicy.windowColor(0x66000000, 102, 0f));
        assertEquals(0, BackgroundDimPolicy.windowColor(0x33000000, 102, 0f));
        assertEquals(0x33000000, BackgroundDimPolicy.windowColor(0x66000000, 102, 20f));
        assertEquals(0x19000000, BackgroundDimPolicy.windowColor(0x33000000, 102, 20f));
        assertEquals(0, BackgroundDimPolicy.windowColor(0, 102, 100f));
    }
}
