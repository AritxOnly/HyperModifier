package com.aritxonly.myhypermodifier;

import org.junit.Test;

import static com.aritxonly.myhypermodifier.StatusBarNetworkVisibilityPolicy.Kind.*;
import static org.junit.Assert.assertEquals;

public class StatusBarNetworkVisibilityPolicyTest {
    @Test public void wifiDisconnectRestoresLatestSystemVisibility() {
        var policy = new StatusBarNetworkVisibilityPolicy(0);
        assertEquals(8, policy.resolve(MOBILE_TYPE, true, true, false, false, false));
        policy.recordStockVisibility(4);
        assertEquals(8, policy.resolve(MOBILE_TYPE, true, true, false, false, false));
        assertEquals(4, policy.resolve(MOBILE_TYPE, false, true, false, false, false));
        policy.recordStockVisibility(8);
        assertEquals(8, policy.resolve(MOBILE_TYPE, false, true, false, false, false));
        policy.recordStockVisibility(0);
        assertEquals(0, policy.resolve(MOBILE_TYPE, false, true, false, false, false));
    }

    @Test public void switchesAreIndependentAndDisableRestoresStock() {
        var policy = new StatusBarNetworkVisibilityPolicy(0);
        assertEquals(0, policy.resolve(MOBILE_TYPE, true, false, true, true, false));
        assertEquals(8, policy.resolve(MOBILE_ACTIVITY, false, false, true, false, false));
        assertEquals(0, policy.resolve(WIFI_ACTIVITY, false, false, true, false, false));
        assertEquals(0, policy.resolve(MOBILE_ACTIVITY, false, false, false, true, false));
        assertEquals(8, policy.resolve(WIFI_ACTIVITY, false, false, false, true, false));
        policy.recordStockVisibility(4);
        assertEquals(4, policy.resolve(WIFI_ACTIVITY, true, false, false, false, false));
    }

    @Test public void wifiStandardRestoresLatestVisibilityWithoutHidingActivity() {
        var policy = new StatusBarNetworkVisibilityPolicy(0);
        assertEquals(8, policy.resolve(WIFI_STANDARD, true, false, false, false, true));
        assertEquals(0, policy.resolve(WIFI_ACTIVITY, true, false, false, false, true));
        assertEquals(0, policy.resolve(MOBILE_TYPE, true, false, false, false, true));
        policy.recordStockVisibility(4);
        assertEquals(8, policy.resolve(WIFI_STANDARD, true, false, false, false, true));
        assertEquals(4, policy.resolve(WIFI_STANDARD, true, false, false, false, false));
        policy.recordStockVisibility(0);
        assertEquals(0, policy.resolve(WIFI_STANDARD, true, false, false, true, false));
    }

}
