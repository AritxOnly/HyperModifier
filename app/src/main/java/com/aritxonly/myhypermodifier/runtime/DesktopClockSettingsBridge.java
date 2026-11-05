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
    private static final String FOLD_DUAL_PAGE = "fold_portrait_dual_page_enabled";
    private static final String FOLD_ICON = "fold_portrait_icon_percent";
    private static final String SEARCH = "desktop_search_4x7_enabled";
    private static final String CLOCK_MATERIAL = "desktop_clock_material";
    private static final String CLOCK_RADIUS = "desktop_clock_blur_radius";
    private static final String CLOCK_TINT = "desktop_clock_tint_strength";
    private static final String STACK = "deadliner_widget_stack_enabled";
    private static final String EXPERIMENT = "deadliner_clock_glass_experiment";
    private static final String GLASS = "deadliner_widget_glass_enabled";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static SharedPreferences preferences;
    private static Context context;
    private static int startupAttempts;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static boolean unlockReceiverRegistered;
    private static volatile boolean diagnosticPublished, diagnosticFoldEnabled;
    static String diagnosticSummary() {
        return "系统界面已加载诊断版（UID " + android.os.Process.myUid() + "）。"
                + (diagnosticPublished ? "已发送双页" + (diagnosticFoldEnabled ? "开启" : "关闭") + "设置。尚不能确认桌面已收到。"
                : "尚未发送设置，正在等待模块配置。");
    }
    private static final Runnable PUBLISH = new Runnable() {
        @Override public void run() {
            if (context == null || preferences == null) return;
            try {
                boolean enabled = preferences.getBoolean("module_hooks_enabled", true)
                        && preferences.getBoolean(KEY, false);
                boolean moduleEnabled = preferences.getBoolean("module_hooks_enabled", true);
                int material = moduleEnabled ? preferences.getInt(CLOCK_MATERIAL, 0) : 0;
                int radius = Math.max(2, Math.min(40, preferences.getInt(CLOCK_RADIUS, 12)));
                int tint = Math.max(0, Math.min(80, preferences.getInt(CLOCK_TINT, 36)));
                Intent intent = new Intent("com.android.systemui.fsgesture")
                        .setPackage("com.miui.home")
                        .putExtra("mhm_desktop_clock_control", true)
                        .putExtra("mhm_status_reader_uid", android.os.Process.myUid())
                        .putExtra("mhm_desktop_clock_enabled", enabled)
                        .putExtra("mhm_clock_tuning_v1", radius | (tint << 6))
                        .putExtra("mhm_clock_blur", material == 1)
                        .putExtra("mhm_clock_soft_glass", material == 2)
                        .putExtra("mhm_fold_portrait_dual_page", moduleEnabled
                                && !preferences.getBoolean("systemui_compatibility_mode", false)
                                && preferences.getBoolean(FOLD_DUAL_PAGE, false))
                        .putExtra("mhm_fold_icon_percent", moduleEnabled && !preferences.getBoolean("systemui_compatibility_mode", false)
                                ? preferences.getInt(FOLD_ICON, 100) : 100)
                        .putExtra("mhm_desktop_search_4x7", moduleEnabled && preferences.getBoolean(SEARCH, false))
                        .putExtra("mhm_android_widget_stack", moduleEnabled && preferences.getBoolean(STACK, false))
                        .putExtra("mhm_android_widget_glass", moduleEnabled && preferences.getBoolean(GLASS, false))
                        .putExtra("mhm_deadliner_clock_glass", moduleEnabled && preferences.getBoolean(EXPERIMENT, false));
                boolean sentFold = intent.getBooleanExtra("mhm_fold_portrait_dual_page", false);
                // Packed i32 words use the verified scalar schema ABI; no owned Rust strings.
                putRules(intent, "mhm_widget_rules_size", "mhm_widget_rule_", AndroidWidgetGlassPolicy.KEY);
                putRules(intent, "mhm_clock_glass_rules_size", "mhm_clock_glass_rule_", AndroidWidgetGlassPolicy.CLOCK_STYLE_KEY);
                putPacked(intent, "mhm_glass_styles_size", "mhm_glass_style_", WidgetGlassStyles.encode(WidgetGlassStyles.parse(preferences.getString(WidgetGlassStyles.KEY, ""))));
                context.sendBroadcast(intent, null,
                        BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
                diagnosticFoldEnabled = sentFold;
                diagnosticPublished = true;
            } catch (Throwable error) {
                Log.w("MyHyperModifier", "Desktop clock settings carrier unavailable", error);
            }
            // A bounded startup window; native launcher also restores its persisted cache.
            // Afterwards refresh only on relevant settings changes or user unlock, never a minute poll.
            if (startupAttempts++ < 10) HANDLER.postDelayed(this, 2000L);
        }
    };

    private static void putRules(Intent intent, String sizeKey, String wordPrefix, String preferenceKey) {
        String rules = AndroidWidgetGlassPolicy.encode(AndroidWidgetGlassPolicy.targets(
                preferences.getString(preferenceKey, AndroidWidgetGlassPolicy.DEFAULT_TARGETS)));
        putPacked(intent, sizeKey, wordPrefix, rules);
    }

    private static void putPacked(Intent intent, String sizeKey, String wordPrefix, String rules) {
        intent.putExtra(sizeKey, rules.length());
        for (int offset = 0; offset < rules.length(); offset += 4) {
            int word = 0;
            for (int i = 0; i < 4 && offset + i < rules.length(); i++) word |= rules.charAt(offset + i) << (i * 8);
            intent.putExtra(wordPrefix + offset / 4, word);
        }
    }

    private DesktopClockSettingsBridge() {}

    static void setPreferences(SharedPreferences value) {
        HANDLER.post(() -> {
            if (preferences == value) return;
            if (preferences != null && listener != null)
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
            preferences = value;
            if (value != null) {
                listener = (prefs, key) -> {
                    if (FOLD_DUAL_PAGE.equals(key) || FOLD_ICON.equals(key) || "systemui_compatibility_mode".equals(key) || CLOCK_RADIUS.equals(key) || CLOCK_TINT.equals(key) || CLOCK_MATERIAL.equals(key) || SEARCH.equals(key) || KEY.equals(key) || STACK.equals(key) || GLASS.equals(key) || EXPERIMENT.equals(key) || AndroidWidgetGlassPolicy.KEY.equals(key) || AndroidWidgetGlassPolicy.CLOCK_STYLE_KEY.equals(key) || WidgetGlassStyles.KEY.equals(key) || "module_hooks_enabled".equals(key))
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
            DesktopWidgetInventory.attach(context);
            if (!unlockReceiverRegistered) {
                context.registerReceiver(new android.content.BroadcastReceiver() {
                    @Override public void onReceive(Context ignored, Intent intent) { publishSoon(); }
                }, new android.content.IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED);
                unlockReceiverRegistered = true;
            }
            startupAttempts = 0;
            publishSoon();
        });
    }

    private static void publishSoon() {
        HANDLER.removeCallbacks(PUBLISH);
        if (context != null && preferences != null) HANDLER.post(PUBLISH);
    }
}
