package com.aritxonly.myhypermodifier;

/** Decisions shared by the HMC adapter and the OEM notification combine. */
final class HyperMusicCoverExtensionPolicy {
    private HyperMusicCoverExtensionPolicy() {}
    static boolean active(boolean enabled, boolean held, boolean locked, boolean shown,
                          Integer shade, boolean blocked, String stackState) {
        return enabled && held && locked && shown && !blocked
                && (shade == null || shade == 1) && ("NUMBER".equals(stackState) || "STACK".equals(stackState));
    }
    // TimeView initializes HGHT to 28; any finite axis value may be tested.
    static final float DEFAULT_HEIGHT = 28f;
    static float validHeight(float value) {
        return Float.isFinite(value) ? value : DEFAULT_HEIGHT;
    }
    static float storedHeight(boolean hasAxisValue, float axisValue, float legacyFactor) {
        return validHeight(hasAxisValue ? axisValue : DEFAULT_HEIGHT * legacyFactor);
    }
    static float axisHeight(float original, float setting, float progress) {
        float p = Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
        return (float) ((double) original * (1d - p) + (double) validHeight(setting) * p);
    }
    static final class FontTransition {
        private float reference = Float.NaN;
        private boolean held;
        void observe(float original, boolean active) {
            if (!active || !Float.isFinite(reference)) reference = validHeight(original);
            held = active;
        }
        void beforeOemHeightChange(float original, boolean active) {
            if (!held && active) observe(original, true);
        }
        float reference() { return reference; }
        float height(float setting, float progress) { return axisHeight(reference, setting, progress); }
    }
    static final class FontReference {
        float height, width, weight, size;
        private boolean initialized, held;
        boolean observe(float h, float w, float wt, float sz, boolean active) {
            boolean changed = !initialized || !active || !held;
            if (changed) { height = h; width = w; weight = wt; size = sz; initialized = true; }
            held = active;
            return changed;
        }
    }
    static float baselineUnit(float shapedUnit, float ratio) {
        return Float.isFinite(ratio) && ratio > 0f ? shapedUnit / ratio : shapedUnit;
    }
    static Object[] notificationInputs(Object[] inputs, boolean expanded, int hmcMode,
                                       boolean alwaysSink) {
        Object[] result = inputs.clone();
        int mode = expanded ? 1 : hmcMode != 0 ? hmcMode : alwaysSink ? 1 : 0;
        if (result.length >= 7 && mode != 0) {
            result[5] = mode == 2;
            result[6] = mode == 2;
        }
        return result;
    }
}
