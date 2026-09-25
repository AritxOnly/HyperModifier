package com.aritxonly.myhypermodifier;

import java.util.Arrays;

/** Shared heads-up MiGlass defaults for the settings app and hooked SystemUI process. */
final class HeadsUpGlassDefaults {
    private static final float[] REGULAR = {
            0.8f, 1f, 0f, 1f, 0.2f, 2f, 0.14f, 0.1f, 0f, 0f, 0.02f,
            1f, 1f, 1f, 1.5f, 0f, 0.25f, 0.65f, 1f, 64f, 3.8f, 80f, 600f,
            1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.5f, 1f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };
    private static final float[] DARK = {
            0.8f, 1f, 0f, 1f, 0.2f, 2f, 0.14f, 0.1f, 0f, 0f, 0.02f,
            0.1f, 0.1f, 0.1f, 0.3f, 0f, 0.05f, 1.3f, 1f, 64f, 3.8f, 80f,
            600f, 1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.5f, 1f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    // Only the exact old light/dark pair is migrated; independently edited presets are retained.
    private static final float[] LEGACY_REGULAR = {
            0.5f, 1f, 0f, 0.8f, 0.5f, 1.2f, 0f, 0.2f, 0f, 0f, 0.03f,
            1f, 1f, 1f, 1.5f, 0f, 0.6f, 0.6f, 1f, 62f, 3.8f, 80f, 600f,
            1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.2f, 0.6f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };
    private static final float[] LEGACY_DARK = {
            0.8f, 1f, 0f, 1f, 0.2f, 2f, 0.14f, 0.1f, 0f, 0f, 0.02f,
            0.27f, 0.27f, 0.27f, 0.6f, 0f, 0.2f, 1.2f, 1f, 72f, 3.8f, 80f,
            600f, 1f, 0.8f, -0.4f, 0.6f, -0.8f, 1.5f, 1f, 0.8f, 1.15f, 3f,
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    private HeadsUpGlassDefaults() { }

    static float[] regular() { return REGULAR.clone(); }

    static float[] dark() { return DARK.clone(); }

    static boolean isLegacyDefaultPair(float[] regular, float[] dark) {
        return Arrays.equals(regular, LEGACY_REGULAR) && Arrays.equals(dark, LEGACY_DARK);
    }
}
