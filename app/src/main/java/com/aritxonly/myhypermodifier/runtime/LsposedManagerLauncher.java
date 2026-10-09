package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Uses the active framework's manager category, matching its official shortcut. */
public final class LsposedManagerLauncher {
    private LsposedManagerLauncher() {}

    static String managerPackage(String frameworkName) {
        if (frameworkName == null) return null;
        String name = frameworkName.toLowerCase(java.util.Locale.ROOT);
        if (name.contains("vector")) return "org.matrix.vector.manager";
        if (name.contains("lsposed")) return "org.lsposed.manager";
        return null;
    }

    public static boolean open(Context context, String frameworkName) {
        android.content.pm.PackageManager packages = context.getPackageManager();
        String manager = managerPackage(frameworkName);
        if (manager == null) {
            Toast.makeText(context, "无法识别框架的管理器入口，请使用管理器桌面快捷方式。", Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent standalone = packages.getLaunchIntentForPackage(manager);
        if (launch(context, standalone)) return true;
        if (usesRootManagerBridge(manager)) {
            Intent request = new Intent().setClassName("com.aritxonly.myhypermodifier",
                    "com.aritxonly.myhypermodifier.ManagerLaunchActivity");
            if (launch(context, request)) return true;
            Toast.makeText(context, "无法进入管理器启动页，请确认 HyperModifier 已安装。", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Launch the existing shortcut through the system so the framework's exact
        // component, categories, data and flags are retained. Never guess a Shell Activity.
        try {
            android.content.pm.LauncherApps launcher = context.getSystemService(android.content.pm.LauncherApps.class);
            if (launcher != null) {
                android.content.pm.LauncherApps.ShortcutQuery query = new android.content.pm.LauncherApps.ShortcutQuery()
                        .setPackage("com.android.shell")
                        .setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER
                                | android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC);
                java.util.List<android.content.pm.ShortcutInfo> shortcuts = launcher.getShortcuts(query, android.os.Process.myUserHandle());
                if (shortcuts != null) for (android.content.pm.ShortcutInfo shortcut : shortcuts) {
                    if (!shortcut.isEnabled()) continue;
                    boolean matches = isManagerShortcut(shortcut.getId(), shortcut.getShortLabel(), manager);
                    Intent[] intents = shortcut.getIntents();
                    if (intents != null) for (Intent intent : intents) {
                        if (intent.hasCategory(manager + ".LAUNCH_MANAGER")) matches = true;
                    }
                    if (!matches) continue;
                    launcher.startShortcut(shortcut, null, null);
                    return true;
                }
            }
        } catch (RuntimeException unavailable) {
            android.util.Log.w("MyHyperModifier", "Cannot launch official manager shortcut", unavailable);
        }
        Toast.makeText(context, "无法打开管理器，请使用框架自身的启动入口。", Toast.LENGTH_SHORT).show();
        return false;
    }

    static boolean usesRootManagerBridge(String manager) {
        return "org.lsposed.manager".equals(manager);
    }

    static boolean isManagerShortcut(String id, CharSequence label, String manager) {
        String token = "org.matrix.vector.manager".equals(manager) ? "vector" : "lsposed";
        return (id != null && id.toLowerCase(java.util.Locale.ROOT).contains(token))
                || (label != null && label.toString().equalsIgnoreCase(token));
    }

    private static boolean launch(Context context, Intent intent) {
        if (intent == null) return false;
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (RuntimeException unavailable) { return false; }
    }
}
