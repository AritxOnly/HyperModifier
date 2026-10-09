package com.aritxonly.myhypermodifier;

/** Keeps SystemUI's latest requested visibility, including changes made while hidden. */
final class StatusBarNetworkVisibilityPolicy {
    enum Kind { MOBILE_TYPE, MOBILE_ACTIVITY, WIFI_ACTIVITY, WIFI_STANDARD }

    private int stockVisibility;

    StatusBarNetworkVisibilityPolicy(int visibility) {
        stockVisibility = visibility;
    }

    void recordStockVisibility(int visibility) {
        stockVisibility = visibility;
    }

    int resolve(Kind kind, boolean wifi, boolean hideMobileTypeOnWifi,
                boolean hideMobileActivity, boolean hideWifiActivity, boolean hideWifiStandard) {
        boolean hidden = switch (kind) {
            case MOBILE_TYPE -> hideMobileTypeOnWifi && wifi;
            case MOBILE_ACTIVITY -> hideMobileActivity;
            case WIFI_ACTIVITY -> hideWifiActivity;
            case WIFI_STANDARD -> hideWifiStandard;
        };
        return hidden ? 8 /* View.GONE */ : stockVisibility;
    }
}
