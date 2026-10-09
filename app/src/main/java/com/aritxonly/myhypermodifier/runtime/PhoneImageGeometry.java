package com.aritxonly.myhypermodifier;

/** Shared fit/transform math for the Settings illustration and the calibration preview. */
public final class PhoneImageGeometry {
    private PhoneImageGeometry() {}
    public static float offset(float value) { return finiteClamp(value, -96f, 96f, 0f); }
    public static float scale(float value) { return finiteClamp(value, .1f, 4f, 1f); }
    private static float finiteClamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    /** Offset units are dp in the real 64dp illustration, including enlarged previews. */
    public static float[] destination(float boxWidth, float boxHeight, float imageWidth, float imageHeight,
                                      float x, float y, float scale) {
        float fit = .88f * Math.min(boxWidth / imageWidth, boxHeight / imageHeight) * scale(scale);
        float width = imageWidth * fit, height = imageHeight * fit;
        float left = (boxWidth - width) / 2 + offset(x) * boxWidth / 64f;
        float top = (boxHeight - height) / 2 + offset(y) * boxHeight / 64f;
        return new float[] { left, top, left + width, top + height };
    }
}
