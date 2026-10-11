package com.aritxonly.myhypermodifier;

import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Publishes LSPosed-owned settings from SystemUI into the JNI-free launcher runtime. */
final class DesktopClockSettingsBridge {
    private static final String KEY = "hide_desktop_clock_leading_zero";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static SharedPreferences preferences;
    private static Context context;
    private static int startupAttempts;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static final Runnable PUBLISH = new Runnable() {
        @Override public void run() {
            if (context == null || preferences == null) return;
            try {
                boolean enabled = preferences.getBoolean("module_hooks_enabled", true)
                        && preferences.getBoolean(KEY, false);
                Intent intent = new Intent("com.android.systemui.fsgesture")
                        .setPackage("com.miui.home")
                        .putExtra("mhm_desktop_clock_control", true)
                        .putExtra("mhm_desktop_clock_enabled", enabled);
                context.sendBroadcast(intent, null,
                        BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
            } catch (Throwable error) {
                Log.w("MyHyperModifier", "Desktop clock settings carrier unavailable", error);
            }
            // Brief startup retry window, then one broadcast per minute to handle a
            // restarted native launcher. No per-frame work or filesystem polling.
            HANDLER.postDelayed(this, startupAttempts++ < 10 ? 2000L : 60000L);
        }
    };

    private DesktopClockSettingsBridge() {}

    static void setPreferences(SharedPreferences value) {
        HANDLER.post(() -> {
            if (preferences == value) return;
            if (preferences != null && listener != null)
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
            preferences = value;
            if (value != null) {
                listener = (prefs, key) -> {
                    if (KEY.equals(key) || "module_hooks_enabled".equals(key))
                        HANDLER.post(DesktopClockSettingsBridge::publishSoon);
                };
                value.registerOnSharedPreferenceChangeListener(listener);
            }
            publishSoon();
        });
    }

    static void attach(Context value) {
        if (value == null || !"com.android.systemui".equals(value.getPackageName())) return;
        HANDLER.post(() -> {
            context = value.getApplicationContext() != null ? value.getApplicationContext() : value;
            startupAttempts = 0;
            publishSoon();
        });
    }

    private static void publishSoon() {
        HANDLER.removeCallbacks(PUBLISH);
        if (context != null && preferences != null) HANDLER.post(PUBLISH);
    }
}
