package com.aritxonly.myhypermodifier;

/** Color-only backdrop policy, independent of blur radius and Android APIs. */
public final class BackgroundDimPolicy {
    // Native MiBackgroundBlendColor uses SkBlendMode values: kSrcOver = 3.
    static final int SRC_OVER = 3;

    private BackgroundDimPolicy() {}

    public static float normalizePercent(float percent) {
        return Float.isFinite(percent) ? Math.max(0f, Math.min(100f, percent)) : 20f;
    }

    static int blackColor(float percent, float animationRatio) {
        float ratio = Float.isFinite(animationRatio)
                ? Math.max(0f, Math.min(1f, animationRatio)) : 0f;
        // Match MiBlurCompat: quantize the configured color first, then truncate animated alpha.
        int baseAlpha = Math.round(255f * normalizePercent(percent) / 100f);
        int alpha = (int) (baseAlpha * ratio);
        return alpha << 24;
    }

    static int[] blendColors(float percent) {
        return new int[]{blackColor(percent, 1f), SRC_OVER};
    }
}
