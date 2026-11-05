package com.aritxonly.myhypermodifier;

/** Text-based Mi Home selection state that survives obfuscated IDs across releases. */
final class MiHomeNavigationPolicy {
    private MiHomeNavigationPolicy() {}

    static boolean isSelectionHeader(CharSequence value) {
        if (value == null) return false;
        String text = value.toString();
        if (text.isEmpty()) return false;
        if ((text.startsWith("已选择") || text.startsWith("已选"))
                && text.indexOf('项') > 0) {
            return true;
        }
        if (!containsIgnoreCase(text, "selected")) return false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) return true;
        }
        return false;
    }

    private static boolean containsIgnoreCase(String text, String expected) {
        int max = text.length() - expected.length();
        for (int start = 0; start <= max; start++) {
            if (text.regionMatches(true, start, expected, 0, expected.length())) return true;
        }
        return false;
    }
}
