package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.ColorStateList;
import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.SeekBar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.xmlpull.v1.XmlPullParser;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;
import static com.aritxonly.myhypermodifier.ResourceOverrides.*;
import static com.aritxonly.myhypermodifier.ControlCenterAppearance.*;
import static com.aritxonly.myhypermodifier.ReflectiveAccess.*;

/**
 * LSPosed entry point. Domain hook installation lives in focused runtime collaborators.
 */
public final class MyHyperModifier extends XposedModule {
    private static final String TAG = "MyHyperModifier";
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String SYSTEM_UI_PLUGIN = "miui.systemui.plugin";
    private static final String XMSF = "com.xiaomi.xmsf";
    private static final String MILINK = "com.milink.service";
    private static final String MILINK_FUSION_ACTIVITY =
            "com.miui.circulate.world.CirculateWorldActivity";
    private static final String XIAOMI_HEALTH = "com.mi.health";
    private static final String MARKET = "com.xiaomi.market";
    private static final String MI_HOME = "com.xiaomi.smarthome";
    private static final String AMAP = "com.autonavi.minimap";
    private static final String BILIBILI = "tv.danmaku.bili";
    private static final String SPOTIFY = "com.spotify.music";
    private static final String XIAOMI_COMMUNITY = "com.xiaomi.vipaccount";

    // Entry-point lifecycle state; domain-specific state belongs to the extracted collaborators.
    private static final boolean LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED = false;
    private static final AtomicBoolean RESOURCE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLICATION_SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_REFRESH_LISTENER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean EAGER_TARGET_SETTINGS = new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_UI_RUNTIME_ENTRY_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean EARLY_SYSTEM_UI_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean EARLY_PLUGIN_HOOKS_INSTALLED = new AtomicBoolean();

