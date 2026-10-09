package com.aritxonly.myhypermodifier;

/** Minimal displacement from the user's saved AllInOne layout; all dimensions are pixels. */
final class LockscreenClockAvoidancePolicy {
    static final float GAP_DP = 32f;

    record Result(float translation, int width, int height, int weight, float magazineTranslation) {}

    static Result calculate(float editTop, float editHeight, float topRatio, float bottomRatio,
                            int fullHeight, int fullWidth, int minHeight, int baseHeight,
                            int weight, float magazineHeight, float squeezeRatio,
                            boolean scaleWeight, float notificationTop, float density) {
        float topSpace = editHeight * topRatio;
        float spare = editHeight - baseHeight;
        // Preserve the native magazine-only layout instead of treating the magazine as a new
        // notification. Unusual layouts without enough magazine space use native calculation.
        if (magazineHeight > spare || spare < 0 || topSpace < 0 || topSpace > spare
                || minHeight <= 0 || fullHeight < minHeight || fullWidth <= 0
                || density <= 0 || squeezeRatio <= 0 || Float.isNaN(notificationTop)) return null;
        float share = topRatio + bottomRatio > 0
                ? topRatio / (topRatio + bottomRatio) : 0.46153846f;
        float restingY = Math.max(-topSpace,
                Math.min(-magazineHeight * share, spare - topSpace - magazineHeight));
        float bottom = editTop + topSpace + restingY + baseHeight + magazineHeight;
        float intrusion = Math.max(0, bottom + GAP_DP * density - notificationTop);
        float movement = Math.min(intrusion, topSpace + restingY);
        float shrink = Math.min(Math.max(0, intrusion - movement), fullHeight - minHeight);
        int height = Math.max(minHeight, (int) Math.floor(fullHeight - shrink));
        float reduction = fullHeight - height;
        // Like the native minimum-size branch, move farther only after exhausting font height.
        float overflow = Math.max(0, intrusion - movement - reduction);
        float translation = restingY - movement - overflow;
        float aspect = (float) fullWidth / fullHeight;
        float limit = Math.max(aspect, squeezeRatio);
        int width = Math.min(fullWidth, Math.round(height * limit));
        float scale = aspect >= squeezeRatio
                ? reduction / Math.max(1, fullHeight - minHeight)
                : Math.max(0, fullWidth / squeezeRatio - height)
                    / Math.max(1, fullWidth / squeezeRatio - minHeight);
        int resultWeight = scaleWeight ? (int) (weight * (1 - 0.15f * Math.min(1, scale))) : weight;
        return new Result(translation, width, height, resultWeight, translation - reduction);
    }
}
