package com.aritxonly.myhypermodifier;

import java.util.Set;

/** Rule-based defaults also apply to apps installed after the preset was selected. */
public final class GestureHandleDefaults {
    private GestureHandleDefaults() {}

    public static String mode(String preset, String packageName, boolean systemApp,
            Set<String> configuredScope) {
        // Explicit global presets do not depend on a foreground task. SystemUI can report a null
        // top activity during boot and transitions; stock fallback made "show" ineffective.
        if (GestureHandlePresets.SHOW.equals(preset)) return "show";
        if (GestureHandlePresets.HIDE.equals(preset)) return "hide";
        if (GestureHandlePresets.IMMERSIVE.equals(preset)) return "immersive";
        if (packageName == null) return null;
        if (!GestureHandlePresets.MODULE.equals(preset)) return null;
        String added = GestureHandlePresets.MODULE_APPS.get(packageName);
        if (added != null) return added;
        if (systemApp || packageName.startsWith("com.aritxonly.")
                || ModuleScopePackage.PACKAGES.contains(packageName)
                || configuredScope.contains(packageName)) return "hide";
        return "immersive";
    }
}
