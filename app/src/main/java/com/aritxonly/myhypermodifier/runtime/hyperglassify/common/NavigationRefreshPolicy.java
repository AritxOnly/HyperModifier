package com.aritxonly.myhypermodifier;

/** Bounds native tree work independently of the host's display refresh rate. */
public final class NavigationRefreshPolicy {
    private final long intervalMs;
    private long lastRefreshAt = Long.MIN_VALUE;

    public NavigationRefreshPolicy(long intervalMs) {
        this.intervalMs = Math.max(0L, intervalMs);
    }

    public boolean shouldRefresh(long now, boolean force) {
        if (force || lastRefreshAt == Long.MIN_VALUE || now < lastRefreshAt
                || now - lastRefreshAt >= intervalMs) {
            lastRefreshAt = now;
            return true;
        }
        return false;
    }
}
