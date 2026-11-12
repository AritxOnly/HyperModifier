package com.aritxonly.myhypermodifier;

/** Allocation-free geometry and eligibility checks, independent of ROM classes. */
final class CoverAdaptationPolicy {
    static int appWidth(int height) { return (int) ((long) height * 9 / 16); }

    static boolean fits(int width, int height, int densityDpi) {
        if (width <= 0 || height <= width || densityDpi <= 0) return false;
        int rail = width - appWidth(height);
        // The remaining width must accommodate a 48dp target plus 8dp side margins.
        return appWidth(height) >= (long) densityDpi * 240 / 160
                && rail >= (long) densityDpi * 64 / 160;
    }

    static boolean application(String name) {
        if (name == null || name.equals("android") || name.equals(CoverAdaptationContract.PACKAGE)) return false;
        return !name.equals("com.android.systemui") && !name.equals("com.miui.home")
                && !name.contains("permissioncontroller") && !name.contains("packageinstaller")
                && !name.equals("com.android.incallui") && !name.equals("com.android.emergency");
    }

    private CoverAdaptationPolicy() {}
}
