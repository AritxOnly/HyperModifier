package com.aritxonly.myhypermodifier;

import java.util.Locale;

/** Title-based roles survive reordered tabs and the optional native publish slot. */
public final class BilibiliNavigationPolicy {
    public enum Role { HOME, DYNAMIC, FOLLOW, MALL, MINE, PUBLISH, OTHER }

    private BilibiliNavigationPolicy() {}

    public static Role role(String label) {
        String value = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (value.contains("首页") || value.equals("home")) return Role.HOME;
        if (value.contains("动态") || value.equals("dynamics")) return Role.DYNAMIC;
        if (value.contains("关注") || value.equals("following") || value.equals("follow")) return Role.FOLLOW;
        if (value.contains("会员购") || value.equals("mall")) return Role.MALL;
        if (value.contains("我的") || value.equals("mine") || value.equals("profile")) return Role.MINE;
        if (value.contains("发布") || value.equals("publish")) return Role.PUBLISH;
        return Role.OTHER;
    }

    /** Presentation only: keep the native FOLLOW role and its independent visibility setting. */
    public static String displayLabel(String nativeLabel) {
        return role(nativeLabel) == Role.FOLLOW ? "动态" : nativeLabel;
    }

    public static boolean isVisible(String label, boolean home, boolean dynamic,
                                    boolean mall, boolean mine, boolean publish) {
        return isVisible(label, home, dynamic, true, mall, mine, publish);
    }

    public static boolean isVisible(String label, boolean home, boolean dynamic, boolean follow,
                                    boolean mall, boolean mine, boolean publish) {
        return switch (role(label)) {
            case HOME -> home;
            case DYNAMIC -> dynamic;
            case FOLLOW -> follow;
            case MALL -> mall;
            case MINE -> mine;
            case PUBLISH -> publish;
            case OTHER -> true;
        };
    }

    /** Hyper-PiliPlus 58e5edc67: home 0.95, dynamics/profile 1.0. */
    public static float iconScale(String label) {
        return role(label) == Role.HOME ? 0.95f : 1f;
    }

    public static boolean overlayAllowed(boolean foreground, boolean splash,
                                         boolean startup, boolean ime) {
        return foreground && !splash && !startup && !ime;
    }
}
