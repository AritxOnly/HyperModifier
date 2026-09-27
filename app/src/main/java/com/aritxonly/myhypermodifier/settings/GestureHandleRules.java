package com.aritxonly.myhypermodifier;

import org.json.JSONObject;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/** Shared, version-independent preference contract for the settings app and SystemUI. */
public final class GestureHandleRules {
    public static final String SHOW = "show";
    public static final String HIDE = "hide";
    public static final String IMMERSIVE = "immersive";
    public static final String SYSTEM = "system";

    private GestureHandleRules() {}

    public static Map<String, String> decode(String serialized) {
        TreeMap<String, String> rules = new TreeMap<>();
        if (serialized == null || serialized.isEmpty()) return Collections.emptyMap();
        try {
            JSONObject object = new JSONObject(serialized);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String mode = object.optString(key);
                if (!key.isEmpty() && validMode(mode)) rules.put(key, mode);
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(rules);
    }

    public static String encode(Map<String, String> rules) {
        TreeMap<String, String> valid = new TreeMap<>();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            if (!entry.getKey().isEmpty() && validMode(entry.getValue())) {
                valid.put(entry.getKey(), entry.getValue());
            }
        }
        return new JSONObject(valid).toString();
    }

    private static boolean validMode(String value) {
        return SHOW.equals(value) || HIDE.equals(value) || IMMERSIVE.equals(value) || SYSTEM.equals(value);
    }
}
