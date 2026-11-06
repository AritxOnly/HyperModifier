package com.aritxonly.myhypermodifier;

/** Per-view ownership of a temporary effective style; never stores the user's setting. */
final class ControlCenterBatteryPercentPolicy {
    static final int PLACEMENT_OUTSIDE = 0;
    static final int PLACEMENT_INSIDE = 1;

    private int stockStyle;
    private boolean applied;
    private boolean dirty;

    static int normalizePlacement(int placement) {
        return placement == PLACEMENT_INSIDE ? PLACEMENT_INSIDE : PLACEMENT_OUTSIDE;
    }

    static boolean drawsInside(boolean solidFrame, int placement) {
        return solidFrame && normalizePlacement(placement) == PLACEMENT_INSIDE;
    }

    static float centeredTranslationY(float originalTranslationY,
                                      float containerLayoutCenterY, float iconCenterY) {
        return originalTranslationY + iconCenterY - containerLayoutCenterY;
    }

    boolean hasOverride() {
        return applied || dirty;
    }

    int beforeStockStyle(int currentStyle) {
        if (!applied) return currentStyle;
        applied = false;
        dirty = true; // Stock may skip its update when the original style has not changed.
        return stockStyle;
    }

    int resolve(int currentStyle, boolean enabled, boolean expanded) {
        return resolve(currentStyle, enabled, expanded, true);
    }

    int resolve(int currentStyle, boolean enabled, boolean expanded, boolean useExternalStyle) {
        if (!applied || currentStyle != 3) stockStyle = currentStyle;
        int desired = enabled && expanded && useExternalStyle && stockStyle == 0 ? 3 : stockStyle;
        applied = desired != stockStyle;
        return desired;
    }

    boolean takeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    int restore(int currentStyle) {
        int result = applied ? stockStyle : currentStyle;
        applied = false;
        dirty = false;
        return result;
    }
}
