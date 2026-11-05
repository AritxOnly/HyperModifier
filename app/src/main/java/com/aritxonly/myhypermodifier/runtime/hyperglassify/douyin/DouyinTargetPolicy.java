package com.aritxonly.myhypermodifier;

/** Process-local setting selector shared by the Douyin main app and Lite adapters. */
final class DouyinTargetPolicy {
    private static volatile String packageName = ModuleScopePackage.DOUYIN;

    private DouyinTargetPolicy() {}

    static void configure(String targetPackage) {
        packageName = ModuleScopePackage.DOUYIN_LITE.equals(targetPackage)
                ? ModuleScopePackage.DOUYIN_LITE : ModuleScopePackage.DOUYIN;
    }

    static String packageName() {
        return packageName;
    }

    static boolean enabled() {
        boolean setting = ModuleScopePackage.DOUYIN_LITE.equals(packageName)
                ? ModuleSettings.douyinLiteFloatingNavigationEnabled
                : ModuleSettings.douyinFloatingNavigationEnabled;
        return ModuleSettings.moduleHooksEnabled && setting;
    }
}
