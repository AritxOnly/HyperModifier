package com.aritxonly.myhypermodifier;

/** Fixed-width ceilings and absolute side margins within the native notification area. */
public final class NotificationWidthPolicy {
    public static final int FIXED = 0, MARGINS = 1;
    public static final float DEFAULT_MARGIN_DP = 14f, MAX_MARGIN_DP = 160f;
    public static int mode(int value) { return value == MARGINS ? MARGINS : FIXED; }
    public static float normalizeMargin(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(MAX_MARGIN_DP, value)) : DEFAULT_MARGIN_DP;
    }
    public static final float DEFAULT_DP = 400f;
    public static final float MIN_DP = 240f;
    public static final float MAX_DP = 840f;
    private NotificationWidthPolicy() {}
    public static float normalize(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? DEFAULT_DP
                : Math.max(MIN_DP, Math.min(MAX_DP, value));
    }
    static int sidePadding(int width, int original, float density, float limitDp) {
        if (width <= 0 || density <= 0 || !Float.isFinite(density)) return original;
        int limit = Math.max(1, Math.round(normalize(limitDp) * density));
        // Round upward so an odd pixel cannot exceed the requested ceiling.
        return Math.max(original, Math.max(0, (width - limit + 1) / 2));
    }
    // Sign bit distinguishes margin mode, including zero margin, from a disabled setting.
    static int settingCode(boolean enabled, int mode, float width, float margin) {
        if (!enabled) return 0;
        return mode(mode) == MARGINS ? Float.floatToIntBits(normalizeMargin(margin)) | Integer.MIN_VALUE
                : Float.floatToIntBits(normalize(width));
    }
    static int configuredPadding(int width, int original, float density, int code) {
        if (code == 0 || width <= 0 || density <= 0 || !Float.isFinite(density)) return original;
        if (code > 0) return sidePadding(width, original, density, Float.intBitsToFloat(code));
        float margin = Float.intBitsToFloat(code & Integer.MAX_VALUE);
        int desired = Math.round(normalizeMargin(margin) * density);
        int minimumContent = Math.min(width, Math.round(MIN_DP * density));
        // Keep a usable content width on narrow screens, even with an oversized requested margin.
        return Math.min(desired, Math.max(0, (width - minimumContent) / 2));
    }
    static int selected(long signature, int scene, boolean open) {
        return scene == 1 ? (int) signature : open ? (int) (signature >>> 32) : 0;
    }
    static float activeLimit(boolean enabled, boolean compatible, int status, boolean expanded,
            boolean shadeEnabled, float shade, boolean lockEnabled, float lock) {
        if (!enabled || compatible) return 0;
        if (status == 1) return lockEnabled ? normalize(lock) : 0;
        return expanded && shadeEnabled ? normalize(shade) : 0;
    }
}
