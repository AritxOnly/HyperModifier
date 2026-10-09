package com.aritxonly.myhypermodifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/** Enabled module entry descriptors, shared by configuration UI and Settings hooks. */
public final class HomeModuleEntryConfig {
    public static final Set<String> MARKERS = Set.of("assets/xposed_init", "META-INF/xposed/java_init.list", "META-INF/xposed/native_init.list");
    private HomeModuleEntryConfig() {}

    public static final class Entry {
        public final String packageName, activity, title, position, category;
        public Entry(String packageName, String activity, String title, String position) {
            this(packageName, activity, title, position, "");
        }
        public Entry(String packageName, String activity, String title, String position, String category) {
            this.packageName = packageName; this.activity = activity; this.title = title;
            this.category = category == null ? "" : category;
            this.position = SettingsHomeHeaderPolicy.normalizePosition(position);
        }
    }

    public static boolean isModule(boolean metadata, Set<String> archiveEntries) {
        return metadata || archiveEntries.stream().anyMatch(MARKERS::contains);
    }

    public static List<Entry> decode(String json) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                JSONObject value = root.optJSONObject(pkg);
                if (value == null || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) continue;
                String activity = value.optString("activity", "");
                if (activity.isBlank()) continue;
                String title = value.optString("title", pkg);
                entries.add(new Entry(pkg, activity, title.isBlank() ? pkg : title, value.optString("position", "middle"), value.optString("category", "")));
            }
        } catch (Exception ignored) { }
        entries.sort(java.util.Comparator.comparing(entry -> entry.packageName));
        return entries;
    }

    public static String update(String json, Entry entry, boolean enabled) {
        JSONObject root;
        try { root = new JSONObject(json); } catch (Exception invalid) { root = new JSONObject(); }
        try {
            if (!enabled) root.remove(entry.packageName);
            else root.put(entry.packageName, new JSONObject().put("activity", entry.activity)
                    .put("title", entry.title).put("position", entry.position).put("category", entry.category));
        } catch (Exception ignored) { }
        return root.toString();
    }
}
