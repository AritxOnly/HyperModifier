package com.aritxonly.myhypermodifier;

import android.os.Build;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Selects the converged large-screen behavior from immutable hardware identity. */
public final class LargeScreenDevicePolicy {
    public static final int AUTO = 0;
    public static final int PHONE = 1;
    public static final int FOLDABLE = 2;
    public static final int TABLET = 3;

    private static final Set<String> TABLET_DEVICE_CODES = new HashSet<>(Arrays.asList(
            "cappu",
            "clover",
            "pipa",
            "liuqin",
            "yudi",
            "sheng",
            "uke",
            "muyu",
            "piano"
    ));

    private static final Set<String> FOLDABLE_DEVICE_CODES = new HashSet<>(Arrays.asList(
            "lhasa",
            "cetus",
            "zizhan",
            "babylon",
            "goku",
            "ruyi"
    ));
    private static final int DETECTED_MODE = detectMode(Build.DEVICE, Build.MODEL);

    private LargeScreenDevicePolicy() {}

    public static boolean isLargeScreenDevice() {
        return currentMode() != PHONE;
    }

    public static boolean isLargeScreenDevice(String device, String model) {
        return detectMode(device, model) != PHONE;
    }

    public static int detectedMode() {
        return DETECTED_MODE;
    }

    public static int effectiveMode(int override) {
        return resolveMode(override, DETECTED_MODE);
    }

    public static int currentMode() {
        return effectiveMode(ModuleSettings.deviceModeOverride);
    }

    public static int resolveMode(int override, int detectedMode) {
        int normalized = normalizeOverride(override);
        int detected = normalizeOverride(detectedMode);
        return normalized == AUTO ? detected : normalized;
    }

    public static boolean usesLargeScreenLayout(int override) {
        return effectiveMode(override) != PHONE;
    }

    public static int normalizeOverride(int override) {
        return override >= AUTO && override <= TABLET ? override : AUTO;
    }

    public static int detectMode(String device, String model) {
        String code = normalize(device);
        if (FOLDABLE_DEVICE_CODES.contains(code)) return FOLDABLE;
        if (TABLET_DEVICE_CODES.contains(code)) return TABLET;

        String name = normalize(model);
        boolean foldable = name.contains("mixfold")
                || name.contains("mixflip")
                || ((name.startsWith("xiaomi") || name.startsWith("redmi"))
                    && (name.contains("fold") || name.contains("flip")));
        if (foldable) return FOLDABLE;

        boolean tablet = name.startsWith("xiaomipad")
                || name.startsWith("redmipad")
                || name.startsWith("mipad")
                || name.contains("tablet");
        return tablet ? TABLET : PHONE;
    }

    private static String normalize(String value) {
        return value == null ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
    }
}
