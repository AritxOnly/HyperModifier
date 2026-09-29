package com.aritxonly.myhypermodifier;

/** Drawing opacity, separate from the visibility policy and its three-second deadline. */
final class GestureHandleFade {
    static final long DURATION_MS = 220L;
    static final long QUICK_REVEAL_DURATION_MS = 90L;
    private float from;
    private float target;
    private long startedAt;
    private long duration = DURATION_MS;

    GestureHandleFade(boolean hidden) {
        from = target = hidden ? 0f : 1f;
    }

    float alpha(boolean hidden, long now) {
        return alpha(hidden, now, false);
    }

    float alpha(boolean hidden, long now, boolean quickReveal) {
        float next = hidden ? 0f : 1f;
        float current = value(now);
        if (next != target) {
            // Reverse from the current frame rather than jumping to either endpoint.
            from = current;
            target = next;
            startedAt = now;
            duration = !hidden && quickReveal ? QUICK_REVEAL_DURATION_MS : DURATION_MS;
        }
        return current;
    }

    boolean running(long now) {
        return from != target && now - startedAt < duration;
    }

    private float value(long now) {
        float progress = Math.max(0f, Math.min(1f, (now - startedAt) / (float) duration));
        float eased = progress * progress * (3f - 2f * progress);
        return from + (target - from) * eased;
    }
}
