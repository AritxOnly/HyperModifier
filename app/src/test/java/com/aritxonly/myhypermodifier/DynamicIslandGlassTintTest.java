package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicIslandGlassTintTest {
    @Test public void opacityEditsPreserveStockRecipeAndAllOtherShaderChannels() {
        float[] stock = new float[37];
        for (int i = 0; i < stock.length; i++) stock[i] = i / 10f;
        stock[14] = .6f;
        float[] snapshot = stock.clone();
        for (int percent : new int[]{0, 30, 60, 100, -1, 101}) {
            float[] result = DynamicIslandGlassTint.withOpacity(stock, percent);
            assertNotSame(stock, result);
            assertEquals(Math.max(0, Math.min(100, percent)) / 100f, result[14], 0f);
            result[14] = stock[14];
            assertArrayEquals(snapshot, result, 0f);
            assertArrayEquals(snapshot, stock, 0f);
        }
    }
    @Test public void animationRewriteKeepsCustomAlphaAndRestoresLatestSystemRecipeWithoutAllocatingEachFrame() {
        DynamicIslandGlassTint.State state = new DynamicIslandGlassTint.State();
        float[] original = new float[42];
        original[14] = .6f;
        original[18] = 1f;
        state.record(original);
        float[] buffer = state.recipe(17);
        float[] stockBuffer = state.stock;
        assertEquals(.17f, buffer[14], 0f);
        // A pull-to-window animation updates color and overall alpha independently.
        original[11] = .2f;
        original[14] = .8f;
        original[18] = .4f;
        state.record(original);
        assertSame(stockBuffer, state.stock);
        assertSame(buffer, state.recipe(17));
        assertEquals(.17f, buffer[14], 0f);
        assertEquals(.4f, buffer[18], 0f);
        assertEquals(.2f, buffer[11], 0f);
        assertArrayEquals(original, state.recipe(-1), 0f);
        assertEquals(.8f, original[14], 0f);
        assertEquals(0f, state.recipe(0)[14], 0f);
        assertEquals(1f, state.recipe(100)[14], 0f);
    }

    @Test public void bothSurfacesKeepIndependentOpacityAndCanRestoreIndependently() {
        DynamicIslandGlassTint.State expanded = new DynamicIslandGlassTint.State(false);
        DynamicIslandGlassTint.State notification = new DynamicIslandGlassTint.State(true);
        float[] expandedStock = new float[42]; expandedStock[14] = .6f;
        float[] notificationStock = new float[42]; notificationStock[14] = .1f;
        expanded.record(expandedStock); notification.record(notificationStock);
        int expandedConfig = DynamicIslandGlassTint.effectiveOpacity(expanded.notification, true, 17, true, 83);
        int notificationConfig = DynamicIslandGlassTint.effectiveOpacity(notification.notification, true, 17, true, 83);
        assertEquals(.17f, expanded.recipe(expandedConfig)[14], 0f);
        assertEquals(.83f, notification.recipe(notificationConfig)[14], 0f);
        // Disable expanded customization while changing notification's setting.
        expandedConfig = DynamicIslandGlassTint.effectiveOpacity(false, false, 17, true, 0);
        notificationConfig = DynamicIslandGlassTint.effectiveOpacity(true, false, 17, true, 0);
        assertArrayEquals(expandedStock, expanded.recipe(expandedConfig), 0f);
        assertEquals(0f, notification.recipe(notificationConfig)[14], 0f);
        // Turning notification off restores its own stock, independently of expanded.
        notificationConfig = DynamicIslandGlassTint.effectiveOpacity(true, true, 100, false, 0);
        assertArrayEquals(notificationStock, notification.recipe(notificationConfig), 0f);
        assertEquals(100, DynamicIslandGlassTint.effectiveOpacity(false, true, 100, false, 0));
    }

    @Test public void repeatedNativeWritesClaimGenericCardOwnershipOnlyOnTransitions() {
        DynamicIslandGlassTint.State state = new DynamicIslandGlassTint.State(true);
        float[] recipe = new float[42]; recipe[14] = .6f;
        assertTrue(state.record(recipe, 30));
        float[] stock = state.stock;
        float[] tuned = state.recipe(30);
        for (int i = 0; i < 120; i++) {
            recipe[18] = i / 120f;
            assertFalse(state.record(recipe, 30));
            assertSame(stock, state.stock);
            assertSame(tuned, state.recipe(30));
            assertEquals(recipe[18], tuned[18], 0f);
        }
        assertTrue(state.record(recipe, 83));
        assertFalse(state.record(recipe, 83));
        assertFalse(state.record(recipe, -1));
        assertArrayEquals(recipe, state.recipe(-1), 0f);
        assertTrue(state.record(recipe, 83));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsupportedRecipeIsRejectedInsteadOfChangingAnUnknownChannel() {
        DynamicIslandGlassTint.withOpacity(new float[14], 30);
    }
}
