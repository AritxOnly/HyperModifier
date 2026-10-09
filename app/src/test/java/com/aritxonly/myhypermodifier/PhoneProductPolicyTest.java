package com.aritxonly.myhypermodifier;

import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneProductPolicyTest {
    @Test public void matchesAllRequestedMarketNames() {
        String[][] cases = {
            {"小米 17", "xiaomi-17"}, {"Xiaomi 17 Pro", "xiaomi-17-pro"},
            {"Xiaomi 17 ProMax", "xiaomi-17-pro-max"}, {"Xiaomi 17 Ultra", "xiaomi-17-ultra"},
            {"Xiaomi 17 Ultra Leica", "xiaomi-17-ultra-byleica"}, {"REDMI K90", "redmi-k90"},
            {"REDMI K90 Pro Max", "redmi-k90-pro-max"}, {"Xiaomi 18 Fold", "xiaomi-18-fold"},
            {"Xiaomi 18 pro", "xiaomi-18-pro"}, {"Xiaomi 18 pro max", "xiaomi-18-pro-max"},
            {"REDMI K100 Pro", "redmi-k100-pro"}, {"REDMI K100 Pro Max", "redmi-k100-pro-max"}
        };
        for (String[] item : cases) assertEquals(item[1], PhoneProductPolicy.asset(item[0]));
    }

    @Test public void prefersLeicaEditionAndFallsBackToVerifiedDeviceCode() {
        assertEquals("xiaomi-17-ultra-byleica", PhoneProductPolicy.asset("Xiaomi 17 Ultra", "Xiaomi 17 Ultra by Leica"));
        assertEquals("xiaomi-17-ultra-byleica", PhoneProductPolicy.asset("小米17 Ultra 徕卡版"));
        assertEquals("xiaomi-17", PhoneProductPolicy.asset("25113PN0EC", "pudding"));
        assertEquals("xiaomi-18-pro-max", PhoneProductPolicy.asset(null, "", "XIAOMI_18_PRO_MAX"));
    }

    @Test public void unknownOrSimilarDevicesDoNotGetTheWrongProduct() {
        assertNull(PhoneProductPolicy.asset((String[]) null));
        assertNull(PhoneProductPolicy.asset(null, "", "Xiaomi 17T", "REDMI K90 Ultra", "Xiaomi 18"));
    }
}
