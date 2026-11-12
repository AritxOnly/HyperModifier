package com.aritxonly.myhypermodifier;

public final class LockscreenBottomPolicy {
    public static final int SCREEN = 0, SHORTCUTS = 1;
    public static final float DEFAULT_SIZE = 54f, DEFAULT_GAP = 24f;
    private LockscreenBottomPolicy() {}
    public static float size(float value) { return finite(value, DEFAULT_SIZE, 32f, 96f); }
    static float iconScale(float diameterDp) { return size(diameterDp) / 54f; }
    public static float gap(float value) { return finite(value, DEFAULT_GAP, 0f, 480f); }
    public static int anchor(int value) { return value == SHORTCUTS ? SHORTCUTS : SCREEN; }
    private static float finite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    static float bottom(float original, float rootBottom, float stackTop, float stackHeight,
            float shortcutTop, float gapPx, int anchor, boolean shortcutsEnabled) {
        if (!Float.isFinite(original) || stackHeight <= 0 || !Float.isFinite(rootBottom)
                || !Float.isFinite(stackTop) || !Float.isFinite(gapPx)) return original;
        float edge = rootBottom;
        if (anchor == SHORTCUTS) {
            if (!shortcutsEnabled || !Float.isFinite(shortcutTop)) return original;
            edge = shortcutTop;
        }
        return Math.max(0, Math.min(stackHeight, edge - stackTop - Math.max(0, gapPx)));
    }
}
