package com.aritxonly.myhypermodifier;

/** Uses the same current-user resources as DisplayPolicy, rather than system Context resources. */
final class GestureHandleTransitionOverride {
    static boolean resolve(boolean overlay, int navigationMode, boolean enabled) {
        return overlay || (enabled && navigationMode == 2);
    }
}
