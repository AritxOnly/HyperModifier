package com.aritxonly.myhypermodifier;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Activity/task identity and deadline logic, independent of Android or view lifecycles. */
final class GestureHandlePolicy {
    static final long IMMERSIVE_DURATION_MS = 3_000L;
    private Map<String, String> rules = Collections.emptyMap();
    private String packageName;
    private String activity;
    private int taskId = -1;
    private long shownAt;
    private long touchRevealUntil;
    private long swipeRevealUntil;
    private boolean touchHeld;
    private boolean swipeHeld;
    private String preset;
    private boolean systemApp;
    private Set<String> scope = Collections.emptySet();

    void setRules(Map<String, String> next, long now) {
        configure(next, preset, scope, now);
    }

    void configure(Map<String, String> next, String nextPreset, Set<String> nextScope, long now) {
        String previousMode = mode();
        rules = next;
        preset = nextPreset;
        scope = nextScope;
        if (!Objects.equals(previousMode, mode())) {
            shownAt = now;
        }
    }

    void foreground(String nextPackage, String nextActivity, int nextTaskId, long now) {
        foreground(nextPackage, nextActivity, nextTaskId, false, now);
    }

    void foreground(String nextPackage, String nextActivity, int nextTaskId, boolean isSystemApp, long now) {
        String previousMode = mode();
        boolean changed = !Objects.equals(activity, nextActivity) || taskId != nextTaskId;
        packageName = nextPackage;
        activity = nextActivity;
        taskId = nextTaskId;
        systemApp = isSystemApp;
        if (changed || !Objects.equals(previousMode, mode())) {
            shownAt = now;
        }
    }

    void reveal(long now) { shownAt = now; }

    void revealOnTouch(long now) { touchRevealUntil = now + IMMERSIVE_DURATION_MS; }

    void touchDown(long now) {
        touchHeld = true;
        revealOnTouch(now);
    }

    void touchEvent(long now) {
        if (touchHeld) revealOnTouch(now);
    }

    void touchUp(long now) {
        if (!touchHeld) return;
        touchHeld = false;
        revealOnTouch(now);
    }

    void revealOnSwipe(long now) { swipeRevealUntil = now + IMMERSIVE_DURATION_MS; }

    void swipeDown(long now) {
        swipeHeld = true;
        revealOnSwipe(now);
    }

    void swipeEvent(long now) {
        if (swipeHeld) revealOnSwipe(now);
    }

    void swipeUp(long now) {
        if (!swipeHeld) return;
        swipeHeld = false;
        revealOnSwipe(now);
    }

    boolean touchRevealActive(long now) { return touchHeld || now < touchRevealUntil; }

    boolean swipeRevealActive(long now) { return swipeHeld || now < swipeRevealUntil; }

    void clearTouchReveal() {
        touchHeld = false;
        touchRevealUntil = 0L;
    }

    void clearSwipeReveal() {
        swipeHeld = false;
        swipeRevealUntil = 0L;
    }

    String mode() {
        if (packageName == null) return null;
        String explicit = rules.get(packageName);
        if ("system".equals(explicit)) return null;
        return explicit != null ? explicit : GestureHandleDefaults.mode(preset, packageName, systemApp, scope);
    }

    boolean hidden(long now, boolean systemHidden) {
        if (touchRevealActive(now) || swipeRevealActive(now)) return false;
        String mode = mode();
        if ("hide".equals(mode)) return true;
        if ("show".equals(mode)) return false;
        if ("immersive".equals(mode)) return now - shownAt >= IMMERSIVE_DURATION_MS;
        return systemHidden;
    }

    long remaining(long now) {
        long immersive = "immersive".equals(mode())
                ? Math.max(0L, IMMERSIVE_DURATION_MS - (now - shownAt)) : 0L;
        long touch = touchHeld ? 0L : Math.max(0L, touchRevealUntil - now);
        long swipe = swipeHeld ? 0L : Math.max(0L, swipeRevealUntil - now);
        return Math.max(immersive, Math.max(touch, swipe));
    }
}
