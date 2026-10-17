package com.aritxonly.myhypermodifier;

/** InCallUI keeps its stock sampling/tint; only the blur amount changes. */
final class InCallBlurPolicy {
    private InCallBlurPolicy() {}

    static int backgroundRadius(int stock, int percent) {
        return Math.max(0, Math.min(500, Math.round(stock * (percent / 100f))));
    }

    static int containerRadius(int stock, int backgroundPercent, boolean glassContainer, int glassRadius) {
        // A stock zero is a sampler clear/animation boundary, never an invitation to enable blur.
        if (stock <= 0) return stock;
        if (glassContainer && glassRadius >= 0) return GlobalGlassBlurState.clamp(glassRadius);
        return backgroundPercent == 100 ? stock : backgroundRadius(stock, backgroundPercent);
    }

    static float bitmapRadius(float stock, int percent) {
        // ScriptIntrinsicBlur accepts (0, 25]; zero is handled by an input copy.
        return Math.max(0.001f, Math.min(25f, stock * (percent / 100f)));
    }

    static int bitmapConfig(int blurPercent, boolean dimEnabled, float dimPercent) {
        int alpha = dimEnabled ? BackgroundDimPolicy.blackColor(dimPercent, 1f) >>> 24 : -1;
        return blurPercent | ((alpha + 1) << 8);
    }

    static int blurPercent(int config) { return config & 255; }
    static int dimAlpha(int config) { return (config >>> 8) - 1; }

    static boolean glassActive(int small, int big) {
        return small != 0 || big != 0;
    }
}
