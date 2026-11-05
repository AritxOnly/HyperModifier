package com.aritxonly.myhypermodifier;

/** Only unlocked launcher windows on the primary display may override the stock icon decision. */
public final class DesktopStatusBarPolicy {
    public static final String KEY = "desktop_status_bar_icon_mode";
    public static final int APPEARANCE_LIGHT_STATUS_BARS = 8;
    public static final int SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private DesktopStatusBarPolicy() {}

    public static int normalize(int mode) {
        return mode == LIGHT || mode == DARK ? mode : SYSTEM;
    }

    public static boolean applies(int mode, boolean enabled, boolean launcher,
                                  boolean keyguard, boolean shade, boolean recents) {
        return enabled && normalize(mode) != SYSTEM && launcher && !keyguard && !shade
                && !recents;
    }

    public static boolean hasLightStatusBars(int appearance) {
        return (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
    }

    public static boolean shouldLightenRecentsIcons(boolean enabled, boolean recents,
                                                    boolean nightMode) {
        return enabled && recents && !nightMode;
    }

    public static boolean canRepairRecentsAppearance(boolean enabled, boolean launcher,
                                                     boolean nightMode, boolean knownLight,
                                                     int appearance) {
        return enabled && launcher && !nightMode && knownLight
                && !hasLightStatusBars(appearance);
    }
}
