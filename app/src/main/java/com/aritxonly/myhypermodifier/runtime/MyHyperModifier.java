package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Outline;
import android.content.res.ColorStateList;
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
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String SYSTEM_UI_PLUGIN = "miui.systemui.plugin";
    private static final String MILINK = "com.milink.service";
    private static final String MILINK_FUSION_ACTIVITY =
            "com.miui.circulate.world.CirculateWorldActivity";
    private static final String XIAOMI_HEALTH = "com.mi.health";
    private static final String MARKET = "com.xiaomi.market";
    private static final String MI_HOME = "com.xiaomi.smarthome";
    private static final String AMAP = "com.autonavi.minimap";
    private static final String XIAOMI_COMMUNITY = "com.xiaomi.vipaccount";

    // Entry-point lifecycle state; domain-specific state belongs to the extracted collaborators.
    private static final boolean LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED = false;
    private static final AtomicBoolean RESOURCE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLICATION_SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_REFRESH_LISTENER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean EAGER_TARGET_SETTINGS = new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_UI_RUNTIME_ENTRY_LOGGED = new AtomicBoolean();

    /**
     * PackageReady is late enough on HyperOS for the plugin factory and several first-frame
     * Control Center classes to have already run. Register SystemUI and MiLink backdrop hooks at
     * PackageLoaded so their View entry point exists before the first relevant Activity frame.
     */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI.equals(packageName) && !SYSTEM_UI_PLUGIN.equals(packageName)
                && !MILINK.equals(packageName)) return;
        try {
            connectRemoteSettings();
            EAGER_TARGET_SETTINGS.set(SYSTEM_UI.equals(packageName)
                    || SYSTEM_UI_PLUGIN.equals(packageName) || MILINK.equals(packageName));
            installSettingsLoader();
            ClassLoader classLoader = param.getDefaultClassLoader();
            if (SYSTEM_UI.equals(packageName)) {
                // This is intentionally before every plugin-factory, media and heads-up hook.
                // A missing cosmetic class must never suppress the password-page control point.
                if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
                    lockscreenHooks().installLockscreenBouncerBlurCompatHook(classLoader);
                    lockscreenHooks().installLockscreenPasswordWallpaperRatioHook(classLoader);
                }
                pluginHooks().installSystemUiPluginLoaderFactoryHook(classLoader);
                pluginHooks().installSystemUiPluginClassLoaderResolver();
                systemUiHooks().installSystemUiHooks(classLoader);
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                pluginHooks().installSystemUiPluginCornerHooks(classLoader);
                systemUiHooks().installGlobalBackgroundBlurHook();
            } else if (MILINK.equals(packageName)) {
                systemUiHooks().installMiLinkFusionBackgroundBlurHook(classLoader);
            }
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Could not install early hooks for " + packageName, throwable);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI.equals(packageName) && !SYSTEM_UI_PLUGIN.equals(packageName)
                && !MILINK.equals(packageName) && !XIAOMI_HEALTH.equals(packageName)
                && !MARKET.equals(packageName) && !MI_HOME.equals(packageName)
                && !AMAP.equals(packageName) && !XIAOMI_COMMUNITY.equals(packageName)) {
            return;
        }

        try {
            connectRemoteSettings();
            boolean eagerTarget = SYSTEM_UI.equals(packageName)
                    || SYSTEM_UI_PLUGIN.equals(packageName) || MILINK.equals(packageName);
            if (eagerTarget) {
                EAGER_TARGET_SETTINGS.set(true);
            }
            installSettingsLoader();
            if (eagerTarget) {
                // SystemUI and MiLink need the saved blur percentage before their first frame.
                // If Application is not attached yet, the attach hook performs this read.
                ModuleSettings.loadImmediately();
            } else {
                ModuleSettings.ensureLoaded();
            }
            if (XIAOMI_HEALTH.equals(packageName)) {
                XiaomiHealthHooks.install(this, param.getClassLoader());
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (MARKET.equals(packageName)) {
                MarketHooks.install(this, param.getClassLoader());
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (MI_HOME.equals(packageName)) {
                MiHomeHooks.install(this, param.getClassLoader());
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (AMAP.equals(packageName)) {
                AmapHooks.install(this, param.getClassLoader());
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            if (XIAOMI_COMMUNITY.equals(packageName)) {
                XiaomiCommunityHooks.install(this, param.getClassLoader());
                log(Log.INFO, TAG, "Installed for " + packageName);
                return;
            }
            installResourceValueHooks();
            if (SYSTEM_UI.equals(packageName)) {
                if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
                    lockscreenHooks().installLockscreenBouncerBlurCompatHook(param.getClassLoader());
                    lockscreenHooks().installLockscreenPasswordWallpaperRatioHook(param.getClassLoader());
                }
                pluginHooks().installSystemUiPluginLoaderFactoryHook(param.getClassLoader());
                pluginHooks().installSystemUiPluginClassLoaderResolver();
                systemUiHooks().installSystemUiHooks(param.getClassLoader());
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                pluginHooks().installSystemUiPluginCornerHooks(param.getClassLoader());
                systemUiHooks().installGlobalBackgroundBlurHook();
            } else if (MILINK.equals(packageName)) {
                // Fusion Device Center runs in MiLink's isolated :ui process. Both renderer
                // variants pass through BlurControllerImpl.setBlurRatio.
                systemUiHooks().installMiLinkFusionBackgroundBlurHook(param.getClassLoader());
                pluginHooks().installMiLinkFusionCardHooks(param.getClassLoader());
            }
            log(Log.INFO, TAG, "Installed for " + packageName);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Could not install hooks for " + packageName, throwable);
        }
    }

    /**
     * Reads the module's own SharedPreferences through LSPosed rather than Android package IPC.
     * Target packages cannot reliably discover our package/provider on current HyperOS builds.
     */
    private void connectRemoteSettings() {
        ModuleSettings.setRemotePreferences(getRemotePreferences("modifier_settings"));
    }

    /** Reads saved appearance options after the target process receives its base context. */
    private void installSettingsLoader() throws NoSuchMethodException {
        if (!SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) return;
        if (SETTINGS_REFRESH_LISTENER_INSTALLED.compareAndSet(false, true)) {
            ModuleSettings.onLoaded(RuntimeRefreshRegistry::refreshViewsAfterSettingsLoad);
        }
        hook(ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class))
                .setId("settings-loader")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Context context = (Context) chain.getArg(0);
                    if (!EAGER_TARGET_SETTINGS.get()) {
                        markLoaded(context);
                        return chain.proceed();
                    }
                    Object result = chain.proceed();
                    logSystemUiRuntimeEntry(context);
                    ModuleSettings.loadImmediately(context);
                    return result;
                });

        // PackageReady is delivered after Application.attach() on some HyperOS builds.  onCreate
        // is still ahead of Control Center view inflation and is the reliable settings hand-off.
        if (APPLICATION_SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) {
            hook(Application.class.getDeclaredMethod("onCreate"))
                    .setId("application-settings-loader")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Context application = (Context) chain.getThisObject();
                        if (!EAGER_TARGET_SETTINGS.get()) {
                            markLoaded(application);
                            return chain.proceed();
                        }
                        Object result = chain.proceed();
                        logSystemUiRuntimeEntry(application);
                        ModuleSettings.loadImmediately(application);
                        return result;
                    });
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
