package com.aritxonly.myhypermodifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Filter presentation only: returned positions remain indices into the complete native tab list. */
public final class NativeBottomBarPolicy {
    private NativeBottomBarPolicy() {}

    /** Covering the injected panel must not revive an already-replaced native dock. */
    public static boolean shouldSuppressNative(boolean enabled, boolean overlayVisible,
                                               boolean retainDuringCover) {
        return enabled && (overlayVisible || retainDuringCover);
    }

    public static List<Integer> visibleNavigationIndices(List<String> labels, int actionIndex,
                                                         BiPredicate<String, Integer> visible) {
        List<Integer> result = new ArrayList<>();
        int firstNavigation = -1;
        for (int index = 0; index < labels.size(); index++) {
            if (index == actionIndex) continue;
            if (firstNavigation == -1) firstNavigation = index;
            if (visible.test(labels.get(index), index)) result.add(index);
        }
        if (result.isEmpty() && firstNavigation >= 0) result.add(firstNavigation);
        return result;
    }
}
