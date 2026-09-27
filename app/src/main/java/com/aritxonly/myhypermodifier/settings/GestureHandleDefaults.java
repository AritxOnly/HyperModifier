package com.aritxonly.myhypermodifier;

import java.util.Set;

/** Rule-based defaults also apply to apps installed after the preset was selected. */
public final class GestureHandleDefaults {
    private GestureHandleDefaults() {}

    public static String mode(boolean modulePreset, String packageName, boolean systemApp,
            Set<String> configuredScope) {
        if (!modulePreset || packageName == null) return null;
        if (systemApp || packageName.startsWith("com.aritxonly.")
                || ModuleScopePackage.PACKAGES.contains(packageName)
                || configuredScope.contains(packageName)) return "hide";
        return "immersive";
    }
}
