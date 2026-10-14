package com.aritxonly.myhypermodifier;

import android.content.SharedPreferences;

/** Input callbacks only read this local snapshot; preference reads happen on change notifications. */
final class KeyShortcutConfig {
    static final String SCOPE_KEY = "gesture_handle_scope_packages";
    static final class Snapshot {
        final String volumePower;
        final String doublePower;
        final boolean voiceScoped;
        Snapshot(boolean enabled, String volumePower, String doublePower, boolean voiceScoped) {
            this.voiceScoped = voiceScoped;
            this.volumePower = enabled ? KeyShortcutActions.normalize(volumePower) : KeyShortcutActions.STOCK;
            this.doublePower = enabled ? KeyShortcutActions.normalize(doublePower) : KeyShortcutActions.STOCK;
        }
        boolean allows(String action) {
            return voiceScoped && (KeyShortcutActions.MEMORY.equals(action) || KeyShortcutActions.SCREEN.equals(action))
                    && (action.equals(volumePower) || action.equals(doublePower));
        }
    }
    private volatile Snapshot snapshot = new Snapshot(false, null, null, false);
    // Keep a strong reference: Android preference implementations may hold weak listeners.
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    KeyShortcutConfig(SharedPreferences prefs) {
        listener = (updated, key) -> {
            if (key == null || "module_hooks_enabled".equals(key)
                    || SCOPE_KEY.equals(key) || KeyShortcutActions.VOLUME_POWER_KEY.equals(key) || KeyShortcutActions.DOUBLE_POWER_KEY.equals(key)) {
                reload(updated);
            }
        };
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(listener);
            reload(prefs);
        }
    }
    Snapshot current() { return snapshot; }
    private void reload(SharedPreferences prefs) {
        try {
            snapshot = new Snapshot(prefs.getBoolean("module_hooks_enabled", true),
                    prefs.getString(KeyShortcutActions.VOLUME_POWER_KEY, KeyShortcutActions.STOCK),
                    prefs.getString(KeyShortcutActions.DOUBLE_POWER_KEY, KeyShortcutActions.STOCK),
                    java.util.Arrays.asList(prefs.getString(SCOPE_KEY, "").split("\\R"))
                            .contains(ModuleScopePackage.VOICE_ASSIST));
        } catch (RuntimeException unavailable) {
            snapshot = new Snapshot(false, null, null, false);
        }
    }
}
