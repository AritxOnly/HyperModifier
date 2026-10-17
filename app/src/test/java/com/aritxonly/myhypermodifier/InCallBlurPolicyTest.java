package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class InCallBlurPolicyTest {
    @Test public void backgroundPreservesStockProportionsAndZeroClears() {
        assertEquals(90, InCallBlurPolicy.backgroundRadius(180, 50));
        assertEquals(220, InCallBlurPolicy.backgroundRadius(110, 200));
        assertEquals(0, InCallBlurPolicy.backgroundRadius(0, 200));
        assertEquals(0, InCallBlurPolicy.backgroundRadius(180, 0));
        assertEquals(500, InCallBlurPolicy.backgroundRadius(400, 200));
    }

    @Test public void softGlassMaskUsesGlassSliderEvenThoughItUsesBackgroundSampler() {
        // Mask_Glass_Light supplies 110px through MiBackgroundBlur, not MiGlassBlurRadius.
        assertEquals(20, InCallBlurPolicy.containerRadius(110, 100, true, 20));
        assertEquals(70, InCallBlurPolicy.containerRadius(110, 50, true, 70));
        assertEquals(0, InCallBlurPolicy.containerRadius(110, 100, true, 0));
        assertEquals(100, InCallBlurPolicy.containerRadius(110, 100, true, 900));
    }

    @Test public void disablingGlassOverrideRestoresLatestStockWithBackgroundScale() {
        assertEquals(110, InCallBlurPolicy.containerRadius(110, 100, true, -1));
        assertEquals(55, InCallBlurPolicy.containerRadius(110, 50, true, -1));
        assertEquals(140, InCallBlurPolicy.containerRadius(140, 100, true, -1));
        // Classic container and button backdrop must not inherit the Glass slider.
        assertEquals(110, InCallBlurPolicy.containerRadius(110, 100, false, 20));
        assertEquals(90, InCallBlurPolicy.containerRadius(180, 50, false, 20));
    }

    @Test public void softGlassStockClearRemainsClearWhileOverrideIsEnabled() {
        assertEquals(0, InCallBlurPolicy.containerRadius(0, 200, true, 70));
        assertEquals(70, InCallBlurPolicy.containerRadius(110, 200, true, 70));
    }

    @Test public void bitmapRadiusHonorsIntrinsicLimit() {
        assertEquals(7.5f, InCallBlurPolicy.bitmapRadius(15f, 50), 0f);
        assertEquals(15f, InCallBlurPolicy.bitmapRadius(15f, 100), 0f);
        assertEquals(25f, InCallBlurPolicy.bitmapRadius(15f, 200), 0f);
        assertTrue(InCallBlurPolicy.bitmapRadius(15f, 0) > 0f);
    }

    @Test public void bitmapConfigDistinguishesStockFromTransparentMask() {
        int stock = InCallBlurPolicy.bitmapConfig(100, false, 20f);
        assertEquals(100, stock);
        assertEquals(-1, InCallBlurPolicy.dimAlpha(stock));
        int transparent = InCallBlurPolicy.bitmapConfig(100, true, 0f);
        assertNotEquals(stock, transparent);
        assertEquals(0, InCallBlurPolicy.dimAlpha(transparent));
        assertEquals(100, InCallBlurPolicy.blurPercent(transparent));
        assertEquals(255, InCallBlurPolicy.dimAlpha(InCallBlurPolicy.bitmapConfig(200, true, 100f)));
    }

    @Test public void disabledDimIgnoresUnrelatedSliderChangesAndPreservesBlur() {
        assertEquals(InCallBlurPolicy.bitmapConfig(50, false, 0f),
                InCallBlurPolicy.bitmapConfig(50, false, 100f));
        int config = InCallBlurPolicy.bitmapConfig(200, true, 20f);
        assertEquals(200, InCallBlurPolicy.blurPercent(config));
        assertEquals(51, InCallBlurPolicy.dimAlpha(config));
    }

    @Test public void stockClearStaysInactiveAcrossSettingsRefreshThenReactivates() {
        GlobalGlassBlurState state = new GlobalGlassBlurState(120, 120);
        state.active = InCallBlurPolicy.glassActive(120, 120);
        assertEquals(30, state.smallRadius(true, 30));
        state.recordStock(0, 0);
        state.active = InCallBlurPolicy.glassActive(0, 0);
        assertEquals(0, state.smallRadius(true, 70));
        assertEquals(0, state.bigRadius(true, 70));
        state.recordStock(50, 500);
        state.active = InCallBlurPolicy.glassActive(50, 500);
        assertEquals(70, state.bigRadius(true, 70));
        assertEquals(50, state.smallRadius(false, 70));
        assertEquals(500, state.bigRadius(false, 70));
        assertTrue(InCallBlurPolicy.glassActive(0, 500));
    }
}
