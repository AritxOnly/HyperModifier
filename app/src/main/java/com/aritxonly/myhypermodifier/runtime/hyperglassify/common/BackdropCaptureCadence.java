package com.aritxonly.myhypermodifier;

/** Display-paced sampling without forcing other target adapters to change cadence. */
public final class BackdropCaptureCadence {
    private BackdropCaptureCadence() {}

    public static long intervalMs(float refreshRate) {
        float validRate = Float.isFinite(refreshRate) && refreshRate > 0f ? refreshRate : 60f;
        return Math.max(4L, (long) (1_000f / Math.min(validRate, 120f)));
    }

    /**
     * PixelCopy performs a compositor readback and an allocation for every accepted frame.
     * Thirty samples per second keep the frosted backdrop responsive without multiplying
     * readback work on 90/120 Hz displays.
     */
    public static long pixelCopyIntervalMs(float refreshRate, long minimumIntervalMs) {
        return Math.max(
                Math.max(32L, Math.max(0L, minimumIntervalMs)),
                intervalMs(refreshRate));
    }
}
