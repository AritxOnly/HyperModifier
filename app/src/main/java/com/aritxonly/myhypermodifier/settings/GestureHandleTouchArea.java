package com.aritxonly.myhypermodifier;

/** Screen-bottom touch band used by the gesture hint's passive input observer. */
public final class GestureHandleTouchArea {
    public static final float DEFAULT_DP = 16f;
    public static final float MAX_DP = 32f;

    private GestureHandleTouchArea() {}

    public static float normalize(float distanceDp) {
        if (Float.isNaN(distanceDp) || Float.isInfinite(distanceDp)) return DEFAULT_DP;
        return Math.max(0f, Math.min(MAX_DP, distanceDp));
    }

    static boolean contains(float rawX, float rawY, int displayWidth, int displayHeight,
            float handleBottomY, float density, float distanceDp) {
        float distance = normalize(distanceDp);
        return distance > 0f && density > 0f && displayWidth > 0 && displayHeight > 0
                && handleBottomY >= displayHeight - 32f * density
                && rawX >= 0f && rawX <= displayWidth
                && rawY >= displayHeight - distance * density && rawY <= displayHeight;
    }
}
