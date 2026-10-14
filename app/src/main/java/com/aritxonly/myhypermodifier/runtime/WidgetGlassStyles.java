package com.aritxonly.myhypermodifier;

import java.util.Map;
import java.util.TreeMap;

/** Explicit styles override migrated glass selections: instance > provider > app. */
public final class WidgetGlassStyles {
    public static final String KEY = "android_widget_glass_styles";
    public static final int OFF = 0, TINTED = 1, CLEAR = 2, CUSTOM = 3;
    public static final int MAX_BYTES = 16384;
    private WidgetGlassStyles() {}
    public static Map<String, Integer> parse(String encoded) {
        Map<String, Integer> result = new TreeMap<>();
        if (encoded == null || encoded.length() > MAX_BYTES) return result;
        for (String line : encoded.split("\n")) {
            String[] pair = line.split("\t", -1);
            if (pair.length != 2 || !pair[1].matches("[0-3]")) continue;
            int mode = Integer.parseInt(pair[1]);
            if (validTarget(pair[0]) && (mode != CUSTOM || !pair[0].contains("/"))) result.put(pair[0], mode);
        }
        return result;
    }
    private static boolean validTarget(String target) {
        String[] parts = target.split("#", -1);
        if (parts.length > 2 || !parts[0].matches("[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+(?:/[A-Za-z0-9_$]+(?:\\.[A-Za-z0-9_$]+)+)?")) return false;
        if (parts.length == 1) return true;
        if (!parts[0].contains("/") || !parts[1].matches("[1-9][0-9]*")) return false;
        try { return Integer.parseInt(parts[1]) > 0; } catch (NumberFormatException error) { return false; }
    }
    public static String encode(Map<String, Integer> rules) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(rules).entrySet()) {
            if (!validTarget(entry.getKey()) || entry.getValue() < OFF || entry.getValue() > CUSTOM
                    || (entry.getValue() == CUSTOM && entry.getKey().contains("/"))) throw new IllegalArgumentException("Invalid widget style");
            if (result.length() > 0) result.append('\n');
            result.append(entry.getKey()).append('\t').append(entry.getValue());
        }
        return result.toString();
    }
    public static int resolve(String styles, String glass, boolean clockEnabled, String clockRules,
            String pkg, String className, int id) {
        if (pkg == null || pkg.isEmpty() || className == null || className.isEmpty()) return OFF;
        if (className.startsWith(".")) className = pkg + className;
        else if (!className.contains(".")) className = pkg + "." + className;
        return resolve(parse(styles), AndroidWidgetGlassPolicy.targets(glass), clockEnabled,
                AndroidWidgetGlassPolicy.targets(clockRules), pkg, className, id);
    }
    static int resolve(Map<String, Integer> rules, java.util.Set<String> glass, boolean clockEnabled,
            java.util.Set<String> clockRules, String pkg, String className, int id) {
        String component = pkg + "/" + className;
        Integer mode = id > 0 ? rules.get(component + "#" + id) : null;
        if (mode == null) mode = rules.get(component);
        if (mode == null) mode = rules.get(pkg);
        if (mode != null && mode != CUSTOM) return mode;
        if (!AndroidWidgetGlassPolicy.matches(glass, pkg, className)) return OFF;
        return clockEnabled && AndroidWidgetGlassPolicy.matches(clockRules, pkg, className) ? CLEAR : TINTED;
    }
    static final class Rules {
        final String stylesSource, glassSource, clockSource;
        final Map<String, Integer> styles;
        final java.util.Set<String> glass, clock;
        Rules(String stylesSource, String glassSource, String clockSource) {
            this.stylesSource = stylesSource; this.glassSource = glassSource; this.clockSource = clockSource;
            styles = parse(stylesSource); glass = AndroidWidgetGlassPolicy.targets(glassSource);
            clock = AndroidWidgetGlassPolicy.targets(clockSource);
        }
        boolean matchesSources(String styles, String glass, String clock) {
            return java.util.Objects.equals(stylesSource, styles) && java.util.Objects.equals(glassSource, glass)
                    && java.util.Objects.equals(clockSource, clock);
        }
    }

    public static Map<String, Integer> setApplication(String encoded, String pkg, int mode) {
        Map<String, Integer> rules = parse(encoded);
        rules.keySet().removeIf(key -> key.equals(pkg) || (key.startsWith(pkg + "/") && !key.contains("#")));
        rules.put(pkg, mode);
        return rules;
    }
}
