package com.aritxonly.myhypermodifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted app-wide defaults; per-app rules still take priority. */
public final class GestureHandlePresets {
    public static final String MODULE = "module";
    public static final String SHOW = "show";
    public static final String HIDE = "hide";
    public static final String IMMERSIVE = "immersive";
    /** Used by the app-wide reset; the page exposes the four explicit presets above. */
    public static final String STOCK = "stock";
    public static final Map<String, String> MODULE_APPS = moduleApps();

    private GestureHandlePresets() {}

    public static boolean selectable(String value) {
        return MODULE.equals(value) || SHOW.equals(value)
                || HIDE.equals(value) || IMMERSIVE.equals(value);
    }

    public static boolean valid(String value) {
        return selectable(value) || STOCK.equals(value);
    }

    public static String fromStored(String value, boolean legacyModulePreset) {
        if (valid(value)) return value;
        // Previous releases stored the all-system choice as a Boolean or JSON "system".
        if ("system".equals(value)) return SHOW;
        return legacyModulePreset ? MODULE : SHOW;
    }

    private static Map<String, String> moduleApps() {
        Map<String, String> apps = new LinkedHashMap<>();
        apps.put("com.android.calendar", HIDE);
        apps.put("com.android.deskclock", HIDE);
        apps.put("com.android.soundrecorder", HIDE);
        apps.put("com.coolapk.market", HIDE);
        apps.put("com.deepseek.chat", HIDE);
        apps.put("com.eg.android.AlipayGphone", HIDE);
        apps.put("com.hyper.gallery.plugin", HIDE);
        apps.put("com.lilystudio.wheretosleepinnju", SHOW);
        apps.put("com.miui.gallery", HIDE);
        apps.put("com.miui.mediaeditor", HIDE);
        apps.put("com.miui.notes", HIDE);
        apps.put("com.miui.themestore", HIDE);
        apps.put("com.miui.weather2", HIDE);
        apps.put("com.moonshot.kimichat", HIDE);
        apps.put("com.openai.chatgpt", HIDE);
        apps.put("com.ruanmei.ithome", HIDE);
        apps.put("com.tencent.mobileqq", SHOW);
        apps.put("com.xingin.xhs", SHOW);
        apps.put("me.weishu.kernelsu", HIDE);
        return Collections.unmodifiableMap(apps);
    }
}
