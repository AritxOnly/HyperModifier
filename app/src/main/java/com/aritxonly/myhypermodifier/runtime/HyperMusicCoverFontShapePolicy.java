package com.aritxonly.myhypermodifier;

/** Native variable-font aspect fitting; no View/Canvas stretching. */
final class HyperMusicCoverFontShapePolicy {
    static final float DEFAULT_RATIO = 2.4f;
    interface Probe { float ratio(float width, float height) throws ReflectiveOperationException; }
    static final class Axes {
        final float width, height, ratio;
        Axes(float width, float height, float ratio) { this.width = width; this.height = height; this.ratio = ratio; }
    }
    static float validRatio(float ratio) {
        return Float.isFinite(ratio) && ratio > 0f ? ratio : DEFAULT_RATIO;
    }
    static float axis(float value) { return Float.isFinite(value) ? Math.max(10f, Math.min(100f, value)) : 100f; }
    static float fontSizeForWidth(float size, float referenceWidth, float shapedWidth) {
        if (!(size > 0f) || !(referenceWidth > 0f) || !(shapedWidth > 0f)) return size;
        double fitted = (double) size * referenceWidth / shapedWidth;
        return Double.isFinite(fitted) && fitted > 0 && fitted <= Float.MAX_VALUE ? (float) fitted : size;
    }
    static Axes solve(float requested, float originalWidth, Probe probe) throws ReflectiveOperationException {
        float target = validRatio(requested), width = axis(originalWidth);
        Axes shortFont = measured(width, 10f, probe), tallFont = measured(width, 100f, probe);
        if (between(target, shortFont.ratio, tallFont.ratio))
            return search(target, width, true, shortFont, tallFont, probe);
        Axes end = closer(target, shortFont, tallFont);
        Axes narrow = measured(10f, end.height, probe), wide = measured(100f, end.height, probe);
        return search(target, end.height, false, narrow, wide, probe);
    }
    private static Axes search(float target, float fixed, boolean changeHeight, Axes low, Axes high, Probe probe)
            throws ReflectiveOperationException {
        Axes best = closer(target, low, high);
        if (!between(target, low.ratio, high.ratio) || low.ratio == high.ratio) return best;
        boolean increasing = high.ratio > low.ratio;
        float lo = 10f, hi = 100f;
        for (int i = 0; i < 12; i++) {
            float mid = (lo + hi) / 2f;
            Axes candidate = measured(changeHeight ? fixed : mid, changeHeight ? mid : fixed, probe);
            best = closer(target, best, candidate);
            if ((candidate.ratio < target) == increasing) lo = mid; else hi = mid;
        }
        return best;
    }
    private static Axes measured(float width, float height, Probe probe) throws ReflectiveOperationException {
        float ratio = probe.ratio(width, height);
        if (!Float.isFinite(ratio) || ratio <= 0f) throw new IllegalArgumentException("Invalid native glyph bounds");
        return new Axes(width, height, ratio);
    }
    private static boolean between(float target, float a, float b) { return target >= Math.min(a, b) && target <= Math.max(a, b); }
    private static Axes closer(float target, Axes a, Axes b) { return Math.abs(a.ratio - target) <= Math.abs(b.ratio - target) ? a : b; }
}
