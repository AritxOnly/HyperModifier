package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverExtensionPolicyTest {
    @Test public void expandedPolicyOnlyOwnsVisibleKeyguardIncludingAod() {
        assertTrue(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, "NUMBER"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, false, true, true, 1, false, "NUMBER"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, false, true, 1, false, "NUMBER"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 0, false, "NUMBER"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, true, "NUMBER"));
        assertTrue(HyperMusicCoverExtensionPolicy.active(true, true, true, true, null, false, "NUMBER"));
    }
    @Test public void heldVisibleAodSceneSinksImmediatelyAndListStillRestores() {
        Object[] source = new Object[9]; source[5] = true; source[6] = true;
        boolean fullscreenAod = HyperMusicCoverExtensionPolicy.active(true, true, true, true,
                1, false, "NUMBER");
        assertTrue(fullscreenAod);
        Object[] effective = HyperMusicCoverExtensionPolicy.notificationInputs(source, fullscreenAod, 0, false);
        assertEquals(false, effective[5]); assertEquals(false, effective[6]);
        boolean allCards = HyperMusicCoverExtensionPolicy.active(true, true, true, true,
                1, false, "LIST");
        assertFalse(allCards);
        Object[] restored = HyperMusicCoverExtensionPolicy.notificationInputs(source, allCards, 0, false);
        assertEquals(true, restored[5]); assertEquals(true, restored[6]);
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, false, 1, false, "NUMBER"));
    }
    @Test public void collapseRestoresEachManualModeWithoutMutatingCombineCache() {
        for (int count : new int[]{7, 8, 9}) {
            Object[] source = new Object[count]; source[5] = true; source[6] = true;
            if (count >= 8) source[7] = 2;
            Object[] active = HyperMusicCoverExtensionPolicy.notificationInputs(source, true, 2, false);
            assertEquals(false, active[5]); assertEquals(false, active[6]);
            assertEquals(true, source[5]); assertEquals(true, source[6]);
            Object[] restored = HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 2, false);
            assertEquals(true, restored[5]); assertEquals(true, restored[6]);
            Object[] manualSink = HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 1, false);
            assertEquals(false, manualSink[5]); assertEquals(false, manualSink[6]);
            Object[] system = HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 0, false);
            assertArrayEquals(source, system);
            if (count >= 8) assertEquals(2, active[7]);
        }
    }
    @Test public void moduleGlobalSinkRemainsAfterCollapseButHmcExplicitAvoidanceWins() {
        Object[] source = new Object[7]; source[5] = true; source[6] = true;
        assertEquals(false, HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 0, true)[6]);
        assertEquals(true, HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 2, true)[6]);
    }
    @Test public void absoluteAxisValueDoesNotDependOnOemStartingHeight() {
        assertEquals(50f, HyperMusicCoverExtensionPolicy.axisHeight(28f, 50f, 1f), 0f);
        assertEquals(50f, HyperMusicCoverExtensionPolicy.axisHeight(14f, 50f, 1f), 0f);
        assertEquals(28f, HyperMusicCoverExtensionPolicy.axisHeight(28f, 50f, 0f), 0f);
        assertEquals(39f, HyperMusicCoverExtensionPolicy.axisHeight(28f, 50f, .5f), 0f);
        assertEquals(14f, HyperMusicCoverExtensionPolicy.axisHeight(14f, 50f, Float.NaN), 0f);
    }
    @Test public void customAxisAcceptsPreviouslyBoundedValues() {
        assertEquals(2f, HyperMusicCoverExtensionPolicy.axisHeight(28f, 2f, 1f), 0f);
        assertEquals(60f, HyperMusicCoverExtensionPolicy.axisHeight(28f, 60f, 1f), 0f);
        assertEquals(5f, HyperMusicCoverExtensionPolicy.storedHeight(true, 5f, 1f), 0f);
    }
    @Test public void oldPercentageMigratesToSeparateAxisKeyWithNewValueTakingPriority() {
        assertEquals(42f, HyperMusicCoverExtensionPolicy.storedHeight(false, 0f, 1.5f), 0f);
        assertEquals(50f, HyperMusicCoverExtensionPolicy.storedHeight(true, 50f, 1.5f), 0f);
        assertEquals(28f, HyperMusicCoverExtensionPolicy.storedHeight(true, Float.NaN, 1.5f), 0f);
        assertEquals(28f, HyperMusicCoverExtensionPolicy.storedHeight(false, 0f, Float.NaN), 0f);
        assertEquals(1f, HyperMusicCoverExtensionPolicy.validHeight(1f), 0f);
        assertEquals(200f, HyperMusicCoverExtensionPolicy.validHeight(200f), 0f);
    }
    @Test public void listExpansionDoesNotSinkButNumberAndStackCanKeepFullscreenScene() {
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, "LIST"));
        assertTrue(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, "NUMBER"));
        assertTrue(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, "STACK"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, false, true, true, 1, false, "STACK"));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, null));
        assertFalse(HyperMusicCoverExtensionPolicy.active(true, true, true, true, 1, false, "UNKNOWN"));
        Object[] source = new Object[9]; source[5] = true; source[6] = true;
        assertEquals(false, HyperMusicCoverExtensionPolicy.notificationInputs(source, true, 0, false)[6]);
        assertEquals(true, HyperMusicCoverExtensionPolicy.notificationInputs(source, false, 0, false)[6]);
    }
    @Test public void negativeAndDecimalAxisSurvivesStorageAndAnimation() {
        assertEquals(-12.5f, HyperMusicCoverExtensionPolicy.storedHeight(true, -12.5f, 1f), 0f);
        assertEquals(-12.5f, HyperMusicCoverExtensionPolicy.axisHeight(28f, -12.5f, 1f), 0f);
        assertEquals(7.75f, HyperMusicCoverExtensionPolicy.axisHeight(28f, -12.5f, .5f), 0f);
        assertEquals(28f, HyperMusicCoverExtensionPolicy.validHeight(Float.POSITIVE_INFINITY), 0f);
        assertTrue(Float.isFinite(HyperMusicCoverExtensionPolicy.axisHeight(Float.MAX_VALUE, -Float.MAX_VALUE, .5f)));
    }
    @Test public void entryDoesNotFollowOemSqueezeThenGrowBackOnGlyphTail() {
        HyperMusicCoverExtensionPolicy.FontTransition transition = new HyperMusicCoverExtensionPolicy.FontTransition();
        transition.observe(28f, false);
        float last = 28f;
        float[] oem = {28f, 18f, 8f, 4f, 2f, 2f};
        for (int i = 0; i < oem.length; i++) {
            transition.observe(oem[i], true);
            float current = transition.height(10f, i / 5f);
            assertTrue(current <= last);
            assertTrue(current >= 10f);
            assertEquals(28f, transition.reference(), 0f);
            last = current;
        }
        assertEquals(10f, last, 0f);
        transition.observe(1f, true);
        assertEquals(10f, transition.height(10f, 1f), 0f);
        assertEquals(19f, transition.height(10f, .5f), 0f); // interrupted/reversed animation
        transition.observe(40f, false);
        transition.beforeOemHeightChange(40f, true);
        transition.observe(2f, true);
        assertEquals(25f, transition.height(10f, .5f), 0f);
    }
    @Test public void firstEntryCapturesAxisBeforeOemSetterAndDefaultHasNoExtraMorph() {
        HyperMusicCoverExtensionPolicy.FontTransition transition = new HyperMusicCoverExtensionPolicy.FontTransition();
        transition.beforeOemHeightChange(28f, true);
        transition.observe(2f, true);
        assertEquals(28f, transition.reference(), 0f);
        assertEquals(28f, transition.height(28f, .5f), 0f);
        assertEquals(28f, transition.height(28f, 1f), 0f);
    }
    @Test public void baselineUnitsPreserveNativeInkChangeUnderUniformCoverScale() {
        float baseline = 300f, measuredNative = 420f, coverUnit = 60f;
        float returned = HyperMusicCoverExtensionPolicy.baselineUnit(measuredNative, measuredNative / baseline);
        float scale = coverUnit / returned;
        assertEquals(.2f, scale, .0001f);
        assertEquals(84f, measuredNative * scale, .0001f);
        assertEquals(60f, baseline * scale, .0001f);
        assertEquals(measuredNative, HyperMusicCoverExtensionPolicy.baselineUnit(measuredNative, Float.NaN), 0f);
    }
}
