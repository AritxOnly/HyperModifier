package com.aritxonly.myhypermodifier;

/** Mutable, per-View state: system calls update stock values; settings replays never do. */
final class GlobalGlassBlurState {
    int small;
    int big;
    boolean active;
    private int appliedSmall;
    private int appliedBig;
    final java.util.concurrent.atomic.AtomicBoolean refreshPending = new java.util.concurrent.atomic.AtomicBoolean();

    GlobalGlassBlurState(int small, int big) {
        this(small, big, true);
    }

    GlobalGlassBlurState(int small, int big, boolean active) {
        this.active = active;
        recordStock(small, big);
        recordApplied(small, big);
    }

    void recordStock(int small, int big) {
        this.small = small;
        this.big = big;
    }

    void recordApplied(int small, int big) {
        appliedSmall = small;
        appliedBig = big;
    }

    static int clamp(int radius) {
        return Math.max(0, Math.min(ShadeCardGlassPolicy.MAX_GLASS_BLUR_RADIUS, radius));
    }

    int smallRadius(boolean enabled, int radius) { return enabled && active ? clamp(radius) : small; }
    int bigRadius(boolean enabled, int radius) { return enabled && active ? clamp(radius) : big; }

    boolean needsRefresh(boolean enabled, int radius) {
        return appliedSmall != smallRadius(enabled, radius) || appliedBig != bigRadius(enabled, radius);
    }
}
