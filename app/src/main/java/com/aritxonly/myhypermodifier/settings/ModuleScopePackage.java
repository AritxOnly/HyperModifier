package com.aritxonly.myhypermodifier;

import java.util.Set;

/** Declared module targets shared by preset classification and the scope dashboard. */
public final class ModuleScopePackage {
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final String PLUGIN = "miui.systemui.plugin";
    public static final String MILINK = "com.milink.service";
    public static final String XIAOMI_HEALTH = "com.mi.health";
    public static final String MARKET = "com.xiaomi.market";
    public static final String MI_HOME = "com.xiaomi.smarthome";
    public static final String AMAP = "com.autonavi.minimap";
    public static final String XIAOMI_COMMUNITY = "com.xiaomi.vipaccount";
    public static final String BILIBILI = "tv.danmaku.bili";
    public static final Set<String> PACKAGES = Set.of(SYSTEM_UI, PLUGIN, MILINK, XIAOMI_HEALTH,
            MARKET, MI_HOME, AMAP, XIAOMI_COMMUNITY, BILIBILI);

    private ModuleScopePackage() {}
}
