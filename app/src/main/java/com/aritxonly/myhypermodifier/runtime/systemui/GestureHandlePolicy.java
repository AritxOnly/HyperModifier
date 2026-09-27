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
    private boolean modulePreset;
    private boolean systemApp;
    private Set<String> scope = Collections.emptySet();

    void setRules(Map<String, String> next, long now) {
        configure(next, modulePreset, scope, now);
    }

    void configure(Map<String, String> next, boolean useModulePreset, Set<String> nextScope, long now) {
        String previousMode = mode();
        rules = next;
        modulePreset = useModulePreset;
        scope = nextScope;
        if (!Objects.equals(previousMode, mode())) shownAt = now;
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
        if (changed || !Objects.equals(previousMode, mode())) shownAt = now;
    }

    void reveal(long now) { shownAt = now; }

    String mode() {
        if (packageName == null) return null;
        String explicit = rules.get(packageName);
        if ("system".equals(explicit)) return null;
        return explicit != null ? explicit : GestureHandleDefaults.mode(modulePreset, packageName, systemApp, scope);
    }

    boolean hidden(long now, boolean systemHidden) {
        String mode = mode();
        if ("hide".equals(mode)) return true;
        if ("show".equals(mode)) return false;
        if ("immersive".equals(mode)) return now - shownAt >= IMMERSIVE_DURATION_MS;
        return systemHidden;
    }

    long remaining(long now) {
        return "immersive".equals(mode())
                ? Math.max(0L, IMMERSIVE_DURATION_MS - (now - shownAt)) : 0L;
    }
}
