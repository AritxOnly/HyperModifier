package com.aritxonly.myhypermodifier;

/** Color-only backdrop policy, independent of blur radius and Android APIs. */
public final class BackgroundDimPolicy {
    // Native MiBackgroundBlendColor uses SkBlendMode values: kSrcOver = 3.
    static final int SRC_OVER = 3;
    // Verified in the device's libhwui shader: 26 is saturation blending and 201
    // adjusts saturation. Mode 106 instead remaps Lab lightness, so retaining it
    // leaves stock dim active even when the custom overlay is fully transparent.
    private static boolean preservesSaturation(int mode) {
        return mode == 26 || mode == 201;
    }

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

    static int windowColor(int original, int stockAlpha, float percent) {
        float ratio = stockAlpha > 0 ? (original >>> 24) / (float) stockAlpha : 0f;
        return blackColor(percent, ratio);
    }

    static int[] blendColors(int[] original, float percent) {
        int retained = 0;
        if (original != null) {
            for (int i = 0; i + 1 < original.length; i += 2) {
                if (preservesSaturation(original[i + 1])) retained += 2;
            }
        }
        int[] colors = new int[retained + 2];
        int next = 0;
        if (original != null) {
            for (int i = 0; i + 1 < original.length; i += 2) {
                if (preservesSaturation(original[i + 1])) {
                    colors[next++] = original[i];
                    colors[next++] = original[i + 1];
                }
            }
        }
        // Dim after stock color processing, preserving its saturation parameters.
        colors[next] = blackColor(percent, 1f);
        colors[next + 1] = SRC_OVER;
        return colors;
    }
}
