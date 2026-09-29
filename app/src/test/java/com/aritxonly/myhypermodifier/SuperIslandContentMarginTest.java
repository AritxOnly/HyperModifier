package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SuperIslandContentMarginTest {
    @Test public void ordinaryContentKeepsStockHeightWhileHostReservesBottomSpace() {
        int content = SuperIslandContentMargin.contentHeight(200, 84, 168, false);
        assertEquals(168, content);
        assertEquals(184, SuperIslandContentMargin.hostHeight(content, 16));
    }

    @Test public void mediaAndPromotedContentCanExceedStockMaximum() {
        int content = SuperIslandContentMargin.contentHeight(200, 84, 168, true);
        assertEquals(200, content);
        assertEquals(224, SuperIslandContentMargin.hostHeight(content, 24));
    }

    @Test public void disabledMarginPreservesTheStockSize() {
        int content = SuperIslandContentMargin.contentHeight(0, 84, 168, false);
        assertEquals(84, content);
        assertEquals(84, SuperIslandContentMargin.hostHeight(content, 0));
    }
}
