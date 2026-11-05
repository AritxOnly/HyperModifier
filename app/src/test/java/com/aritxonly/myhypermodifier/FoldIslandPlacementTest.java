package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoldIslandPlacementTest {
    @Test public void physicalPanelSurvivesRotationAndDensityOverrides() {
        assertTrue(FoldIslandPlacement.isInnerPanel(2364, 1672));
        assertTrue(FoldIslandPlacement.isInnerPanel(1672, 2364));
        assertFalse(FoldIslandPlacement.isInnerPanel(1168, 1712));
    }

    @Test public void foldedCentersPrimaryForBothModes() {
        for (int mode : new int[]{1, 2}) {
            assertEquals(452, FoldIslandPlacement.left(mode, false, 1168, 264, 0, false, 190, -1, 8, 23));
        }
    }

    @Test public void inlineStartsAtStockClockBoundaryAndMirrorsOnRtl() {
        assertEquals(224, FoldIslandPlacement.left(1, true, 2364, 264, 84, false, 224, 2294, 8, 0));
        assertEquals(1876, FoldIslandPlacement.left(1, true, 2364, 264, 84, true, 224, 2294, 8, 0));
        assertEquals(224, FoldIslandPlacement.left(2, true, 2364, 264, 0, false, 224, -1, 8, 0));
    }

    @Test public void cameraNearRightEdgeKeepsBothCapsulesInsideWindow() {
        int single = FoldIslandPlacement.left(2, true, 2364, 264, 0, false, 224, 2294, 8, 0);
        assertTrue(single <= 2294 && 2294 < single + 264);
        assertEquals(2364 - 8, single + 264);
        int paired = FoldIslandPlacement.left(2, true, 2364, 264, 84, false, 224, 2294, 8, 0);
        assertEquals(2364 - 8, paired + 264 + 84);
    }

    @Test public void contentAndClipShareOriginDuringPairedTranslation() {
        assertEquals(200, FoldIslandPlacement.contentOrigin(200, 0));
        assertEquals(200, FoldIslandPlacement.contentOrigin(200, -12) - 12);
    }

    @Test public void cornerSlotClearsReportedHoleWhileClockSideUsesNoSpacer() {
        assertEquals(0, FoldIslandPlacement.cornerSlot(2364, 0, -1, 10));
        int slot = FoldIslandPlacement.cornerSlot(2364, 2224, 2294, 10);
        assertEquals(2214, 2364 - 10 - slot);
    }

    @Test public void disabledOrIncompleteLayoutPreservesStockCoordinates() {
        assertEquals(743, FoldIslandPlacement.left(0, true, 2364, 264, 0, false, 224, 2294, 8, 743));
        assertEquals(743, FoldIslandPlacement.left(1, true, 2364, 264, 0, false, -1, -1, 8, 743));
        assertEquals(0, FoldIslandPlacement.normalize(25));
    }
}
