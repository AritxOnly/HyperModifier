package com.aritxonly.myhypermodifier;

/** Stable persisted values; stock and none deliberately have different meanings. */
public final class KeyShortcutActions {
    public static final String STOCK = "stock";
    public static final String WALLET = "wallet";
    public static final String MEMORY = "memory";
    public static final String SCREEN = "screen";
    public static final String NONE = "none";
    public static final String VOLUME_POWER_KEY = "shortcut_volume_up_power";
    public static final String DOUBLE_POWER_KEY = "shortcut_double_power";
    public static final String DOUBLE_POWER_SOURCE = "double_click_power_key";
    public static final String VOLUME_POWER_SOURCE = "key_chord_power_volume_up";

    private KeyShortcutActions() {}
    public static String normalize(String value) {
        return WALLET.equals(value) || MEMORY.equals(value) || SCREEN.equals(value) || NONE.equals(value)
                ? value : STOCK;
    }
    public static boolean isVolumePowerPair(int first, int second) {
        return first == 24 && second == 26 || first == 26 && second == 24;
    }
}
