package com.aritxonly.myhypermodifier;

/** Shared reference recipe from notification_glass_params_normal (Sep 24 SystemUI build). */
final class ShadeCardGlassPolicy {
    static final int COUNT = 42;
    static final int MAX_GLASS_BLUR_RADIUS = 100;
    // The custom base is limited to 100px; retain stock radii when scaling without override.
    private static final int MAX_EFFECTIVE_GLASS_BLUR_RADIUS = 2000;
    private static final float[] BASE = {
            .67f, .16f, .09f, 0f, .24f, 1.4f, -.02f, .30f, .6f, 1f,
            .03f, 1f, 1f, 1f, .10f, .2f, .3f, 1f, 1f, 72f, 3.8f, 80f,
            800f, 1.2f, 1f, -.4f, .6f, -.8f, 1.4f, .7f, .8f, 1.15f,
            4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    private ShadeCardGlassPolicy() {}

    static float[] defaults() { return BASE.clone(); }

    public static float normalize(float value, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(maximum, value)) : fallback;
    }

    static int scaleBackgroundRadius(int original, int percent) {
        return Math.max(0, Math.min(500, Math.round(original * Math.max(0, Math.min(200, percent)) / 100f)));
    }

    static int[] effectiveGlassRadii(int small, int big, boolean override, int radius, int percent) {
        int baseSmall = override ? Math.max(0, Math.min(MAX_GLASS_BLUR_RADIUS, radius)) : small;
        int baseBig = override ? Math.max(0, Math.min(MAX_GLASS_BLUR_RADIUS, radius)) : big;
        return new int[]{scaleGlassRadius(baseSmall, percent), scaleGlassRadius(baseBig, percent)};
    }

    private static int scaleGlassRadius(int radius, int percent) {
        return Math.max(0, Math.min(MAX_EFFECTIVE_GLASS_BLUR_RADIUS,
                Math.round(radius * Math.max(0, Math.min(200, percent)) / 100f)));
    }

    static float[] tune(float[] original, float[] configured) {
        if (original == null || configured == null
                || original.length != COUNT || configured.length != COUNT) return null;
        boolean nonzero = false;
        for (int i = 0; i < COUNT; i++) {
            if (!Float.isFinite(original[i]) || !Float.isFinite(configured[i])) return null;
            nonzero |= original[i] != 0f;
        }
        // A zero recipe clears the effect. Never turn a clear call into a visible material.
        if (!nonzero) return null;
        float[] result = original.clone();
        for (int i = 0; i < COUNT; i++) {
            float delta = configured[i] - BASE[i];
            if (delta == 0f) continue;
            float value = original[i] + delta;
            if (!Float.isFinite(value)) return null;
            boolean signed = i == 6 || i == 8 || i == 9 || (i >= 25 && i <= 27) || i == 34;
            result[i] = signed ? value : Math.max(0f, value);
        }
        return result;
    }

    /** Pure stack classification; View/row identity is checked separately by the hook. */
    static int callKind(String[] owners) {
        return callKind(owners, false);
    }

    static int callKind(String[] owners, boolean controlAncestor) {
        boolean control = false;
        boolean controlMaterial = false;
        boolean notification = false;
        boolean keyguard = false;
        for (String owner : owners) {
            if (owner.contains("HeadsUp") || owner.contains("ShadeBlendBlurController")) return 0;
            keyguard |= owner.contains("OnKeyguard") || owner.contains("FullAod")
                    || owner.startsWith("com.android.keyguard.")
                    || owner.startsWith("com.miui.keyguard.");
            control |= owner.startsWith("miui.systemui.controlcenter.");
            controlMaterial |= owner.startsWith("miui.systemui.util.MiBackgroundStyle");
            notification |= owner.startsWith("com.android.systemui.statusbar.notification.");
        }
        return control || (controlMaterial && controlAncestor) ? 2 : notification && !keyguard ? 1 : 0;
    }

    /** HyperChanger's elementMaterialOverride caller routing for native Glass radii.
     * Deliberately separate from recipe/card routing: BlurProvider owns the sampling buffers.
     */
    static int radiusCallKind(String[] owners) {
        boolean notification = false;
        boolean control = false;
        for (String owner : owners) {
            if (owner.contains("HeadsUp")) return 0;
            control |= owner.startsWith("miui.systemui.controlcenter.");
            notification |= owner.startsWith("com.android.systemui.statusbar.notification.")
                    || owner.startsWith("com.android.systemui.shade.")
                    || owner.startsWith("com.miui.systemui.shade.");
        }
        return control ? 2 : notification ? 1 : 0;
    }
}
