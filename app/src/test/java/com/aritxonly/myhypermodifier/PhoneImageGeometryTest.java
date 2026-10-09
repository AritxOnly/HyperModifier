package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneImageGeometryTest {
    @Test public void portraitPhoneFitsCompletelyAndPreservesAspectRatio() {
        float[] rect = PhoneImageGeometry.destination(64, 64, 160, 335, 0, 0, 1);
        assertTrue(rect[0] > 0); assertTrue(rect[1] > 0);
        assertTrue(rect[2] < 64); assertTrue(rect[3] < 64);
        assertEquals(160f / 335f, (rect[2] - rect[0]) / (rect[3] - rect[1]), .00001f);
        assertEquals(32f, (rect[0] + rect[2]) / 2f, .00001f);
    }
    @Test public void previewAndRuntimeUseTheSameDpCoordinateSpace() {
        float[] runtime = PhoneImageGeometry.destination(192, 192, 160, 335, -7, 4, 1.2f);
        float[] preview = PhoneImageGeometry.destination(576, 576, 160, 335, -7, 4, 1.2f);
        for (int i = 0; i < 4; i++) assertEquals(runtime[i] * 3, preview[i], .0001f);
        float[] base = PhoneImageGeometry.destination(64, 64, 100, 100, 0, 0, 1);
        float[] moved = PhoneImageGeometry.destination(64, 64, 100, 100, -7, 4, 2);
        assertEquals((base[2] - base[0]) * 2, moved[2] - moved[0], .0001f);
        assertEquals(25, (moved[0] + moved[2]) / 2, .0001f);
        assertEquals(36, (moved[1] + moved[3]) / 2, .0001f);
    }
    @Test public void malformedProfilesAndUnsafeNumbersFallBackSafely() {
        assertArrayEquals(new float[] {0, 0, 1}, PhoneImageProfile.read("invalid", "auto"), 0f);
        assertArrayEquals(new float[] {96, -96, .1f}, PhoneImageProfile.read(
                "{\"custom\":{\"x\":200,\"y\":-200,\"scale\":0}}", "custom"), 0f);
        assertEquals(0, PhoneImageGeometry.offset(Float.NaN), 0f);
        assertEquals(1, PhoneImageGeometry.scale(Float.POSITIVE_INFINITY), 0f);
    }
}
