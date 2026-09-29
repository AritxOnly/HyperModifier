package com.aritxonly.myhypermodifier;

/** Display-paced sampling without forcing other target adapters to change cadence. */
public final class BackdropCaptureCadence {
    private BackdropCaptureCadence() {}

    public static long intervalMs(float refreshRate) {
        float validRate = Float.isFinite(refreshRate) && refreshRate > 0f ? refreshRate : 60f;
        return Math.max(4L, (long) (1_000f / Math.min(validRate, 120f)));
    }
}
