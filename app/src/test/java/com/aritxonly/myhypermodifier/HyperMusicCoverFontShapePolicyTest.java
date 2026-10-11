package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperMusicCoverFontShapePolicyTest {
    private static final HyperMusicCoverFontShapePolicy.Probe FONT = (width, height) -> (100f + width) / (20f + height);
    @Test public void heightAxisFitsRatioWhenAvailable() throws Exception {
        HyperMusicCoverFontShapePolicy.Axes a = HyperMusicCoverFontShapePolicy.solve(2f, 50f, FONT);
        assertEquals(50f, a.width, 0f);
        assertEquals(2f, a.ratio, .002f);
    }
    @Test public void widthAxisExtendsFlatteningAfterHeightHitsFloor() throws Exception {
        float floorAtNarrowWidth = FONT.ratio(10f, 10f);
        HyperMusicCoverFontShapePolicy.Axes a = HyperMusicCoverFontShapePolicy.solve(6f, 10f, FONT);
        assertEquals(10f, a.height, 0f);
        assertTrue(a.width > 10f);
        assertTrue(a.ratio > floorAtNarrowWidth);
        assertEquals(6f, a.ratio, .002f);
        // A wider native glyph is drawn at a smaller point size to retain its original width.
        float pointSize = HyperMusicCoverFontShapePolicy.fontSizeForWidth(100f, 110f, 100f + a.width);
        assertEquals(110f, (100f + a.width) * pointSize / 100f, .001f);
        assertTrue(30f * pointSize / 100f < 30f);
    }
    @Test public void unreachableRatioStopsAtNativeAxesRatherThanStretching() throws Exception {
        HyperMusicCoverFontShapePolicy.Axes a = HyperMusicCoverFontShapePolicy.solve(100f, 10f, FONT);
        assertEquals(100f, a.width, 0f); assertEquals(10f, a.height, 0f);
        assertEquals(FONT.ratio(100f, 10f), a.ratio, 0f);
    }
    @Test public void unsupportedAxesLeaveAspectAndPointSizeUnchanged() throws Exception {
        HyperMusicCoverFontShapePolicy.Axes a = HyperMusicCoverFontShapePolicy.solve(6f, 50f, (w, h) -> 2f);
        assertEquals(2f, a.ratio, 0f);
        assertEquals(100f, HyperMusicCoverFontShapePolicy.fontSizeForWidth(100f, 200f, 200f), 0f);
    }
    @Test public void invalidSettingsAndEmptyGlyphCannotProduceInvalidSize() throws Exception {
        assertEquals(2.4f, HyperMusicCoverFontShapePolicy.validRatio(-1f), 0f);
        assertEquals(2.4f, HyperMusicCoverFontShapePolicy.validRatio(Float.NaN), 0f);
        assertEquals(2.4f, HyperMusicCoverFontShapePolicy.validRatio(Float.POSITIVE_INFINITY), 0f);
        assertEquals(100f, HyperMusicCoverFontShapePolicy.fontSizeForWidth(100f, 200f, 0f), 0f);
        assertThrows(IllegalArgumentException.class, () -> HyperMusicCoverFontShapePolicy.solve(2f, 50f, (w, h) -> Float.NaN));
    }
    @Test public void interpolationStartsFromSameAxesAndSizeWithoutShapeJump() throws Exception {
        HyperMusicCoverFontShapePolicy.Axes a = HyperMusicCoverFontShapePolicy.solve(6f, 10f, FONT);
        assertEquals(10f, HyperMusicCoverExtensionPolicy.axisHeight(10f, a.width, 0f), 0f);
        assertEquals(28f, HyperMusicCoverExtensionPolicy.axisHeight(28f, a.height, 0f), 0f);
        assertEquals(a.width, HyperMusicCoverExtensionPolicy.axisHeight(10f, a.width, 1f), 0f);
    }
}
