package com.aritxonly.myhypermodifier;

/** Pure storage calculations used by the About phone card layout. */
public final class AboutPhoneAppearancePolicy {
    private AboutPhoneAppearancePolicy() {}

    /** Only remove the expansion annotation, keeping the stock used/total capacity value. */
    public static String storageLabel(String value) {
        return value == null ? "" : value.replaceFirst("\\s*[（(][^（）()]*扩容[^（）()]*[）)]\\s*$", "").trim();
    }

    public static float storageFraction(long total, long available) {
        if (total <= 0) return 0f;
        return (float) Math.max(0d, Math.min(1d, 1d - (double) available / total));
    }

}
