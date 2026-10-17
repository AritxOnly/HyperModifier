package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalWidgetCatalogSizesTest {
    private LocalWidgetCatalogSizes sizes() {
        return new LocalWidgetCatalogSizes(new int[]{60,60,160,160,344,160,344,344,318},
                new int[]{60,160,60,160,60,244,160,344,152});
    }
    @Test public void legacyWideProvidersUseStockFourByTwoFootprint() {
        assertEquals(2, sizes().select(0,0,327,152));
        assertEquals(2, sizes().select(0,0,306,147));
        assertEquals(2, sizes().select(0,0,316,147));
        assertEquals(1, sizes().select(0,0,147,147));
    }
    @Test public void explicitCellsTakePriorityAndUnsupportedTargetsNeverShrink() {
        assertEquals(2, sizes().select(4,2,999,999));
        assertEquals(6, sizes().select(2,1,110,40));
        assertEquals(2, sizes().select(3,2,110,40));
        assertEquals(0, sizes().select(5,5,110,40));
    }
    @Test public void portraitAndTallWidgetsDoNotBecomeSquare() {
        assertEquals(7, sizes().select(0,0,150,230));
        assertEquals(4, sizes().select(0,0,300,300));
        assertEquals(3, sizes().select(0,0,300,50));
    }
    @Test public void MissingOrOversizedMetadataDoesNotDefaultToTwoByTwo() {
        assertEquals(0, sizes().select(0,0,500,500));
        assertEquals(0, sizes().select(0,0,0,0));
        assertEquals(0, sizes().select(0,0,150,0));
    }
    @Test public void selectedStyleAndBindingCellCountsAgree() {
        for (int[] span : LocalWidgetCatalogSizes.SPANS) {
            int style = LocalWidgetCatalogPolicy.style(span[0],span[1]);
            assertEquals(span[0], LocalWidgetCatalogSizes.spanX(style));
            assertEquals(span[1], LocalWidgetCatalogSizes.spanY(style));
        }
    }
}
