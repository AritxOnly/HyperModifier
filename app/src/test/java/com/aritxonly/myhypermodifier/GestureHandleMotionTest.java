package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class GestureHandleMotionTest {
    @Test public void followsAllDirectionsWithinViewBounds() {
        GestureHandleMotion motion = new GestureHandleMotion();
        motion.start(100f, 100f, 1_000);
        assertFalse(motion.movedBeyond(105f, 104f, 8f));
        assertTrue(motion.movedBeyond(110f, 100f, 8f));
        motion.move(200f, 200f, 16f, 16f, 10f, 5f, 1_016);
        assertTrue(motion.running(1_016));
        assertTrue(motion.x(1_080) > 0f && motion.x(1_080) < 16f);
        assertTrue(motion.y(1_080) > 0f && motion.y(1_080) <= 5f);
        motion.move(0f, 0f, 16f, 16f, 10f, 5f, 1_096);
        assertTrue(motion.x(1_400) < 0f);
        assertTrue(motion.y(1_400) < 0f);
    }

    @Test public void releasesSmoothlyAndCanRestartOrReset() {
        GestureHandleMotion motion = new GestureHandleMotion();
        motion.start(0f, 0f, 1_000);
        motion.move(20f, 0f, 16f, 16f, 10f, 5f, 1_016);
        float beforeRelease = motion.x(1_064);
        assertTrue(beforeRelease > 0f);
        motion.release(1_064);
        assertTrue(motion.x(1_150) > beforeRelease);
        assertTrue(motion.x(1_450) > -1f);
        assertEquals(0f, motion.x(2_200), 0.15f);
        assertFalse(motion.running(2_200));
        motion.start(0f, 0f, 2_300);
        motion.move(0f, -20f, 16f, 16f, 10f, 5f, 2_316);
        assertTrue(motion.y(2_450) < 0f);
        motion.reset();
        assertEquals(0f, motion.y(2_450), 0.001f);
    }

    @Test public void fastSwipeKeepsReturnAnimationWhenNavigationViewIsRecreated() {
        GestureHandleMotion motion = new GestureHandleMotion();
        motion.start(100f, 100f, 1_000);
        motion.move(100f, 20f, 16f, 16f, 12f, 8f, 1_016);
        motion.release(1_020);
        motion.rebaseForNewHost(1_400);
        assertTrue(motion.running(1_400));
        assertTrue(motion.y(1_500) < 0f);
        assertEquals(0f, motion.y(2_400), 0.15f);
    }
}
