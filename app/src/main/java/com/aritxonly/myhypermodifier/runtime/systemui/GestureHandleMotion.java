package com.aritxonly.myhypermodifier;

/** A damped visual spring. Input coordinates and native navigation handling are untouched. */
final class GestureHandleMotion {
    static final long RELEASE_HOLD_MS = 260L;
    private static final long HOST_HOLD_MS = 200L;
    private static final float FOLLOW_FACTOR = 0.5f;
    private static final float DRAG_STIFFNESS = 150f;
    private static final float DRAG_DAMPING = 25f;
    private static final float RETURN_STIFFNESS = 105f;
    private static final float RETURN_DAMPING = 16f;
    private static final float SETTLED_DISTANCE = 0.15f;
    private static final float SETTLED_SPEED = 2f;

    private float startX;
    private float startY;
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private float targetX;
    private float targetY;
    private float left;
    private float right;
    private float up;
    private float down;
    private long lastFrameAt;
    private long holdUntil;
    private long releasedAt;
    private boolean dragging;

    void start(float rawX, float rawY, long now) {
        advance(now);
        startX = rawX;
        startY = rawY;
        targetX = targetY = 0f;
        holdUntil = releasedAt = 0L;
        dragging = true;
        lastFrameAt = now;
    }

    void move(float rawX, float rawY, float maxLeft, float maxRight,
            float maxUp, float maxDown, long now) {
        if (!dragging) return;
        advance(now);
        left = maxLeft;
        right = maxRight;
        up = maxUp;
        down = maxDown;
        targetX = resisted((rawX - startX) * FOLLOW_FACTOR, maxLeft, maxRight);
        targetY = resisted((rawY - startY) * FOLLOW_FACTOR, maxUp, maxDown);
    }

    boolean movedBeyond(float rawX, float rawY, float slop) {
        if (!dragging) return false;
        float dx = rawX - startX;
        float dy = rawY - startY;
        return dx * dx + dy * dy >= slop * slop;
    }

    void release(long now) {
        if (!dragging) return;
        advance(now);
        dragging = false;
        releasedAt = now;
        holdUntil = now + RELEASE_HOLD_MS;
    }

    void rebaseForNewHost(long now) {
        if (dragging) {
            lastFrameAt = now;
        } else if (releasedAt != 0L && now - releasedAt < 1_000L
                && (targetX != 0f || targetY != 0f)) {
            // Do not spend the whole return animation while the old navigation view is gone.
            lastFrameAt = now;
            holdUntil = Math.max(holdUntil, now + HOST_HOLD_MS);
        }
    }

    void reset() {
        dragging = false;
        x = y = velocityX = velocityY = targetX = targetY = 0f;
        lastFrameAt = holdUntil = releasedAt = 0L;
    }

    float x(long now) { advance(now); return x; }

    float y(long now) { advance(now); return y; }

    boolean running(long now) {
        advance(now);
        return Math.abs(x - (dragging || now < holdUntil ? targetX : 0f)) > SETTLED_DISTANCE
                || Math.abs(y - (dragging || now < holdUntil ? targetY : 0f)) > SETTLED_DISTANCE
                || Math.abs(velocityX) > SETTLED_SPEED || Math.abs(velocityY) > SETTLED_SPEED
                || (!dragging && now < holdUntil && (targetX != 0f || targetY != 0f));
    }

    private void advance(long now) {
        if (lastFrameAt == 0L || now <= lastFrameAt) {
            lastFrameAt = now;
            return;
        }
        // Cap a long suspended frame; one second is enough for the spring to settle.
        long cursor = Math.max(lastFrameAt, now - 1_000L);
        while (cursor < now) {
            long stepMs = Math.min(16L, now - cursor);
            cursor += stepMs;
            boolean holding = dragging || cursor < holdUntil;
            float stiffness = dragging ? DRAG_STIFFNESS : RETURN_STIFFNESS;
            float damping = dragging ? DRAG_DAMPING : RETURN_DAMPING;
            float dt = stepMs / 1_000f;
            float goalX = holding ? targetX : 0f;
            float goalY = holding ? targetY : 0f;
            velocityX += ((goalX - x) * stiffness - velocityX * damping) * dt;
            velocityY += ((goalY - y) * stiffness - velocityY * damping) * dt;
            x = clamp(x + velocityX * dt, -left, right);
            y = clamp(y + velocityY * dt, -up, down);
        }
        lastFrameAt = now;
        if (!dragging && now >= holdUntil && Math.abs(x) < SETTLED_DISTANCE
                && Math.abs(y) < SETTLED_DISTANCE && Math.abs(velocityX) < SETTLED_SPEED
                && Math.abs(velocityY) < SETTLED_SPEED) {
            x = y = velocityX = velocityY = targetX = targetY = 0f;
        }
    }

    private static float resisted(float displacement, float negativeLimit, float positiveLimit) {
        float limit = displacement < 0f ? negativeLimit : positiveLimit;
        if (limit <= 0f) return 0f;
        float amount = (float) (limit * Math.tanh(Math.abs(displacement) / limit));
        return Math.copySign(amount, displacement);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
