package com.aritxonly.myhypermodifier;

/** Pure scale policy shared by settings, UI and the SystemUI battery icon hook. */
final class StatusBarBatteryIconSizePolicy {
    static final float MIN = .5f;
    static final float MAX = 2f;
    static final float DEFAULT = 1f;

    private StatusBarBatteryIconSizePolicy() {}

    static float normalize(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return DEFAULT;
        return Math.max(MIN, Math.min(MAX, scale));
    }

    static float scaledValue(float original, float scale) {
        return original * normalize(scale);
    }
}
