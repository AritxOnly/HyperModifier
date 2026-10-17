package com.aritxonly.myhypermodifier;

/** Stable tab IDs precede positional fallbacks because server-side tab ordering can vary. */
final class DouyinNavigationPolicy {
    private static final String[] LABELS = {"首页", "朋友", "发布", "消息", "我"};
    private DouyinNavigationPolicy() {}
    static boolean isVideoPage(String id, String label) {
        return "homepage_home".equals(id) || "homepage_familiar".equals(id)
                || "首页".equals(label) || "主页".equals(label) || "朋友".equals(label);
    }

    static boolean resolveDarkTheme(Boolean nativeDark, int color, boolean configurationDark) {
        if (nativeDark != null) return nativeDark;
        return darkFromTitleColor(color, configurationDark);
    }

    static boolean darkFromTitleColor(int color, boolean configurationDark) {
        if ((color >>> 24) == 0) return configurationDark;
        int brightness = (((color >> 16) & 255) * 299 + ((color >> 8) & 255) * 587
                + (color & 255) * 114) / 1000;
        return brightness >= 128;
    }

    static int controlsBottomMargin(int original, int viewportTop, int parentTop, int pageTop,
                                    int parentHeight, int dockTop, int clearance, int nativeBottomInset) {
        int settledBottom = viewportTop + parentTop - pageTop + parentHeight;
        return Math.max(original, Math.max(0, settledBottom - dockTop + clearance - nativeBottomInset));
    }

    static float progressTranslation(float original, float current, float trackScreenTop,
                                     float paintedBottom, int dockTop, float gap) {
        float nativeBottom = trackScreenTop + paintedBottom - (current - original);
        return original - Math.max(0f, nativeBottom - dockTop + gap);
    }

    static String fallbackLabel(String id, int index) {
        if ("homepage_publish".equals(id)) return "发布";
        if ("homepage_home".equals(id)) return "首页";
        if ("homepage_familiar".equals(id)) return "朋友";
        if ("homepage_notification".equals(id)) return "消息";
        if ("homepage_profile".equals(id)) return "我";
        if ("homepage_mall".equals(id)) return "商城";
        return LABELS[Math.max(0, Math.min(4, index))];
    }
}
