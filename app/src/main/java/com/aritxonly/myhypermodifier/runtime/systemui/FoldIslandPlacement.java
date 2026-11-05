package com.aritxonly.myhypermodifier;

/** Horizontal positions in the island window's (physical left-to-right) coordinate system. */
final class FoldIslandPlacement {
    static final String KEY = "fold_island_position";
    static final int STOCK = 0, NOTIFICATIONS = 1, CAMERA = 2;

    // Lhasa's built-in panels report 1672 x 2364 (inner) and 1168 x 1712 (cover).
    static boolean isInnerPanel(int width, int height) {
        return Math.min(width, height) == 1672 && Math.max(width, height) == 2364;
    }

    static int normalize(int mode) {
        return mode == NOTIFICATIONS || mode == CAMERA ? mode : STOCK;
    }

    static int cornerSlot(int screen, int holeLeft, int holeCenter, int edgeGap) {
        if (holeCenter <= screen / 2 || holeLeft < 0 || holeLeft >= screen - edgeGap) return 0;
        // The island ends edgeGap pixels before the display edge. Preserve an
        // additional edgeGap on the content side of the system exclusion.
        return screen - holeLeft;
    }

    static int contentOrigin(int capsuleLeft, float animationTranslation) {
        return capsuleLeft - Math.round(animationTranslation);
    }

    static int left(int mode, boolean inner, int screen, int width, int satellite,
                    boolean rtl, int clockEnd, int cameraX, int gap, int stock) {
        if (mode == STOCK || width <= 0 || screen <= 0 || width + satellite > screen) return stock;
        int min = rtl ? satellite : 0;
        int max = screen - width - (rtl ? 0 : satellite);
        if (!inner) return Math.max(min, Math.min(max, (screen - width) / 2));
        if (mode == CAMERA && cameraX > screen / 2) {
            // The corner hole is closer to the edge than half a typical island.
            // Let longer capsules grow inward, keeping the satellite on screen.
            return Math.max(min, Math.min(Math.max(min, max - gap), cameraX - width / 2));
        }
        if (clockEnd <= 0) return stock;
        return Math.max(min, Math.min(max, rtl ? screen - clockEnd - width : clockEnd));
    }

    private FoldIslandPlacement() {}
}
