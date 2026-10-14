package com.aritxonly.myhypermodifier;

/** Only unlocked launcher windows on the primary display may override the stock icon decision. */
public final class DesktopStatusBarPolicy {
    public static final String KEY = "desktop_status_bar_icon_mode";
    public static final int SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private DesktopStatusBarPolicy() {}

    public static int normalize(int mode) {
        return mode == LIGHT || mode == DARK ? mode : SYSTEM;
    }

    public static boolean applies(int mode, boolean enabled, boolean launcher,
                                  boolean keyguard, boolean shade) {
        return enabled && normalize(mode) != SYSTEM && launcher && !keyguard && !shade;
    }
}
