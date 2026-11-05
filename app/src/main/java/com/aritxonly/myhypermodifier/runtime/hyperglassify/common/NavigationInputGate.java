package com.aritxonly.myhypermodifier;

/**
 * Keeps first-use taps out of the host's startup transition and serializes injected navigation.
 * This is deliberately independent of display pacing so the guarantee survives 60/120 Hz.
 */
final class NavigationInputGate {
    static final long SETTLE_TIME_MS = 350L;
    static final long CLICK_INTERVAL_MS = 350L;

    private long firstVisibleAt = Long.MIN_VALUE;
    private long nextAllowedAt = Long.MIN_VALUE;
    private boolean routing;

    void markVisible(long now) {
        if (firstVisibleAt != Long.MIN_VALUE) return;
        firstVisibleAt = now;
        nextAllowedAt = now + SETTLE_TIME_MS;
    }

    boolean samplingReady(long now) {
        return firstVisibleAt != Long.MIN_VALUE && now >= firstVisibleAt + SETTLE_TIME_MS;
    }

    boolean begin(long now, boolean visible, boolean nativeReady) {
        if (!visible || !nativeReady || routing || now < nextAllowedAt) return false;
        routing = true;
        nextAllowedAt = now + CLICK_INTERVAL_MS;
        return true;
    }

    void finish() {
        routing = false;
    }

    boolean isRouting() {
        return routing;
    }

    void reset() {
        firstVisibleAt = nextAllowedAt = Long.MIN_VALUE;
        routing = false;
    }
}
