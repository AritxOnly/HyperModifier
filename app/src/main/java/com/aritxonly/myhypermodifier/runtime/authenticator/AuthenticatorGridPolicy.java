package com.aritxonly.myhypermodifier;

/** Window/content width, rather than physical display resolution, controls card density. */
final class AuthenticatorGridPolicy {
    private AuthenticatorGridPolicy() {}
    static int columns(boolean enabled, int pixels, float density) {
        if (!enabled || pixels <= 0 || !Float.isFinite(density) || density <= 0) return 1;
        float dp = pixels / density;
        return dp >= 840 ? 3 : dp >= 600 ? 2 : 1;
    }
    static int dragDirections(int nativeDirections, boolean grid) {
        // START/END are 0x10/0x20; add UP/DOWN only while native reordering is allowed.
        return grid && nativeDirections != 0 ? nativeDirections | 0x03 : nativeDirections;
    }
}
