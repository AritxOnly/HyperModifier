package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class DesktopWidgetCompatibilityStatusTest {
    private static String status(int schema, int picker) {
        return "MHM_WIDGET_STATUS_V1\nschema=" + schema + "\npicker=" + picker
                + "\ninstances=1\nbridge=1\nconfig=1\nreason=schema-abi-v1\n";
    }
    @Test public void onlyConfirmedSchemaRejectionRequestsLibrary() {
        for (int stage = 0; stage <= 4; stage++)
            assertEquals(stage == 2, DesktopWidgetCompatibilityStatus.parse(status(stage, 1)).needsSchemaLibrary());
    }
    @Test public void dartFailureDoesNotHideIndependentMetadataState() {
        DesktopWidgetCompatibilityStatus result = DesktopWidgetCompatibilityStatus.parse(status(1, 3));
        assertEquals(1, result.schema);
        assertEquals(3, result.picker);
        assertTrue(result.config);
        assertTrue(result.summary().contains("基础能力：就绪"));
        assertFalse(result.needsSchemaLibrary());
    }
    @Test public void malformedOrIncompleteStatusCannotClaimCompatibility() {
        for (String input : new String[]{"", "MHM_WIDGET_STATUS_V1\nschema=1\n",
                status(1, 1) + "schema=2\n", status(9, 1), status(1, 1).replace("config=1", "config=yes")}) {
            try { DesktopWidgetCompatibilityStatus.parse(input); fail(input); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
