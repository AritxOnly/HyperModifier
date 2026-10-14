package com.aritxonly.myhypermodifier;

import android.content.ComponentName;
import java.util.Set;
import java.util.TreeSet;

/** Default-all rules: * enables future providers; -package / -component excludes them. */
public final class AndroidWidgetGlassPolicy {
    public static final String CLOCK_STYLE_KEY = "android_widget_clock_glass_rules";
    public static final String KEY = "android_widget_glass_rules_v2";
    public static final String LEGACY_TARGET = "com.aritxonly.deadliner/com.aritxonly.deadliner.widgets.glance.ui.CompactTaskGlanceWidgetReceiver";
    public static final String DEFAULT_TARGETS = "*";
    public static final int MAX_BYTES = 16384;
    private AndroidWidgetGlassPolicy() {}
    public static Set<String> targets(String encoded) {
        Set<String> result = new TreeSet<>();
        if (encoded == null || encoded.length() > MAX_BYTES) return result;
        for (String line : encoded.split("\n")) {
            if (line.equals("*") || line.matches("-?[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+(?:/[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+)?")) result.add(line);
        }
        return result;
    }
    public static String encode(Set<String> targets) { return String.join("\n", new TreeSet<>(targets)); }
    public static boolean matches(String encoded, ComponentName provider) {
        if (provider == null) return false;
        return matches(encoded, provider.getPackageName(), provider.getClassName());
    }
    public static boolean matches(String encoded, String packageName, String className) {
        if (packageName == null || className == null || packageName.isEmpty() || className.isEmpty()) return false;
        if (className.startsWith(".")) className = packageName + className;
        else if (className.indexOf('.') < 0) className = packageName + "." + className;
        return matches(targets(encoded), packageName, className);
    }
    static boolean matches(Set<String> rules, String packageName, String className) {
        String component = packageName + "/" + className;
        return !rules.contains("-" + packageName) && !rules.contains("-" + component)
                && (rules.contains("*") || rules.contains(packageName) || rules.contains(component));
    }
    public static boolean includesPackage(String encoded, String packageName) {
        Set<String> rules = targets(encoded);
        if (packageName == null || packageName.isEmpty() || rules.contains("-" + packageName)) return false;
        if (rules.contains("*")) return true;
        for (String rule : rules) if (rule.equals(packageName) || rule.startsWith(packageName + "/")) return true;
        return false;
    }
}