    /**
     * PackageReady is late enough on HyperOS for the plugin factory and several first-frame
     * Control Center classes to have already run. Register SystemUI and MiLink backdrop hooks at
     * PackageLoaded so their View entry point exists before the first relevant Activity frame.
     */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI.equals(packageName) && !SYSTEM_UI_PLUGIN.equals(packageName)
                && !MILINK.equals(packageName) && !XMSF.equals(packageName)) return;
        logSpotifyEntry(packageName, "before settings");
        try {
            connectRemoteSettings();
            logSpotifyEntry(packageName, "after settings");
            if (!ModuleSettings.moduleHooksEnabled || systemUiCompatibilityFor(packageName)) return;
            EAGER_TARGET_SETTINGS.set(SYSTEM_UI.equals(packageName)
                    || SYSTEM_UI_PLUGIN.equals(packageName) || MILINK.equals(packageName) || XMSF.equals(packageName));
            ClassLoader classLoader = param.getDefaultClassLoader();
            if (!installSafely(packageName, "设置加载器", this::installSettingsLoader)) return;
            if (SYSTEM_UI.equals(packageName)) {
                Application application = currentApplication();
                if (application != null) HookDiagnostics.attach(application);
                if (SystemUiCrashGuard.start(param.getApplicationInfo(), ModuleSettings.systemUiRetryGeneration)) {
                    HookDiagnostics.unavailable(packageName, "自动兼容模式", SystemUiCrashGuard.compatibilityDetail());
                    return;
                }
                startEarlySystemUiHooks(classLoader);
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                Application application = currentApplication();
                if (application != null) HookDiagnostics.attach(application);
                if (SystemUiCrashGuard.start(param.getApplicationInfo(), ModuleSettings.systemUiRetryGeneration)) {
                    HookDiagnostics.unavailable(packageName, "自动兼容模式", SystemUiCrashGuard.compatibilityDetail());
                    return;
                }
                startEarlyPluginHooks(classLoader);
            } else if (XMSF.equals(packageName)) {
                installSafely(packageName, "超级岛焦点认证", () -> SuperIslandWhitelistHooks.installXmsf(this, classLoader));
            } else if (MILINK.equals(packageName)) {
                installSafely(packageName, "MiLink 背景模糊", () ->
                        systemUiHooks().installMiLinkFusionBackgroundBlurHook(classLoader));
            }
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Could not install early hooks for " + packageName, throwable);
            HookDiagnostics.failure(packageName, "提前注入", throwable);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI.equals(packageName) && !SYSTEM_UI_PLUGIN.equals(packageName)
                && !MILINK.equals(packageName) && !XIAOMI_HEALTH.equals(packageName)
                && !MARKET.equals(packageName) && !MI_HOME.equals(packageName)
                && !AMAP.equals(packageName) && !XIAOMI_COMMUNITY.equals(packageName)
                && !BILIBILI.equals(packageName) && !SPOTIFY.equals(packageName) && !XMSF.equals(packageName) && !SETTINGS.equals(packageName)) {
            return;
        }

        logSpotifyEntry(packageName, "before settings");
        try {
            connectRemoteSettings();
            logSpotifyEntry(packageName, "after settings");
            if (!ModuleSettings.moduleHooksEnabled || systemUiCompatibilityFor(packageName)) return;
            Application application = currentApplication();
            if (application != null) HookDiagnostics.attach(application);
            if (SYSTEM_UI.equals(packageName) || SYSTEM_UI_PLUGIN.equals(packageName)) {
                if (SystemUiCrashGuard.start(param.getApplicationInfo(), ModuleSettings.systemUiRetryGeneration)) {
                    HookDiagnostics.unavailable(packageName, "自动兼容模式", SystemUiCrashGuard.compatibilityDetail());
                    return;
                }
            }
            boolean eagerTarget = SYSTEM_UI.equals(packageName)
                    || SYSTEM_UI_PLUGIN.equals(packageName) || MILINK.equals(packageName) || XMSF.equals(packageName);
            if (eagerTarget) {
                EAGER_TARGET_SETTINGS.set(true);
            }
            if (!installSafely(packageName, "设置加载器", this::installSettingsLoader)) return;
            if (eagerTarget) {
                // SystemUI and MiLink need the saved blur percentage before their first frame.
                // If Application is not attached yet, the attach hook performs this read.
                ModuleSettings.loadImmediately();
            } else {
                ModuleSettings.ensureLoaded();
            }
            if (SETTINGS.equals(packageName)) {
                installSafely(packageName, "设置主页入口", () -> SettingsHomeHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "关于手机", () -> AboutPhoneHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                return;
            }
            if (XMSF.equals(packageName)) {
                installSafely(packageName, "超级岛焦点认证", () -> SuperIslandWhitelistHooks.installXmsf(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                return;
            }
            if (XIAOMI_HEALTH.equals(packageName)) {
                installSafely(packageName, "小米健康", () -> XiaomiHealthHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (MARKET.equals(packageName)) {
                installSafely(packageName, "小米应用商店", () -> MarketHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (MI_HOME.equals(packageName)) {
                installSafely(packageName, "米家", () -> MiHomeHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (AMAP.equals(packageName)) {
                installSafely(packageName, "高德地图", () -> AmapHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (SPOTIFY.equals(packageName)) {
                installSafely(packageName, "Spotify 媒体按钮", () -> SpotifyHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (BILIBILI.equals(packageName)) {
                installSafely(packageName, "哔哩哔哩", () -> BilibiliHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (XIAOMI_COMMUNITY.equals(packageName)) {
                installSafely(packageName, "小米社区", () -> XiaomiCommunityHooks.install(this, param.getClassLoader()));
                HookDiagnostics.ready(packageName);
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            installSafely(packageName, "资源覆盖", this::installResourceValueHooks);
            if (SYSTEM_UI.equals(packageName) || SYSTEM_UI_PLUGIN.equals(packageName)) {
                installSafely(packageName, "卡片玻璃效果", () -> ShadeCardGlassHooks.install(this));
            }
            if (SYSTEM_UI.equals(packageName)) {
                if (SYSTEM_UI.equals(packageName)) installSafely(packageName, "超级岛白名单", () ->
                        SuperIslandWhitelistHooks.installSystemUi(this, param.getClassLoader()));
                installSafely(packageName, "Spotify 媒体卡片", () -> SpotifyMediaCardHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "手势提示线", () -> GestureHandleHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "悬浮通知迷你栏", () -> HeadsUpMiniBarHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "悬浮通知底部间距", () -> HeadsUpBottomMarginHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "息屏时钟字重", () -> AodClockWeightHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "锁屏时钟冒号", () -> LockscreenClockColonHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "绕过 HyperMusicCover 时钟避让", () -> HyperMusicCoverClockHooks.install(this, param.getClassLoader()));
                installSafely(packageName, "锁屏时钟渐进避让通知", () -> LockscreenClockAvoidanceHooks.install(this, param.getClassLoader()));
                if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
                    installSafely(packageName, "锁屏密码背景模糊", () ->
                            lockscreenHooks().installLockscreenBouncerBlurCompatHook(param.getClassLoader()));
                    installSafely(packageName, "锁屏密码壁纸", () ->
                            lockscreenHooks().installLockscreenPasswordWallpaperRatioHook(param.getClassLoader()));
                }
                installSafely(packageName, "插件加载器", () ->
                        pluginHooks().installSystemUiPluginLoaderFactoryHook(param.getClassLoader()));
                installSafely(packageName, "插件类加载器", () ->
                        pluginHooks().installSystemUiPluginClassLoaderResolver());
                installSafely(packageName, "SystemUI", () ->
                        systemUiHooks().installSystemUiHooks(param.getClassLoader()));
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                installSafely(packageName, "控制中心圆角", () ->
                        pluginHooks().installSystemUiPluginCornerHooks(param.getClassLoader()));
                installSafely(packageName, "全局背景模糊", () ->
                        systemUiHooks().installGlobalBackgroundBlurHook());
            } else if (MILINK.equals(packageName)) {
                // Fusion Device Center runs in MiLink's isolated :ui process. Both renderer
                // variants pass through BlurControllerImpl.setBlurRatio.
                installSafely(packageName, "MiLink 背景模糊", () ->
                        systemUiHooks().installMiLinkFusionBackgroundBlurHook(param.getClassLoader()));
                installSafely(packageName, "MiLink 卡片", () ->
                        pluginHooks().installMiLinkFusionCardHooks(param.getClassLoader()));
            }
            HookDiagnostics.ready(packageName);
            log(Log.INFO, TAG, "Installed for " + packageName);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Could not install hooks for " + packageName, throwable);
            HookDiagnostics.failure(packageName, "注入入口", throwable);
        }
    }

    /**
     * Reads the module's own SharedPreferences through LSPosed rather than Android package IPC.
     * Target packages cannot reliably discover our package/provider on current HyperOS builds.
     */
    /** Emit before the gates as well: an enabled module can still skip runtime installation. */
    private static void logSpotifyEntry(String packageName, String stage) {
        if (!SPOTIFY.equals(packageName) && !SYSTEM_UI.equals(packageName)) return;
        Log.i(TAG, "Spotify hook bootstrap: package=" + packageName + ", stage=" + stage
                + ", build=" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE
                + ", enabled=" + ModuleSettings.moduleHooksEnabled
                + ", compatibility=" + systemUiCompatibilityFor(packageName)
                + ", settings=" + ModuleSettings.loadStatus());
    }

    private void connectRemoteSettings() {
        ModuleSettings.setRemotePreferences(getRemotePreferences("modifier_settings"));
    }

    private static boolean systemUiCompatibilityFor(String packageName) {
        return (SYSTEM_UI.equals(packageName) || SYSTEM_UI_PLUGIN.equals(packageName))
                && (ModuleSettings.systemUiCompatibilityMode
                    || SystemUiCrashGuard.isAutomaticCompatibilityActive());
    }

    private static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            return application instanceof Application ? (Application) application : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface HookInstall {
        void run() throws Throwable;
    }

    private static boolean installSafely(String packageName, String feature, HookInstall install) {
        try {
            install.run();
            return true;
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError || error instanceof ThreadDeath) throw (Error) error;
            HookDiagnostics.failure(packageName, feature, error);
            return false;
        }
    }

    private void startEarlySystemUiHooks(ClassLoader classLoader) {
        if (!ModuleSettings.moduleHooksEnabled || systemUiCompatibilityFor(SYSTEM_UI)) return;
        if (classLoader == null || !EARLY_SYSTEM_UI_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        installSafely(SYSTEM_UI, "超级岛白名单", () -> SuperIslandWhitelistHooks.installSystemUi(this, classLoader));
        installSafely(SYSTEM_UI, "卡片玻璃效果", () -> ShadeCardGlassHooks.install(this));
        installSafely(SYSTEM_UI, "手势提示线", () -> GestureHandleHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "悬浮通知迷你栏", () -> HeadsUpMiniBarHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "悬浮通知底部间距", () -> HeadsUpBottomMarginHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "息屏时钟字重", () -> AodClockWeightHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "锁屏时钟冒号", () -> LockscreenClockColonHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "绕过 HyperMusicCover 时钟避让", () -> HyperMusicCoverClockHooks.install(this, classLoader));
        installSafely(SYSTEM_UI, "锁屏时钟渐进避让通知", () -> LockscreenClockAvoidanceHooks.install(this, classLoader));
        if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
            installSafely(SYSTEM_UI, "锁屏密码背景模糊", () ->
                    lockscreenHooks().installLockscreenBouncerBlurCompatHook(classLoader));
            installSafely(SYSTEM_UI, "锁屏密码壁纸", () ->
                    lockscreenHooks().installLockscreenPasswordWallpaperRatioHook(classLoader));
        }
        installSafely(SYSTEM_UI, "插件加载器", () ->
                pluginHooks().installSystemUiPluginLoaderFactoryHook(classLoader));
        installSafely(SYSTEM_UI, "插件类加载器", () ->
                pluginHooks().installSystemUiPluginClassLoaderResolver());
        installSafely(SYSTEM_UI, "SystemUI", () -> systemUiHooks().installSystemUiHooks(classLoader));
        HookDiagnostics.ready(SYSTEM_UI);
    }

    private void startEarlyPluginHooks(ClassLoader classLoader) {
        if (!ModuleSettings.moduleHooksEnabled || systemUiCompatibilityFor(SYSTEM_UI_PLUGIN)) return;
        if (classLoader == null || !EARLY_PLUGIN_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        installSafely(SYSTEM_UI_PLUGIN, "卡片玻璃效果", () -> ShadeCardGlassHooks.install(this));
        installSafely(SYSTEM_UI_PLUGIN, "控制中心圆角", () ->
                pluginHooks().installSystemUiPluginCornerHooks(classLoader));
        installSafely(SYSTEM_UI_PLUGIN, "全局背景模糊", () ->
                systemUiHooks().installGlobalBackgroundBlurHook());
        HookDiagnostics.ready(SYSTEM_UI_PLUGIN);
    }

    /** Reads saved appearance options after the target process receives its base context. */
    private void installSettingsLoader() throws Throwable {
        if (!SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) return;
        if (SETTINGS_REFRESH_LISTENER_INSTALLED.compareAndSet(false, true)) {
            ModuleSettings.onLoaded(RuntimeRefreshRegistry::refreshViewsAfterSettingsLoad);
        }
        try {
            hook(ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class))
                .setId("settings-loader")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Context context = (Context) chain.getArg(0);
                    if (!EAGER_TARGET_SETTINGS.get()) {
                        markLoaded(context);
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof Application) HookDiagnostics.attach(context);
                        return result;
                    }
                    Object result = chain.proceed();
                    logSystemUiRuntimeEntry(context);
                    ModuleSettings.loadImmediately(context);
                    if (chain.getThisObject() instanceof Application) {
                        HookDiagnostics.attach(context);
                    }
                    return result;
                });
        } catch (Throwable error) {
            SETTINGS_HOOK_INSTALLED.set(false);
            throw error;
        }

        // PackageReady is delivered after Application.attach() on some HyperOS builds.  onCreate
        // is still ahead of Control Center view inflation and is the reliable settings hand-off.
        if (APPLICATION_SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) {
            try {
                hook(Application.class.getDeclaredMethod("onCreate"))
                    .setId("application-settings-loader")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context application = (Context) chain.getThisObject();
                        HookDiagnostics.attach(application);
                        if (!EAGER_TARGET_SETTINGS.get()) {
                            markLoaded(application);
                            return chain.proceed();
                        }
                        Object result = chain.proceed();
                        logSystemUiRuntimeEntry(application);
                        ModuleSettings.loadImmediately(application);
                        return result;
                    });
            } catch (Throwable error) {
                APPLICATION_SETTINGS_HOOK_INSTALLED.set(false);
                Application application = currentApplication();
                HookDiagnostics.failure(application == null ? "unknown" : application.getPackageName(),
                        "Application 设置刷新", error);
            }
        }
    }

    /** A one-shot proof that this exact APK is injected into the SystemUI process. */
    private static void logSystemUiRuntimeEntry(Context context) {
        if (context == null || !SYSTEM_UI.equals(context.getPackageName())
                || !SYSTEM_UI_RUNTIME_ENTRY_LOGGED.compareAndSet(false, true)) {
            return;
        }
        Log.e(TAG, "SystemUI runtime entry confirmed; password hook registration is active");
    }

    private void installResourceValueHooks() throws NoSuchMethodException {
        if (!RESOURCE_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        hook(Resources.class.getDeclaredMethod("getDimension", int.class))
                .setId("dimension-float")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementDimension(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? replacement : result;
                });

        hook(Resources.class.getDeclaredMethod("getDimensionPixelSize", int.class))
                .setId("dimension-pixel-size")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementDimension(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? Math.round(replacement) : result;
                });

        hook(Resources.class.getDeclaredMethod("getDimensionPixelOffset", int.class))
                .setId("dimension-pixel-offset")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementDimension(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? (int) replacement.floatValue() : result;
                });

        hook(Resources.class.getDeclaredMethod("getColor", int.class))
                .setId("super-island-pull-bar-color")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Integer replacement = replacementColor(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? replacement : result;
                });

        hook(Resources.class.getDeclaredMethod("getColor", int.class, Resources.Theme.class))
                .setId("super-island-pull-bar-color-themed")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Integer replacement = replacementColor(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? replacement : result;
                });

        hook(Resources.class.getDeclaredMethod("getColorStateList", int.class))
                .setId("super-island-pull-bar-color-state-list")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Integer replacement = replacementColor(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? ColorStateList.valueOf(replacement) : result;
                });

        hook(Resources.class.getDeclaredMethod("getColorStateList", int.class, Resources.Theme.class))
                .setId("super-island-pull-bar-color-state-list-themed")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Integer replacement = replacementColor(
                            (Resources) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? ColorStateList.valueOf(replacement) : result;
                });

        // XML drawables resolve their <corners android:radius="@dimen/..."> through a
        // TypedArray rather than Resources#getDimension*.  The Fusion Device Center uses this
        // path for its normal, active and expanded card backgrounds.
        hook(TypedArray.class.getDeclaredMethod("getDimension", int.class, float.class))
                .setId("typed-array-dimension-float")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementTypedArrayDimension(
                            (TypedArray) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? replacement : result;
                });

        hook(TypedArray.class.getDeclaredMethod("getDimensionPixelSize", int.class, int.class))
                .setId("typed-array-dimension-pixel-size")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementTypedArrayDimension(
                            (TypedArray) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? Math.round(replacement) : result;
                });

        hook(TypedArray.class.getDeclaredMethod("getDimensionPixelOffset", int.class, int.class))
                .setId("typed-array-dimension-pixel-offset")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Float replacement = replacementTypedArrayDimension(
                            (TypedArray) chain.getThisObject(), (Integer) chain.getArg(0));
                    return replacement != null ? (int) replacement.floatValue() : result;
                });

    }

    private SystemUiRuntimeHooks systemUiHooks() {
        return new SystemUiRuntimeHooks(this);
    }

    private LockscreenHooks lockscreenHooks() {
        return new LockscreenHooks(this);
    }

    private PluginHooks pluginHooks() {
        return new PluginHooks(this);
    }

}
