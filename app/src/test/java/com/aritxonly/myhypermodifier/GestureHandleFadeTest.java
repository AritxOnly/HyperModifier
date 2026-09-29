package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureHandleFadeTest {
    @Test public void fadesOutAndInAndStopsRequestingFramesAtEndpoints() {
        GestureHandleFade fade = new GestureHandleFade(false);
        assertEquals(1f, fade.alpha(true, 1_000), 0f);
        assertTrue(fade.running(1_000));
        assertEquals(0.5f, fade.alpha(true, 1_110), 0.001f);
        assertEquals(0f, fade.alpha(true, 1_220), 0f);
        assertFalse(fade.running(1_220));
        assertEquals(0f, fade.alpha(false, 2_000), 0f);
        assertEquals(0.5f, fade.alpha(false, 2_110), 0.001f);
        assertEquals(1f, fade.alpha(false, 2_220), 0f);
        assertFalse(fade.running(2_220));
    }

    @Test public void rapidReversalKeepsCurrentOpacityAndRepeatedEventsDoNotRestart() {
        GestureHandleFade fade = new GestureHandleFade(false);
        fade.alpha(true, 1_000);
        float before = fade.alpha(true, 1_080);
        assertEquals(before, fade.alpha(false, 1_080), 0f);
        float rising = fade.alpha(false, 1_190);
        assertTrue(rising > before);
        assertEquals(rising, fade.alpha(true, 1_190), 0f);
        assertEquals(0f, fade.alpha(true, 1_410), 0f);
        assertFalse(fade.running(1_410));
    }

    @Test public void swipeRevealFadesInBeforeShortGestureEnds() {
        GestureHandleFade fade = new GestureHandleFade(true);
        assertEquals(0f, fade.alpha(false, 1_000, true), 0f);
        assertEquals(0.5f, fade.alpha(false, 1_045, true), 0.001f);
        assertEquals(1f, fade.alpha(false, 1_090, true), 0f);
        assertFalse(fade.running(1_090));
    }

    @Test public void initiallyHiddenHandleDoesNotFlashOrAnimateUntilShown() {
        GestureHandleFade fade = new GestureHandleFade(true);
        assertEquals(0f, fade.alpha(true, 5_000), 0f);
        assertFalse(fade.running(5_000));
        assertEquals(0f, fade.alpha(false, 6_000), 0f);
        assertTrue(fade.running(6_000));
        assertEquals(1f, fade.alpha(false, 6_220), 0f);
    }

    @Test public void immersiveDeadlineStartsFadeAfterThreeSeconds() {
        GestureHandlePolicy policy = new GestureHandlePolicy();
        policy.setRules(java.util.Map.of("app", "immersive"), 0);
        policy.foreground("app", "app/Main", 1, 0);
        GestureHandleFade fade = new GestureHandleFade(false);
        assertEquals(1f, fade.alpha(policy.hidden(2_999, false), 2_999), 0f);
        assertEquals(1f, fade.alpha(policy.hidden(3_000, false), 3_000), 0f);
        assertEquals(0.5f, fade.alpha(policy.hidden(3_110, false), 3_110), 0.001f);
        assertEquals(0f, fade.alpha(policy.hidden(3_220, false), 3_220), 0f);
        policy.foreground("app", "app/Detail", 1, 4_000);
        assertEquals(0f, fade.alpha(policy.hidden(4_000, false), 4_000), 0f);
        assertEquals(1f, fade.alpha(policy.hidden(4_220, false), 4_220), 0f);
    }
}
