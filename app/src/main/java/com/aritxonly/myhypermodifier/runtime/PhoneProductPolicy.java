package com.aritxonly.myhypermodifier;

import java.util.Locale;

/** Uses hardware market names, never the user-editable device name. */
public final class PhoneProductPolicy {
    private PhoneProductPolicy() {}

    public static String currentAsset() {
        try {
            java.lang.reflect.Method get = Class.forName("android.os.SystemProperties")
                    .getDeclaredMethod("get", String.class);
            return asset((String) get.invoke(null, "ro.product.marketname"),
                    (String) get.invoke(null, "ro.product.vendor.marketname"),
                    (String) get.invoke(null, "ro.product.odm.marketname"),
                    android.os.Build.MODEL, android.os.Build.DEVICE);
        } catch (Throwable ignored) {
            return asset(android.os.Build.MODEL, android.os.Build.DEVICE);
        }
    }

    public static String selectedAsset(String source, String detected) {
        return "auto".equals(source) ? detected : "custom".equals(source) ? null : asset(source);
    }

    public static String asset(String... names) {
        if (names == null) return null;
        // Prefer a named Leica edition over a generic Ultra name from another property.
        for (String name : names) {
            String key = normalize(name);
            if (key.equals("xiaomi17ultraleica") || key.equals("xiaomi17ultrabyleica")
                    || key.equals("xiaomi17ultra徕卡版")) return "xiaomi-17-ultra-byleica";
        }
        for (String name : names) {
            switch (normalize(name)) {
                case "xiaomi17": case "pudding": return "xiaomi-17";
                case "xiaomi17pro": return "xiaomi-17-pro";
                case "xiaomi17promax": return "xiaomi-17-pro-max";
                case "xiaomi17ultra": return "xiaomi-17-ultra";
                case "redmik90": return "redmi-k90";
                case "redmik90promax": return "redmi-k90-pro-max";
                case "xiaomi18fold": return "xiaomi-18-fold";
                case "xiaomi18pro": return "xiaomi-18-pro";
                case "xiaomi18promax": return "xiaomi-18-pro-max";
                case "redmik100pro": return "redmi-k100-pro";
                case "redmik100promax": return "redmi-k100-pro-max";
            }
        }
        return null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace("小米", "xiaomi")
                .replaceAll("[\\s_-]", "");
    }
}
