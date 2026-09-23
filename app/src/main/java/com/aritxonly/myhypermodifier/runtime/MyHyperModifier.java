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
 * Runtime equivalent of the resource/XML/smali changes verified against the APKs in reference/.
 * API 102 intentionally has no resource-hook API, so dimensions are intercepted at their Java
 * call sites and ConstraintSets are adjusted just after the SystemUI XML has been loaded.
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

    private static final int XML_MEDIA_ISLAND_NORMAL = 0x7f180018;
    private static final int XML_MEDIA_NORMAL = 0x7f180019;
    private static final String PLAYER_ISLAND_CONSTRAINT_LAYOUT =
            "com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout";
    private static final String HEADS_UP_GLASS_EFFECT =
            "com.android.systemui.statusbar.notification.style.vieweffect."
                    + "HeadsUpNotificationGlassEffect";
    private static final String HEADS_UP_GLASS_DARK_EFFECT =
            "com.android.systemui.statusbar.notification.style.vieweffect."
                    + "HeadsUpNotificationGlassDarkEffect";
    private static final String KEYGUARD_PIN_VIEW = "com.android.keyguard.KeyguardPINView";
    private static final String KEYGUARD_PASSWORD_VIEW = "com.android.keyguard.KeyguardPasswordView";
    private static final String MI_GLASS_COMPAT = "com.miui.systemui.util.MiGlassCompat";
    private static final String MI_BLUR_COMPAT = "com.miui.systemui.util.MiBlurCompat";
    private static final int KEYGUARD_BOUNCER_CONTAINER_ID = 0x7f0b05df;
    // All password-background experiments are paused until the actual rendered surface can be
    // verified. Keep the exploration code dormant so it cannot affect the lockscreen.
    private static final boolean LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED = false;
    private static final String KEYGUARD_WALLPAPER_BLUR_RATIO_LAMBDA =
            "com.android.keyguard.blur.MiuiKeyguardBlurInteractor$wallpaperBlurRatio$2$1";
    private static final String KEYGUARD_BOUNCER_BLUR_RADIUS_LAMBDA =
            "com.android.keyguard.blur.MiuiKeyguardBlurInteractor$bouncerMiBlurRadius$2$1";
    private static final String KEYGUARD_BLUR_COLLECTOR =
            "com.android.keyguard.blur.MiuiKeyguardBlurInteractor$startUpdatingWallpaperRatio$1$1";
    /** Temporary strict reproduction of the verified smali replacement: 0x00000000 / 0.0f. */
    private static final float STRICT_KEYGUARD_WALLPAPER_RATIO = 0f;
    private static final String LOCKSCREEN_PIN_GLASS_TAG =
            "myhypermodifier.lockscreen.pin.soft-glass";
    /**
     * The stock PIN hit target is a short, wide rectangle.  Let the visual glass disc exceed its
     * short edge by this much on every side, while retaining the stock hit target and text
     * placement.  The result is a less cramped disc with deliberate breathing room around digits.
     */
    private static final int LOCKSCREEN_PIN_GLASS_BLUR_RADIUS = 36;
    private static final int LOCKSCREEN_PIN_GLASS_MATERIAL_TYPE = 1;
    private static final int LOCKSCREEN_PIN_GLASS_BLEND_MODE = 101;
    private static final float[] LOCKSCREEN_PIN_GLASS_PARAMETERS = new float[] {
            0.67f, 0.16f, 0.09f, 0f, 0.14f, 1.4f, -0.02f, 0.3f, 0.6f, 1f,
            0.03f, 1f, 1f, 1f, 0.1f, 0.2f, 0.3f, 1f, 1f, 72f, 3.8f, 80f,
            800f, 1.2f, 1f, -0.4f, 0.6f, -0.8f, 1.4f, 0.7f, 0.8f, 1.15f,
            4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    };

    private static final AtomicBoolean RESOURCE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_UI_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_DRAWABLE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_CLASS_LOADER_RESOLVER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_FACTORY_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_CARD_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLICATION_SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_REFRESH_LISTENER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean EAGER_TARGET_SETTINGS = new AtomicBoolean();
    private static final AtomicBoolean LOCKSCREEN_NOTIFICATION_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOCKSCREEN_FINGERPRINT_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOCKSCREEN_CREDENTIAL_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOCKSCREEN_PASSWORD_WALLPAPER_RATIO_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean LOCKSCREEN_BOUNCER_BLUR_COMPAT_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_UI_RUNTIME_ENTRY_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean GLOBAL_BACKGROUND_BLUR_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_BLUR_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_OWNER_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_OWNER_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_BLUR_LOGGED = new AtomicBoolean();
    private static final Set<Method> INSTALLED_SYSTEM_UI_METHOD_HOOKS =
            ConcurrentHashMap.newKeySet();
    private static final Set<Method> INSTALLED_PLUGIN_METHOD_HOOKS =
            ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> INSTALLING_LOADED_CLASS_HOOK = new ThreadLocal<>();
    private static final ThreadLocal<ControlCenterSurface> INFLATING_PLUGIN_DRAWABLE = new ThreadLocal<>();
    private static final Map<View, List<Method>> CONTROL_CENTER_REFRESH_METHODS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Drawable, ControlCenterSurface> PENDING_PLUGIN_DRAWABLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, PendingMediaConstraint> PENDING_MEDIA_CONSTRAINTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> MILINK_FUSION_BACKGROUND_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

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
                    installLockscreenBouncerBlurCompatHook(classLoader);
                    installLockscreenPasswordWallpaperRatioHook(classLoader);
                }
                installSystemUiPluginLoaderFactoryHook(classLoader);
                installSystemUiPluginClassLoaderResolver();
                installSystemUiHooks(classLoader);
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                installSystemUiPluginCornerHooks(classLoader);
                installGlobalBackgroundBlurHook();
            } else if (MILINK.equals(packageName)) {
                installMiLinkFusionBackgroundBlurHook(classLoader);
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
                    installLockscreenBouncerBlurCompatHook(param.getClassLoader());
                    installLockscreenPasswordWallpaperRatioHook(param.getClassLoader());
                }
                installSystemUiPluginLoaderFactoryHook(param.getClassLoader());
                installSystemUiPluginClassLoaderResolver();
                installSystemUiHooks(param.getClassLoader());
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                installSystemUiPluginCornerHooks(param.getClassLoader());
                installGlobalBackgroundBlurHook();
            } else if (MILINK.equals(packageName)) {
                // Fusion Device Center runs in MiLink's isolated :ui process. Both renderer
                // variants pass through BlurControllerImpl.setBlurRatio.
                installMiLinkFusionBackgroundBlurHook(param.getClassLoader());
                installMiLinkFusionCardHooks(param.getClassLoader());
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
            ModuleSettings.onLoaded(MyHyperModifier::refreshViewsAfterSettingsLoad);
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

    private void installSystemUiHooks(ClassLoader classLoader) throws Throwable {
        if (!SYSTEM_UI_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            // This class is present in the target build and is the password-page control point.
            // Register it before optional media/island classes so their startup timing can never
            // suppress the credential hook.
            if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
                installLockscreenBouncerBlurCompatHook(classLoader);
                installLockscreenPasswordWallpaperRatioHook(classLoader);
            }
            installSystemUiHookForLoadedClass(
                    "androidx.constraintlayout.widget.ConstraintSet",
                    Class.forName("androidx.constraintlayout.widget.ConstraintSet", false, classLoader));
            installSystemUiHookForLoadedClass(
                    "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                    Class.forName("com.android.systemui.statusbar.notification.mediacontrol."
                            + "MiuiMediaViewControllerImpl", false, classLoader));
            installSystemUiHookForLoadedClass(PLAYER_ISLAND_CONSTRAINT_LAYOUT,
                    Class.forName(PLAYER_ISLAND_CONSTRAINT_LAYOUT, false, classLoader));
            installHeadsUpGlassEffectHooks(classLoader);
            installGlobalBackgroundBlurHook();
            installLockscreenNotificationHooks(classLoader);
            installLockscreenFingerprintHooks(classLoader);
            installLockscreenCredentialHooks(classLoader);
            StatusBarNetworkType.install(this, classLoader);
        } catch (Throwable throwable) {
            // Do not consume the one-shot marker if a boot-time class is not visible yet. The
            // class-load observer can then install its hook when the real class appears.
            SYSTEM_UI_HOOKS_INSTALLED.set(false);
            throw throwable;
        }
    }

    /** Installs only methods declared by a class that has already been returned by loadClass. */
    private void installSystemUiHookForLoadedClass(String className, Class<?> loadedClass) {
        try {
            if ("androidx.constraintlayout.widget.ConstraintSet".equals(className)) {
                hookMediaConstraintSetLoad(loadedClass.getDeclaredMethod(
                        "load", Context.class, int.class));
                for (Method method : loadedClass.getDeclaredMethods()) {
                    if ("applyTo".equals(method.getName()) && method.getParameterCount() == 1) {
                        hookMediaConstraintSetApply(method);
                    }
                }
                return;
            }
            if (PLAYER_ISLAND_CONSTRAINT_LAYOUT.equals(className)) {
                hookMediaIslandMeasuredHeight(loadedClass.getDeclaredMethod("calSizeByDensity"));
                return;
            }
            if (!"com.android.systemui.statusbar.notification.mediacontrol."
                    .concat("MiuiMediaViewControllerImpl").equals(className)) {
                return;
            }
            hookFullAodStateChanged(loadedClass.getDeclaredMethod(
                    "onFullAodStateChanged", boolean.class));
            for (Method method : loadedClass.getDeclaredMethods()) {
                if ("setSeamless".equals(method.getName()) && method.getParameterCount() == 1) {
                    hookSeamlessVisibility(method);
                } else if ("attach".equals(method.getName()) && method.getParameterCount() == 1
                        && "com.android.systemui.statusbar.notification.mediacontrol."
                        .concat("MiuiMediaViewHolder").equals(
                                method.getParameterTypes()[0].getName())) {
                    hookMediaAttach(method);
                }
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "SystemUI class hook unavailable: " + className, throwable);
        }
    }

    private boolean claimSystemUiMethod(Method method) {
        return INSTALLED_SYSTEM_UI_METHOD_HOOKS.add(method);
    }

    /**
     * SystemUI owns the blur, blend and material-type setup.  Apply the custom payload only after
     * its effect method returns, so this hook changes no other heads-up notification behavior.
     */
    private void installHeadsUpGlassEffectHooks(ClassLoader classLoader) {
        installHeadsUpGlassEffectHook(classLoader, HEADS_UP_GLASS_EFFECT, false);
        installHeadsUpGlassEffectHook(classLoader, HEADS_UP_GLASS_DARK_EFFECT, true);
    }

    private void installHeadsUpGlassEffectHook(
            ClassLoader classLoader, String className, boolean dark) {
        try {
            Class<?> effectClass = Class.forName(className, false, classLoader);
            Method apply = effectClass.getDeclaredMethod("apply", Object.class, Context.class);
            if (!claimSystemUiMethod(apply)) return;
            hook(apply)
                    .setId(dark ? "heads-up-glass-dark-parameters"
                            : "heads-up-glass-parameters")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        if (headsUpGlassParametersEnabled || headsUpBackgroundBlurRadiusEnabled) {
                            applyHeadsUpCustomizations(chain.getArg(0), dark);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Heads-up glass hook unavailable: " + className, throwable);
        }
    }

    private static void applyHeadsUpCustomizations(Object row, boolean dark) {
        if (row == null) return;
        try {
            Object injector = row.getClass().getMethod("getInjector").invoke(row);
            if (injector == null) return;
            Object background = injector.getClass().getMethod("getBackgroundNormal").invoke(injector);
            if (!(background instanceof View)) return;
            if (headsUpGlassParametersEnabled) {
                Method setMiGlass = View.class.getMethod("setMiGlass", float[].class);
                setMiGlass.invoke(background, (Object) headsUpGlassParameters(dark));
            }
            if (headsUpBackgroundBlurRadiusEnabled) {
                Method setMiBackgroundBlurRadius = View.class.getMethod(
                        "setMiBackgroundBlurRadius", int.class);
                setMiBackgroundBlurRadius.invoke(background, headsUpBackgroundBlurRadius);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Adjacent HyperOS versions can rename these view bridges; stock rendering stays intact.
        }
    }

    /**
     * Scale SystemUI shade and Control Center blur calls. Xiaomi uses different stock radii on
     * each surface, so retaining the original argument and multiplying it keeps their relative
     * appearance stable across device builds.
     */
    private void installGlobalBackgroundBlurHook() {
        if (!GLOBAL_BACKGROUND_BLUR_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Method method = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            hook(method)
                    .setId("global-shade-background-blur")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object receiver = chain.getThisObject();
                        Object argument = chain.getArg(0);
                        if (!(receiver instanceof View) || !(argument instanceof Integer)
                                || !isGlobalBackgroundBlurTarget((View) receiver)) {
                            return chain.proceed();
                        }
                        int percent = globalBackgroundBlurPercent;
                        if (percent == 100) return chain.proceed();
                        int original = (Integer) argument;
                        int adjusted = Math.max(0, Math.min(500,
                                Math.round(original * percent / 100f)));
                        return chain.proceedWith(chain.getThisObject(), new Object[]{adjusted});
                    });
        } catch (Throwable throwable) {
            GLOBAL_BACKGROUND_BLUR_HOOK_INSTALLED.set(false);
            Log.w(TAG, "Global background blur hook unavailable", throwable);
        }
    }

    private static boolean isGlobalBackgroundBlurTarget(View view) {
        String className = view.getClass().getName();
        // The global control is deliberately limited to shade/Control Center. Keyguard owns
        // several independent blur effects (including the two bottom shortcuts), which must not
        // inherit this slider.
        boolean keyguardView = className.startsWith("com.android.keyguard.")
                || className.startsWith("com.miui.keyguard.");
        // A notification row (including heads-up) has its own glass background and is deliberately
        // excluded. Its independent radius setting remains the only customization for that view.
        if (className.contains("NotificationBackgroundView")
                || className.contains("MirrorBlur")
                || className.contains("mirrorBlurProvider")) {
            return false;
        }
        String idName = null;
        try {
            if (view.getId() != View.NO_ID) {
                idName = view.getResources().getResourceEntryName(view.getId());
            }
        } catch (Resources.NotFoundException ignored) {
            // Some plugin-provided views have generated IDs with no resource entry in this scope.
        }
        if ("progress_bg".equals(idName) || "volume_column_slider".equals(idName)
                || "volume_column_slider_bg_glass".equals(idName)
                || "volume_column_slider_bg_blend".equals(idName)) {
            return false;
        }
        boolean controlCenter = false;
        boolean notificationShade = false;
        boolean shadeBlurProvider = false;
        boolean keyguardCall = keyguardView;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.startsWith("com.android.keyguard.")
                    || owner.startsWith("com.miui.keyguard.")) {
                keyguardCall = true;
            }
            if (owner.startsWith("miui.systemui.controlcenter.")) controlCenter = true;
            if (owner.startsWith("com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider")) {
                shadeBlurProvider = true;
            }
            if (owner.startsWith("com.android.systemui.shade.")
                    || owner.startsWith("com.miui.systemui.shade.")) {
                notificationShade = true;
            }
            if (owner.contains("HeadsUpNotificationGlassEffect")) return false;
        }
        // Control Center can be opened over the lock screen, so it legitimately retains a
        // Keyguard caller below its own frames. Its explicit controller is authoritative.
        if (controlCenter) return true;
        if (keyguardCall) return false;
        return shadeBlurProvider || notificationShade;
    }

    /** Limit MiLink's shared blur controller to the device-center Activity backdrop. */
    private static boolean isMiLinkFusionDeviceCenterView(View view) {
        synchronized (MILINK_FUSION_BACKGROUND_VIEWS) {
            if (MILINK_FUSION_BACKGROUND_VIEWS.containsKey(view)) return true;
        }
        Context context = view.getContext();
        for (int depth = 0; context != null && depth < 12; depth++) {
            if (MILINK_FUSION_ACTIVITY.equals(context.getClass().getName())) {
                return true;
            }
            if (!(context instanceof ContextWrapper)) return false;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return false;
            context = next;
        }
        return false;
    }

    private static void rememberMiLinkFusionDeviceCenterView(Activity activity) {
        if (activity == null || !MILINK_FUSION_ACTIVITY.equals(activity.getClass().getName())) {
            return;
        }
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor != null) {
                synchronized (MILINK_FUSION_BACKGROUND_VIEWS) {
                    MILINK_FUSION_BACKGROUND_VIEWS.put(decor, Boolean.TRUE);
                }
                if (MILINK_FUSION_BACKGROUND_OWNER_LOGGED.compareAndSet(false, true)) {
                    Log.i(TAG, "MiLink Fusion Device Center backdrop identified");
                }
            }
        } catch (RuntimeException ignored) {
            // The activity may be closing while its blur animation finishes.
        }
    }

    /**
     * MiLink's device center renders through either View.setMiBackgroundBlurRadius or the
     * SurfaceControl fallback. Both paths converge on BlurControllerImpl.setBlurRatio.
     */
    private void installMiLinkFusionBackgroundBlurHook(ClassLoader classLoader) {
        installMiLinkFusionBackgroundOwnerHook(classLoader);
        if (!MILINK_FUSION_BACKGROUND_BLUR_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> blurController = Class.forName(
                    "com.miui.circulate.world.utils.BlurUtils$BlurControllerImpl",
                    false, classLoader);
            Method setBlurRatio = blurController.getDeclaredMethod("setBlurRatio", float.class);
            hook(setBlurRatio)
                    .setId("milink-fusion-background-blur")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object target = declaredFieldValue(chain.getThisObject(), "b");
                        Object ratio = chain.getArg(0);
                        if (!(target instanceof View) || !(ratio instanceof Float)
                                || !isMiLinkFusionDeviceCenterView((View) target)) {
                            return chain.proceed();
                        }
                        int percent = globalBackgroundBlurPercent;
                        if (percent == 100) return chain.proceed();
                        float original = (Float) ratio;
                        float adjusted = Math.max(0f, original * percent / 100f);
                        if (MILINK_FUSION_BACKGROUND_BLUR_LOGGED.compareAndSet(false, true)) {
                            Log.i(TAG, "Fusion Device Center blur " + original + " -> " + adjusted
                                    + " (global " + percent + "% )");
                        }
                        return chain.proceed(new Object[]{adjusted});
                    });

            Log.i(TAG, "MiLink Fusion Device Center blur ratio hook installed");
        } catch (Throwable throwable) {
            MILINK_FUSION_BACKGROUND_BLUR_HOOK_INSTALLED.set(false);
            Log.w(TAG, "MiLink Fusion Device Center blur hook unavailable", throwable);
        }
    }

    /** BaseActivity.onStart prepares the backdrop in both MiLink 18.1 and 18.2. */
    private void installMiLinkFusionBackgroundOwnerHook(ClassLoader classLoader) {
        if (!MILINK_FUSION_BACKGROUND_OWNER_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> baseActivity = Class.forName(
                    "com.miui.circulate.world.base.BaseActivity", false, classLoader);
            Method onStart = baseActivity.getDeclaredMethod("onStart");
            hook(onStart)
                    .setId("milink-fusion-background-owner")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object activity = chain.getThisObject();
                        if (activity instanceof Activity) {
                            rememberMiLinkFusionDeviceCenterView((Activity) activity);
                        }
                        return chain.proceed();
                    });
            Log.i(TAG, "MiLink Fusion Device Center background owner hook installed");
        } catch (Throwable throwable) {
            MILINK_FUSION_BACKGROUND_OWNER_HOOK_INSTALLED.set(false);
            Log.w(TAG, "MiLink Fusion Device Center owner hook unavailable", throwable);
        }
    }

    private void hookMediaConstraintSetLoad(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("media-constraint-set")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        int xmlId = (Integer) chain.getArg(1);
                        if (xmlId == XML_MEDIA_NORMAL || xmlId == XML_MEDIA_ISLAND_NORMAL) {
                            Context context = (Context) chain.getArg(0);
                            boolean island = xmlId == XML_MEDIA_ISLAND_NORMAL;
                            synchronized (PENDING_MEDIA_CONSTRAINTS) {
                                PENDING_MEDIA_CONSTRAINTS.put(chain.getThisObject(),
                                        new PendingMediaConstraint(context, island));
                            }
                            patchMediaConstraintSet(context, chain.getThisObject(), island);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    private void hookMediaConstraintSetApply(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("media-constraint-set-apply")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        PendingMediaConstraint pending;
                        synchronized (PENDING_MEDIA_CONSTRAINTS) {
                            pending = PENDING_MEDIA_CONSTRAINTS.get(chain.getThisObject());
                        }
                        Object target = chain.getArg(0);
                        if (pending != null && target instanceof View) {
                            pending.addTarget((View) target, method);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    /**
     * The media island's root measures itself from calHeight, which HyperOS normally derives
     * from the shared expanded_island_height_dp resource.  Updating that field after its own
     * calculation scopes the override to PlayerIslandConstraintLayout, leaving travel and other
     * Super Island cards on their stock height.
     */
    private void hookMediaIslandMeasuredHeight(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("media-island-measured-height")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ensureLoaded();
                        Object island = chain.getThisObject();
                        if (islandEnabled && island instanceof View) {
                            View view = (View) island;
                            setInt(island, "calHeight", dp(view.getContext(), islandHeight));
                            view.requestLayout();
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    private void hookFullAodStateChanged(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("full-aod-actions")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        inFullAod = (Boolean) chain.getArg(0);
                        Object result = chain.proceed();
                        setAodActionsVisibility(chain.getThisObject(), inFullAod);
                        setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    private void hookSeamlessVisibility(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("full-aod-seamless-visibility")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    private void hookMediaAttach(Method method) throws Throwable {
        if (!claimSystemUiMethod(method)) return;
        try {
            hook(method).setId("media-seekbar-style")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (islandProgressBar) replaceNormalSeekBar(chain.getArg(0));
                        Object result = chain.proceed();
                        if (islandProgressBar) configureSeekBar(chain.getArg(0));
                        setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                        return result;
                    });
        } catch (Throwable throwable) {
            INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            throw throwable;
        }
    }

    private static void rememberControlCenterSetter(View view, Method method) {
        synchronized (CONTROL_CENTER_REFRESH_METHODS) {
            List<Method> methods = CONTROL_CENTER_REFRESH_METHODS.get(view);
            if (methods == null) {
                methods = new ArrayList<>();
                CONTROL_CENTER_REFRESH_METHODS.put(view, methods);
            }
            if (!methods.contains(method)) {
                methods.add(method);
            }
        }
    }

    /**
     * Settings arrive on a worker after SystemUI has often completed its first layout. Re-run
     * only setters and ConstraintSet applications we previously observed, on their own views'
     * main queues. This changes no process-start or class-loader timing.
     */
    private static void refreshViewsAfterSettingsLoad() {
        List<Map.Entry<View, List<Method>>> setters;
        synchronized (CONTROL_CENTER_REFRESH_METHODS) {
            setters = new ArrayList<>(CONTROL_CENTER_REFRESH_METHODS.entrySet());
        }
        for (Map.Entry<View, List<Method>> entry : setters) {
            View view = entry.getKey();
            if (view == null) continue;
            List<Method> methods = new ArrayList<>(entry.getValue());
            view.post(() -> {
                if (view.isAttachedToWindow()) {
                    applyControlCenterSetters(view, methods);
                    return;
                }
                // A very fast unlock can finish the settings IPC while the panel has created its
                // views but before they enter the hierarchy.  Posting again would be a startup
                // retry loop; one attach listener instead applies the already-loaded snapshot at
                // the precise lifecycle point where the background/outline can be retained.
                view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View attachedView) {
                        attachedView.removeOnAttachStateChangeListener(this);
                        applyControlCenterSetters(attachedView, methods);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View detachedView) {
                        // Keep the listener until the first attach; RecyclerView can transiently
                        // detach a just-created card before its initial layout completes.
                    }
                });
            });
        }

        List<Map.Entry<Drawable, ControlCenterSurface>> drawables;
        synchronized (PENDING_PLUGIN_DRAWABLES) {
            drawables = new ArrayList<>(PENDING_PLUGIN_DRAWABLES.entrySet());
        }
        for (Map.Entry<Drawable, ControlCenterSurface> entry : drawables) {
            Drawable drawable = entry.getKey();
            ControlCenterSurface surface = entry.getValue();
            if (drawable == null || surface == null || !controlCenterEnabled) continue;
            float pixels = controlCenterRadius(surface)
                    * Resources.getSystem().getDisplayMetrics().density;
            setDrawableCornerRadius(drawable, pixels);
        }

        List<Map.Entry<Object, PendingMediaConstraint>> constraints;
        synchronized (PENDING_MEDIA_CONSTRAINTS) {
            constraints = new ArrayList<>(PENDING_MEDIA_CONSTRAINTS.entrySet());
        }
        for (Map.Entry<Object, PendingMediaConstraint> entry : constraints) {
            Object constraintSet = entry.getKey();
            PendingMediaConstraint pending = entry.getValue();
            if (constraintSet == null || pending == null) continue;
            pending.reapply(constraintSet);
        }
    }

    private static void applyControlCenterSetters(View view, List<Method> methods) {
        for (Method method : methods) {
            try {
                method.invoke(view, 0f);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The target may have been recreated during a configuration change.
            }
        }
        view.invalidateOutline();
        view.invalidate();
    }

    private static void rememberPluginDrawable(Object candidate, ControlCenterSurface surface) {
        if (!(candidate instanceof Drawable) || surface == null) return;
        synchronized (PENDING_PLUGIN_DRAWABLES) {
            PENDING_PLUGIN_DRAWABLES.put((Drawable) candidate, surface);
        }
    }

    private static final class PendingMediaConstraint {
        private final Context context;
        private final boolean island;
        private final List<WeakReference<View>> targets = new ArrayList<>();
        private Method applyMethod;

        PendingMediaConstraint(Context context, boolean island) {
            this.context = context;
            this.island = island;
        }

        synchronized void addTarget(View target, Method method) {
            applyMethod = method;
            for (WeakReference<View> reference : targets) {
                if (reference.get() == target) return;
            }
            targets.add(new WeakReference<>(target));
        }

        synchronized void reapply(Object constraintSet) {
            for (WeakReference<View> reference : new ArrayList<>(targets)) {
                View target = reference.get();
                if (target == null || applyMethod == null) continue;
                target.post(() -> {
                    if (!target.isAttachedToWindow()) return;
                    try {
                        patchMediaConstraintSet(context, constraintSet, island);
                        applyMethod.invoke(constraintSet, target);
                        target.requestLayout();
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // A stale card is safely refreshed by SystemUI's next media update.
                    }
                });
            }
            targets.removeIf(reference -> reference.get() == null);
        }
    }

    /**
     * HyperOS normally lowers lock-screen notifications whenever an enrolled UDFPS is present.
     * Keeping the original Flow output retains that behavior; disabling the setting replaces just
     * its UDFPS inputs, so the standard notification position is emitted without breaking the
     * Kotlin coroutine that owns the position calculation.
     */
    private void installLockscreenNotificationHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_NOTIFICATION_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> shelfSpaceFlow = Class.forName(
                    "com.android.systemui.statusbar.notification.stack.domain.interactor."
                            + "SharedNotificationContainerInteractor$useExtraShelfSpace$1",
                    false, classLoader);
            hook(shelfSpaceFlow.getDeclaredMethod("invokeSuspend", Object.class))
                    .setId("lockscreen-notification-fod-shelf-space")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        // HyperOS 4 sinks notifications when extra shelf space is disabled.
                        return sinkLockscreenNotificationsForFingerprint ? false : chain.proceed();
                    });

            Class<?> notificationPositionFlow = loadFirstAvailableClass(classLoader,
                    "com.android.keyguard.panel.KeyguardPanelViewController"
                            + "$nsslLockYPosition_delegate$lambda$104$$inlined$combine$1$3",
                    "com.android.keyguard.panel.KeyguardPanelViewController"
                            + "$nsslLockYPosition_delegate$lambda$106$$inlined$combine$1$3");
            Field enrolledValues = notificationPositionFlow.getDeclaredField("L$1");
            enrolledValues.setAccessible(true);
            hook(notificationPositionFlow.getDeclaredMethod("invokeSuspend", Object.class))
                    .setId("lockscreen-notification-fod-position")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        try {
                            Object value = enrolledValues.get(chain.getThisObject());
                            if (value instanceof Object[]) {
                                Object[] values = (Object[]) value;
                                // This is the flow's has-enrolled-UDFPS input on HyperOS 4. Set it
                                // explicitly in both modes: some builds publish false when the
                                // visual icon is transparent even though a UDFPS is enrolled.
                                if (values.length > 6) {
                                    values[6] = !sinkLockscreenNotificationsForFingerprint;
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // A changed SystemUI build keeps its stock positioning instead.
                        }
                        return chain.proceed();
                    });
            log(Log.INFO, TAG, "Installed lockscreen notification-position hooks");
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Could not install lockscreen notification UDFPS hooks", throwable);
        }
    }

    /**
     * Hides only the visual UDFPS surface after SystemUI creates it. The FOD window remains
     * attached, so fingerprint touch handling is unaffected. During AOD, the widget's own
     * mDozing state identifies the active doze session and keeps the freshly shown icon intact.
     */
    private void installLockscreenFingerprintHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_FINGERPRINT_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> iconClass = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiGxzwIconView", false, classLoader);
            Method dismissIcon = iconClass.getMethod("dismissFingerpirntIcon");
            hook(dismissIcon)
                    .setId("lockscreen-fingerprint-aod-dismiss")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        // On this SystemUI build, AOD invokes this method after show() whenever
                        // Xiaomi's own FOD-on-AOD toggle is off. Blocking it preserves the icon;
                        // show() has already attached the FOD window and touch target.
                        return shouldKeepFingerprintIconOnAod(chain.getThisObject())
                                ? null : chain.proceed();
                    });
            int hookedMethods = 0;
            for (Method method : iconClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean isDisplayMethod = ("show".equals(method.getName())
                        && parameterTypes.length == 1 && parameterTypes[0] == boolean.class)
                        || ("showFingerprintIcon".equals(method.getName())
                        && parameterTypes.length == 0)
                        || ("setGxzwIconOpaque".equals(method.getName())
                        && parameterTypes.length == 0);
                if (!isDisplayMethod) continue;
                final int hookIndex = hookedMethods++;
                hook(method)
                        .setId("lockscreen-fingerprint-visual-" + hookIndex)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            ensureLoaded();
                            Object result = chain.proceed();
                            boolean keepForAod = shouldKeepFingerprintIconOnAod(
                                    chain.getThisObject());
                            if (hideLockscreenFingerprintIcon && !keepForAod) {
                                try {
                                    dismissIcon.invoke(chain.getThisObject());
                                } catch (ReflectiveOperationException | RuntimeException ignored) {
                                    // The visual-only method is optional on adjacent HyperOS builds.
                                }
                            }
                            return result;
                        });
            }
            if (hookedMethods == 0) {
                throw new NoSuchMethodException("No MiuiGxzwIconView display methods found");
            }
            log(Log.INFO, TAG, "Installed " + hookedMethods + " lockscreen fingerprint hook(s)");
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "Could not install lockscreen fingerprint hooks", throwable);
        }
    }

    private static boolean shouldKeepFingerprintIconOnAod(Object iconView) {
        return hideLockscreenFingerprintIcon
                && showLockscreenFingerprintIconOnAod
                && booleanDeclaredField(iconView, "mDozing", false);
    }

    /**
     * The coroutine values above are only inputs to this MIUI compatibility bridge. Hooking the
     * bridge gives us a non-coroutine fallback at the exact framework call that applies a blur
     * radius. The stack guard retains the scope to the password-page bouncer consumer.
     */
    private void installLockscreenBouncerBlurCompatHook(ClassLoader classLoader) {
        if (!LOCKSCREEN_BOUNCER_BLUR_COMPAT_HOOK_INSTALLED.compareAndSet(false, true)) return;
        Method method = null;
        try {
            Class<?> compat = Class.forName(MI_BLUR_COMPAT, false, classLoader);
            method = compat.getDeclaredMethod("setMiBackgroundBlurRadiusCompat",
                    int.class, View.class);
            if (!claimSystemUiMethod(method)) return;
            hook(method)
                    .setId("lockscreen-password-bouncer-blur-compat")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!isLockscreenBouncerBlurCall()) return chain.proceed();
                        // Route-verification build: disable only the stock bouncer background
                        // blur, while letting the original method handle every other surface.
                        return chain.proceed(new Object[]{Integer.valueOf(0), chain.getArg(1)});
                    });
            Log.w(TAG, "Installed bouncer MiBlurCompat fallback hook");
        } catch (Throwable throwable) {
            if (method != null) INSTALLED_SYSTEM_UI_METHOD_HOOKS.remove(method);
            LOCKSCREEN_BOUNCER_BLUR_COMPAT_HOOK_INSTALLED.set(false);
            Log.e(TAG, "Bouncer MiBlurCompat fallback hook unavailable", throwable);
        }
    }

    private static boolean isLockscreenBouncerBlurCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (KEYGUARD_BLUR_COLLECTOR.equals(frame.getClassName())) return true;
        }
        return false;
    }

    /**
     * There are two separate PIN background paths in this HyperOS build. wallpaperBlurRatio feeds
     * the wallpaper service's ratio; bouncerMiBlurRadius feeds MiBlurCompat on bouncerContainer.
     * The latter is the actual visual background-blur radius behind the credential surface.
     */
    private void installLockscreenPasswordWallpaperRatioHook(ClassLoader classLoader) {
        if (!LOCKSCREEN_PASSWORD_WALLPAPER_RATIO_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> ratioLambda = Class.forName(KEYGUARD_WALLPAPER_BLUR_RATIO_LAMBDA,
                    false, classLoader);
            Method invokeSuspend = ratioLambda.getDeclaredMethod("invokeSuspend", Object.class);
            if (!claimSystemUiMethod(invokeSuspend)) return;
            // Do not call deoptimize() here. On some LSPosed/ART combinations it rejects these
            // generated coroutine methods before a hook has been registered, which previously
            // caused the whole installer to return without installing any of its hooks.
            hook(invokeSuspend)
                    .setId("lockscreen-password-wallpaper-ratio")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    // Exact semantic equivalent of replacing the method body with:
                    // const v1, 0x00000000; new-instance p0, Float; <init>(F); return-object p0
                    .intercept(chain -> Float.valueOf(STRICT_KEYGUARD_WALLPAPER_RATIO));

            // The Flow invokes the Kotlin Function3 bridge first, and that bridge immediately
            // calls invokeSuspend. Hook the bridge with the identical return value as an ART
            // dispatch fallback; no original code is executed on either path.
            Method invoke = ratioLambda.getDeclaredMethod(
                    "invoke", Object.class, Object.class, Object.class);
            if (!claimSystemUiMethod(invoke)) return;
            hook(invoke)
                    .setId("lockscreen-password-wallpaper-ratio-bridge")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Float.valueOf(STRICT_KEYGUARD_WALLPAPER_RATIO));

            // Unlike wallpaperBlurRatio, this coroutine returns the integer radius that is applied
            // directly to bouncerContainer through MiBlurCompat. A zero result disables that
            // layer; this is deliberately unconditional for the current route-verification build.
            Class<?> bouncerRadiusLambda = Class.forName(KEYGUARD_BOUNCER_BLUR_RADIUS_LAMBDA,
                    false, classLoader);
            Method bouncerInvokeSuspend = bouncerRadiusLambda.getDeclaredMethod(
                    "invokeSuspend", Object.class);
            if (!claimSystemUiMethod(bouncerInvokeSuspend)) return;
            hook(bouncerInvokeSuspend)
                    .setId("lockscreen-password-bouncer-blur-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Integer.valueOf(0));

            Method bouncerInvoke = bouncerRadiusLambda.getDeclaredMethod(
                    "invoke", Object.class, Object.class, Object.class);
            if (!claimSystemUiMethod(bouncerInvoke)) return;
            hook(bouncerInvoke)
                    .setId("lockscreen-password-bouncer-blur-radius-bridge")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Integer.valueOf(0));

            // This collector is the final call site: its class-id 1 branch takes the computed
            // Integer and calls setMiBackgroundBlurRadiusCompat on bouncerContainer. Intercepting
            // it covers an already-created/inlined Flow instance as well as future coroutines.
            Class<?> collector = Class.forName(KEYGUARD_BLUR_COLLECTOR, false, classLoader);
            Method emit = findDeclaredMethod(collector, "emit", 2);
            if (!claimSystemUiMethod(emit)) return;
            hook(emit)
                    .setId("lockscreen-password-bouncer-blur-consumer")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // class-id 0 is wallpaperRatio; class-id 1 is bouncerMiBlurRadius.
                        if (intField(chain.getThisObject(), "$r8$classId", -1) != 1) {
                            return chain.proceed();
                        }
                        return chain.proceed(new Object[]{Integer.valueOf(0), chain.getArg(1)});
                    });
            Log.w(TAG, "Installed keyguard wallpaper and bouncer blur route hooks");
        } catch (Throwable throwable) {
            LOCKSCREEN_PASSWORD_WALLPAPER_RATIO_HOOK_INSTALLED.set(false);
            Log.e(TAG, "Keyguard wallpaper-ratio hook unavailable", throwable);
        }
    }

    private static Method findDeclaredMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + '#' + name + '/' + parameterCount);
    }

    /**
     * The keyguard layouts are still view-based on the referenced HyperOS build.  Hook their
     * completed inflation rather than input dispatch, which keeps PIN/password verification and
     * accessibility owned by SystemUI while allowing a separate visual layer below each key.
     */
    private void installLockscreenCredentialHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_CREDENTIAL_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        int hooked = 0;
        try {
            hooked += hookLockscreenCredentialInflation(classLoader, KEYGUARD_PIN_VIEW, true) ? 1 : 0;
            hooked += hookLockscreenCredentialInflation(classLoader, KEYGUARD_PASSWORD_VIEW, false) ? 1 : 0;
            if (hooked == 0) throw new NoSuchMethodException("No keyguard credential inflation hook");
            log(Log.INFO, TAG, "Installed " + hooked + " lockscreen credential hook(s)");
        } catch (Throwable throwable) {
            LOCKSCREEN_CREDENTIAL_HOOKS_INSTALLED.set(false);
            log(Log.WARN, TAG, "Could not install lockscreen credential hooks", throwable);
        }
    }

    private boolean hookLockscreenCredentialInflation(
            ClassLoader classLoader, String className, boolean pin) throws Throwable {
        Class<?> credentialClass = Class.forName(className, false, classLoader);
        Method onFinishInflate = findMethodInHierarchy(credentialClass, "onFinishInflate");
        if (!claimSystemUiMethod(onFinishInflate)) return false;
        hook(onFinishInflate)
                .setId(pin ? "lockscreen-pin-material" : "lockscreen-password-background")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getThisObject() instanceof View) {
                        View credentialView = (View) chain.getThisObject();
                        // onLoaded runs immediately when PackageReady already has a snapshot and
                        // otherwise defers material creation until the remote settings are ready.
                        onLoaded(() -> credentialView.post(() ->
                                applyLockscreenCredentialCustomizations(credentialView, classLoader, pin)));
                    }
                    return result;
                });
        return true;
    }

    private static Method findMethodInHierarchy(Class<?> type, String name)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + '#' + name);
    }

    private static void applyLockscreenCredentialCustomizations(
            View root, ClassLoader classLoader, boolean pin) {
        if (pin && lockscreenPinKeySoftGlassEnabled) {
            applyLockscreenPinKeyGlass(root, classLoader);
        }
    }

    private static void clearStockLockscreenBouncerBlur(View credentialView, ClassLoader classLoader) {
        try {
            View bouncerContainer = credentialView.getRootView()
                    .findViewById(KEYGUARD_BOUNCER_CONTAINER_ID);
            if (bouncerContainer == null) return;
            Class<?> compat = Class.forName(MI_BLUR_COMPAT, false, classLoader);
            compat.getDeclaredMethod("setPassWindowBlurEnabledCompat", View.class, boolean.class)
                    .invoke(null, bouncerContainer, false);
            compat.getDeclaredMethod("setMiBackgroundBlurModeCompat", int.class, View.class)
                    .invoke(null, 0, bouncerContainer);
            compat.getDeclaredMethod("setMiBackgroundBlurRadiusCompat", int.class, View.class)
                    .invoke(null, 0, bouncerContainer);
            compat.getDeclaredMethod("clearMiBackgroundBlendColorCompat", View.class)
                    .invoke(null, bouncerContainer);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep SystemUI's stock rendering intact on a build that renames this compat bridge.
        }
    }

    private static void applyLockscreenPinKeyGlass(View root, ClassLoader classLoader) {
        applyLockscreenPinGlassLayout(root);
        List<View> keys = new ArrayList<>();
        collectLockscreenPinKeys(root, keys);
        for (View key : keys) {
            if (!(key instanceof ViewGroup)) continue;
            ViewGroup keyGroup = (ViewGroup) key;
            try {
                applyLockscreenPinKeyGlass(keyGroup, classLoader);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // One unsupported button must never prevent the remaining keyguard controls from
                // drawing or accepting input.
            }
        }
    }

    /**
     * The system PIN layout gives rows 1–3 only a tiny bottom margin.  The expanded glass disc
     * needs its own vertical rhythm, so retain the key sizes and make space in the fixed-height
     * keypad container instead of shrinking the stock hit targets.
     */
    private static void applyLockscreenPinGlassLayout(View root) {
        int extraGap = Math.round(lockscreenPinKeyGlassVerticalGap
                * root.getResources().getDisplayMetrics().density);
        int expandedRows = 0;
        for (View view : descendantsNamed(root, "row1", "row2", "row3")) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) continue;
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            Integer original = PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS.get(view);
            if (original == null) {
                original = margins.bottomMargin;
                PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS.put(view, original);
            }
            margins.bottomMargin = original + extraGap;
            view.setLayoutParams(margins);
            expandedRows++;
        }

        if (expandedRows == 0) return;
        for (View container : descendantsNamed(root, "pin_container")) {
            ViewGroup.LayoutParams params = container.getLayoutParams();
            if (params == null || params.height <= 0) continue;
            Integer originalHeight = PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS.get(container);
            if (originalHeight == null) {
                originalHeight = params.height;
                PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS.put(container, originalHeight);
            }
            params.height = originalHeight + extraGap * expandedRows;
            container.setLayoutParams(params);
        }
    }

    private static List<View> descendantsNamed(View root, String... names) {
        List<View> matches = new ArrayList<>();
        collectDescendantsNamed(root, matches, names);
        return matches;
    }

    private static void collectDescendantsNamed(View view, List<View> matches, String... names) {
        try {
            String entryName = view.getResources().getResourceEntryName(view.getId());
            for (String name : names) {
                if (name.equals(entryName)) {
                    matches.add(view);
                    break;
                }
            }
        } catch (Resources.NotFoundException ignored) {
            // Views without a resource ID cannot be a PIN layout row.
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectDescendantsNamed(group.getChildAt(index), matches, names);
        }
    }

    private static void collectLockscreenPinKeys(View view, List<View> keys) {
        if (isLockscreenPinKey(view)) keys.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectLockscreenPinKeys(group.getChildAt(index), keys);
        }
    }

    private static boolean isLockscreenPinKey(View view) {
        try {
            String id = view.getResources().getResourceEntryName(view.getId());
            return id.length() == 4 && id.charAt(0) == 'k' && id.charAt(1) == 'e'
                    && id.charAt(2) == 'y' && id.charAt(3) >= '0' && id.charAt(3) <= '9';
        } catch (Resources.NotFoundException ignored) {
            return false;
        }
    }

    private static void applyLockscreenPinKeyGlass(ViewGroup key, ClassLoader classLoader)
            throws ReflectiveOperationException {
        for (int index = key.getChildCount() - 1; index >= 0; index--) {
            if (LOCKSCREEN_PIN_GLASS_TAG.equals(key.getChildAt(index).getTag())) {
                key.removeViewAt(index);
            }
        }
        int keyDiameter = Math.min(key.getWidth(), key.getHeight());
        if (keyDiameter <= 0) return;
        int visualPadding = Math.round(lockscreenPinKeyGlassExtraRadius
                * key.getResources().getDisplayMetrics().density);
        int diameter = keyDiameter + visualPadding * 2;

        ImageView material = new ImageView(key.getContext());
        material.setTag(LOCKSCREEN_PIN_GLASS_TAG);
        material.setClickable(false);
        material.setFocusable(false);
        material.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        material.setDuplicateParentStateEnabled(true);
        // Xiaomi's backdrop compositor registers a view only after it has drawable content.
        GradientDrawable backdrop = new GradientDrawable();
        backdrop.setShape(GradientDrawable.OVAL);
        backdrop.setColor(Color.argb(1, 255, 255, 255));
        material.setImageDrawable(backdrop);
        GradientDrawable rippleMask = new GradientDrawable();
        rippleMask.setShape(GradientDrawable.OVAL);
        rippleMask.setColor(Color.WHITE);
        material.setForeground(new RippleDrawable(
                ColorStateList.valueOf(0x40FFFFFF), null, rippleMask));
        material.setClipToOutline(true);
        material.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });

        // The stock rows already opt out of clipping.  Opt the key itself out as well because
        // the material intentionally extends past its short edge; it remains non-clickable and
        // therefore cannot steal the original key's touch target.
        key.setClipChildren(false);
        key.setClipToPadding(false);
        key.addView(material, 0, new ViewGroup.LayoutParams(diameter, diameter));
        int left = (key.getWidth() - diameter) / 2;
        int top = (key.getHeight() - diameter) / 2;
        material.layout(left, top, left + diameter, top + diameter);
        material.invalidateOutline();
        try {
            // MiGlassCompat waits for this attached view's layout before it applies the platform
            // material type, so add and place the layer before invoking it.
            applySystemPinGlassMaterial(material, classLoader);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            key.removeView(material);
            throw failure;
        }
        // The stock expanding background would otherwise cover the material layer.  Text and
        // touch dispatch remain on the original NumPadKey above the non-clickable image layer.
        key.setBackground(null);
    }

    private static void applySystemPinGlassMaterial(View view, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Method clearBlend = View.class.getMethod("clearMiBackgroundBlendColor");
        clearBlend.invoke(view);
        View.class.getMethod("setPassWindowBlurEnabled", boolean.class).invoke(view, true);
        View.class.getMethod("setMiViewBlurMode", int.class).invoke(view, 1);
        View.class.getMethod("setMiBackgroundBlurMode", int.class).invoke(view, 1);
        View.class.getMethod("setMiBackgroundBlurRadius", int.class).invoke(view, 80);
        View.class.getMethod("addMiBackgroundBlendColor", int.class, int.class).invoke(
                view, Color.argb(26, 255, 255, 255), LOCKSCREEN_PIN_GLASS_BLEND_MODE);

        Class<?> glassCompat = Class.forName(MI_GLASS_COMPAT, false, classLoader);
        glassCompat.getMethod("setMiGlassBlurRadius", View.class, int.class, int.class).invoke(
                null, view, LOCKSCREEN_PIN_GLASS_BLUR_RADIUS,
                LOCKSCREEN_PIN_GLASS_BLUR_RADIUS * 2);
        glassCompat.getMethod("setMiViewMaterialTypeCompat", int.class, View.class).invoke(
                null, LOCKSCREEN_PIN_GLASS_MATERIAL_TYPE, view);
        glassCompat.getMethod("setMiGlassCompat", View.class, float[].class).invoke(
                null, view, (Object) LOCKSCREEN_PIN_GLASS_PARAMETERS.clone());
    }

    private static Class<?> loadFirstAvailableClass(ClassLoader classLoader, String... classNames)
            throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        for (String className : classNames) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException exception) {
                failure = exception;
            }
        }
        if (failure != null) throw failure;
        throw new ClassNotFoundException("No candidate classes provided");
    }

    /**
     * MIUISystemUIPlugin normally runs inside the SystemUI host process but is loaded by its own
     * PathClassLoader.  Consequently PackageReady only gives us SystemUI's class loader on many
     * HyperOS builds; trying to hook plugin classes from it silently misses every slider class.
     * Resolve the actual loader when one of the Control Center view classes is first loaded.
     */
    private void installSystemUiPluginClassLoaderResolver() throws NoSuchMethodException {
        if (!PLUGIN_CLASS_LOADER_RESOLVER_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Method loadClass = ClassLoader.class.getDeclaredMethod(
                "loadClass", String.class, boolean.class);
        hook(loadClass)
                .setId("systemui-plugin-class-loader")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object requestedName = chain.getArg(0);
                    if (requestedName instanceof String && result instanceof Class<?>
                            && !Boolean.TRUE.equals(INSTALLING_LOADED_CLASS_HOOK.get())) {
                        String className = (String) requestedName;
                        boolean systemUiTarget = isSystemUiLateHookClass(className);
                        boolean pluginTarget = isPluginCornerClass(className);
                        if (!systemUiTarget && !pluginTarget) {
                            return result;
                        }
                        Class<?> loadedClass = (Class<?>) result;
                        INSTALLING_LOADED_CLASS_HOOK.set(true);
                        try {
                            // Never resolve a second plugin class while ClassLoader's monitor is
                            // held. This is the difference from the old working-but-unstable
                            // approach that could repeatedly restart SystemUI during boot.
                            if (systemUiTarget) {
                                installSystemUiHookForLoadedClass(className, loadedClass);
                            }
                            if (pluginTarget) {
                                installPluginCornerHookForLoadedClass(className, loadedClass);
                                // The fallback targets framework resource/drawable methods only;
                                // it never resolves another plugin class under loadClass's lock.
                                installPluginDrawableHooks();
                            }
                        } catch (Throwable throwable) {
                            Log.w(TAG, "Loaded-class hook unavailable: " + className, throwable);
                        } finally {
                            INSTALLING_LOADED_CLASS_HOOK.remove();
                        }
                    }
                    return result;
                });
    }

    /**
     * HyperOS creates the embedded MIUISystemUIPlugin loader through PluginFactory before it
     * loads the plugin entry component. Hooking this factory gives us the real PathClassLoader
     * without doing any installation inside ClassLoader.loadClass.
     */
    private void installSystemUiPluginLoaderFactoryHook(ClassLoader classLoader) {
        if (!PLUGIN_FACTORY_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> factory = Class.forName(
                    "com.android.systemui.shared.plugins.PluginInstance$PluginFactory",
                    false, classLoader);
            Method createClassLoader = factory.getDeclaredMethod("createClassLoader");
            hook(createClassLoader)
                    .setId("systemui-plugin-loader-factory")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (!(result instanceof ClassLoader)) return result;
                        Object applicationInfo = fieldValue(chain.getThisObject(), "pluginAppInfo");
                        Object packageName = fieldValue(applicationInfo, "packageName");
                        if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                            installSystemUiPluginCornerHooks((ClassLoader) result);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            PLUGIN_FACTORY_HOOK_INSTALLED.set(false);
            Log.w(TAG, "SystemUI plugin loader factory hook unavailable", throwable);
        }
    }

    private static boolean isSystemUiLateHookClass(String className) {
        return "androidx.constraintlayout.widget.ConstraintSet".equals(className)
                || PLAYER_ISLAND_CONSTRAINT_LAYOUT.equals(className)
                || "com.android.systemui.statusbar.notification.mediacontrol."
                .concat("MiuiMediaViewControllerImpl").equals(className);
    }

    /**
     * Fusion Device Center is hosted by CirculateWorldActivity in MiLink's :ui process.  Its
     * first-level grid uses BaseCardView, which reads circulate_card_shape and creates a
     * ViewOutlineProvider from it.  Additional Fusion surfaces use
     * RoundedClipFrameLayout/circulate_card_corner_radius.  Cover every path without touching
     * the MiLink settings PreferenceFragment that the previous implementation accidentally
     * targeted.
     */
    private void installMiLinkFusionCardHooks(ClassLoader classLoader) {
        if (!MILINK_FUSION_CARD_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> cardLayout = Class.forName(
                    "com.xiaomi.smarthome.fusion.ui.RoundedClipFrameLayout", false, classLoader);
            Method setCornerRadius = cardLayout.getDeclaredMethod("setCornerRadius", float.class);
            hook(setCornerRadius)
                    .setId("milink-fusion-card-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (!miLinkMainCardsEnabled || !(chain.getThisObject() instanceof View)) {
                            return chain.proceed();
                        }
                        View card = (View) chain.getThisObject();
                        float pixels = miLinkMainCardRadius * card.getResources()
                                .getDisplayMetrics().density;
                        return chain.proceed(new Object[]{pixels});
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "MiLink Fusion Device Center card hook unavailable", throwable);
        }
    }

    private static boolean isPluginCornerClass(String className) {
        return "miui.systemui.controlcenter.qs.tileview.QSTileItemIconView".equals(className)
                || "miui.systemui.controlcenter.qs.tileview.QSCardItemView".equals(className)
                || "miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder".equals(className)
                || "miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase".equals(className)
                || "miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelSliderDelegate"
                .equals(className)
                || "com.android.systemui.miui.volume.VolumeColumnRes".equals(className)
                || "miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder"
                .equals(className);
    }

    /**
     * Covers both direct smali calls and XML shape drawables in MIUISystemUIPlugin.  The latter
     * are resolved through ResourcesImpl/TypedArray instead of Resources.getDimension(), which
     * is why a dimension-only hook could previously miss parts of the control centre.
     */
    private void installSystemUiPluginCornerHooks(ClassLoader classLoader) {
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.qs.tileview.QSTileItemIconView",
                "setCornerRadius", ControlCenterSurface.TILE);
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.qs.tileview.QSCardItemView",
                "setCornerRadius", ControlCenterSurface.CARD);
        hookPluginCardBackgroundRefresh(classLoader);
        hookPluginAdvancedCornerSetter(classLoader,
                "miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase",
                "setContentBgRadius", ControlCenterSurface.CARD);
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder",
                "setCornerRadius", ControlCenterSurface.MEDIA);
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder",
                "setOutlineRadius", ControlCenterSurface.SLIDER);
        // Brightness is not a regular ToggleSliderViewHolder in the secondary panel. It
        // recreates the outer radius on every show/configuration change, so keep that hook here.
        // Its progress layer intentionally keeps MIUI's small native clip radius: the parent
        // outline supplies the rounded bottom while the fill level stays visually flat.
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelSliderDelegate",
                "setOutlineRadius", ControlCenterSurface.DETAIL_SLIDER);
        hookSecondaryVolumeRadiusResolver(classLoader);
        installPluginDrawableHooks();
    }

    /** Hooks one already-loaded plugin class without triggering any further plugin class loads. */
    private void installPluginCornerHookForLoadedClass(String className, Class<?> loadedClass) {
        if ("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView".equals(className)) {
            hookPluginCornerSetter(loadedClass, "setCornerRadius", ControlCenterSurface.TILE, false);
        } else if ("miui.systemui.controlcenter.qs.tileview.QSCardItemView".equals(className)) {
            hookPluginCornerSetter(loadedClass, "setCornerRadius", ControlCenterSurface.CARD, false);
            hookPluginCardBackgroundRefresh(loadedClass);
        } else if ("miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase"
                .equals(className)) {
            hookPluginCornerSetter(loadedClass, "setContentBgRadius", ControlCenterSurface.CARD, true);
        } else if ("miui.systemui.controlcenter.panel.main.media.MediaPlayerController$MediaPlayerViewHolder"
                .equals(className)) {
            hookPluginCornerSetter(loadedClass, "setCornerRadius", ControlCenterSurface.MEDIA, false);
        } else if ("miui.systemui.controlcenter.panel.main.recyclerview.ToggleSliderViewHolder"
                .equals(className)) {
            hookPluginCornerSetter(loadedClass, "setOutlineRadius", ControlCenterSurface.SLIDER, false);
        } else if ("miui.systemui.controlcenter.panel.secondary.brightness.BrightnessPanelSliderDelegate"
                .equals(className)) {
            hookPluginCornerSetter(loadedClass, "setOutlineRadius",
                    ControlCenterSurface.DETAIL_SLIDER, false);
        } else if ("com.android.systemui.miui.volume.VolumeColumnRes".equals(className)) {
            hookSecondaryVolumeRadiusResolver(loadedClass);
        }
    }

    private void hookPluginCornerSetter(ClassLoader classLoader, String className, String methodName,
                                        ControlCenterSurface surface) {
        hookPluginCornerSetter(classLoader, className, methodName, surface, false);
    }

    /**
     * The 1.3.1 implementation for the volume-key popup.  On the user's HyperOS build this
     * resolver is the single outer-panel radius source; keeping it here avoids altering any
     * volume icon, mute-button, or inner progress drawable resource.
     */
    private void hookSecondaryVolumeRadiusResolver(ClassLoader classLoader) {
        try {
            Class<?> resolver = Class.forName(
                    "com.android.systemui.miui.volume.VolumeColumnRes", false, classLoader);
            hookSecondaryVolumeRadiusResolver(resolver);
        } catch (Throwable throwable) {
            Log.w(TAG, "Secondary volume slider hook unavailable", throwable);
        }
    }

    private void hookSecondaryVolumeRadiusResolver(Class<?> resolver) {
        try {
            Method getRadius = resolver.getDeclaredMethod("getRadius", Context.class,
                    boolean.class, boolean.class);
            if (!INSTALLED_PLUGIN_METHOD_HOOKS.add(getRadius)) return;
            hook(getRadius)
                    .setId("secondary-volume-slider-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ensureLoaded();
                        Object showDialog = chain.getArg(1);
                        if (!(showDialog instanceof Boolean)) return result;
                        Context context = (Context) chain.getArg(0);
                        if ((Boolean) showDialog) {
                            if (volumePanelRadius <= 0f) return result;
                            return Math.round(volumePanelRadius
                                    * context.getResources().getDisplayMetrics().density);
                        }
                        if (!controlCenterEnabled) return result;
                        return Math.round(controlCenterRadius(ControlCenterSurface.DETAIL_SLIDER)
                                * context.getResources().getDisplayMetrics().density);
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Secondary volume slider hook unavailable", throwable);
        }
    }

    /** Advanced-only setters are for private detail-panel dimensions, not universal_corner_radius. */
    private void hookPluginAdvancedCornerSetter(ClassLoader classLoader, String className,
                                                String methodName, ControlCenterSurface surface) {
        hookPluginCornerSetter(classLoader, className, methodName, surface, true);
    }

    private void hookPluginCornerSetter(ClassLoader classLoader, String className, String methodName,
                                        ControlCenterSurface surface, boolean advancedOnly) {
        try {
            hookPluginCornerSetter(Class.forName(className, false, classLoader), methodName,
                    surface, advancedOnly);
        } catch (Throwable throwable) {
            Log.w(TAG, "Control-centre hook unavailable: " + className + '#' + methodName, throwable);
        }
    }

    private void hookPluginCornerSetter(Class<?> targetClass, String methodName,
                                        ControlCenterSurface surface, boolean advancedOnly) {
        try {
            Method method = targetClass.getMethod(methodName, float.class);
            if (!INSTALLED_PLUGIN_METHOD_HOOKS.add(method)) return;
            hook(method)
                    .setId("plugin-" + targetClass.getName() + "-" + methodName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (chain.getThisObject() instanceof View) {
                            rememberControlCenterSetter((View) chain.getThisObject(), method);
                        }
                        ensureLoaded();
                        if (!controlCenterEnabled || (advancedOnly && !advancedControlCenterCorners)) {
                            return chain.proceed();
                        }
                        // Secondary brightness tiles (auto brightness, reading mode, etc.) are
                        // non-card icon views.  MIUI deliberately gives them half their tile
                        // size, i.e. a circle.  Replacing that value turns them into rounded
                        // rectangles after a panel refresh.  Only card tiles use the configurable
                        // corner radius. Wi-Fi and cellular are an exception on this plugin
                        // build: their primary surfaces report card=false even though they draw
                        // a full connectivity card.
                        if (surface == ControlCenterSurface.TILE
                                && !booleanDeclaredField(chain.getThisObject(), "card", true)
                                && !isConnectivityTile(chain.getThisObject())) {
                            return chain.proceed();
                        }
                        Object argument = chain.getArg(0);
                        float originalPixels = argument instanceof Float ? (Float) argument : 0f;
                        float replacement = cornerPixels(chain.getThisObject(), surface, originalPixels);
                        return chain.proceed(new Object[]{replacement});
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Control-centre hook unavailable: " + targetClass.getName() + '#'
                    + methodName, throwable);
        }
    }

    /**
     * Wi-Fi and mobile-network cards use QSCardItemView's blur path.  That path replaces its
     * background outline by writing _cornerRadius directly, bypassing setCornerRadius entirely.
     * Reapply after each background update so the outline and the drawable paths agree.
     */
    private void hookPluginCardBackgroundRefresh(ClassLoader classLoader) {
        try {
            hookPluginCardBackgroundRefresh(Class.forName(
                    "miui.systemui.controlcenter.qs.tileview.QSCardItemView", false, classLoader));
        } catch (Throwable throwable) {
            Log.w(TAG, "Control-centre card background hook unavailable", throwable);
        }
    }

    private void hookPluginCardBackgroundRefresh(Class<?> targetClass) {
        try {
            Method updateBackground = targetClass.getDeclaredMethod(
                    "updateBackground", boolean.class, boolean.class);
            Method setCornerRadius = targetClass.getMethod("setCornerRadius", float.class);
            if (!INSTALLED_PLUGIN_METHOD_HOOKS.add(updateBackground)) return;
            hook(updateBackground)
                    .setId("plugin-qs-card-background-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object receiver = chain.getThisObject();
                        if (!(receiver instanceof View)) return result;
                        View view = (View) receiver;
                        rememberControlCenterSetter(view, setCornerRadius);
                        ensureLoaded();
                        if (controlCenterEnabled) {
                            applyControlCenterSetters(view,
                                    Collections.singletonList(setCornerRadius));
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Control-centre card background hook unavailable: "
                    + targetClass.getName(), throwable);
        }
    }

    private static boolean isConnectivityTile(Object tileIconView) {
        Object state = declaredFieldValue(tileIconView, "state");
        Object spec = declaredFieldValue(state, "spec");
        return "wifi".equals(spec) || "cell".equals(spec) || "cellular".equals(spec);
    }

    private static Object declaredFieldValue(Object target, String fieldName) {
        for (Class<?> type = target == null ? null : target.getClass(); type != null;
                type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Try the superclass; Kotlin backing fields are commonly private.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Hooks XML drawable loading as a fallback for every shape that references the universal radius. */
    private void installPluginDrawableHooks() {
        if (!PLUGIN_DRAWABLE_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            hook(GradientDrawable.class.getDeclaredMethod("setCornerRadius", float.class))
                    .setId("plugin-gradient-corner-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        ControlCenterSurface surface = INFLATING_PLUGIN_DRAWABLE.get();
                        rememberPluginDrawable(chain.getThisObject(), surface);
                        Object argument = chain.getArg(0);
                        float originalPixels = argument instanceof Float ? (Float) argument : 0f;
                        if (!controlCenterEnabled || surface == null) {
                            return chain.proceed();
                        }
                        float pixels = controlCenterRadius(surface)
                                * Resources.getSystem().getDisplayMetrics().density;
                        return chain.proceed(new Object[]{pixels});
                    });
            hook(GradientDrawable.class.getDeclaredMethod("setCornerRadii", float[].class))
                    .setId("plugin-gradient-corner-radii")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        ControlCenterSurface surface = INFLATING_PLUGIN_DRAWABLE.get();
                        rememberPluginDrawable(chain.getThisObject(), surface);
                        Object argument = chain.getArg(0);
                        float[] original = argument instanceof float[] ? (float[]) argument : null;
                        if (!controlCenterEnabled || surface == null) {
                            return chain.proceed();
                        }
                        float pixels = controlCenterRadius(surface)
                                * Resources.getSystem().getDisplayMetrics().density;
                        float[] radii = new float[]{pixels, pixels, pixels, pixels, pixels, pixels, pixels, pixels};
                        return chain.proceed(new Object[]{radii});
                    });
            hook(Resources.class.getDeclaredMethod("getDrawable", int.class))
                    .setId("plugin-drawable-public")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        Resources resources = (Resources) chain.getThisObject();
                        int resourceId = (Integer) chain.getArg(0);
                        rememberPluginDrawable(result,
                                drawableSurface(resourceEntryName(resources, resourceId)));
                        patchPluginDrawable(resources, resourceId, result);
                        return result;
                    });
            hook(Resources.class.getDeclaredMethod("getDrawable", int.class, Resources.Theme.class))
                    .setId("plugin-drawable-public-themed")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        Resources resources = (Resources) chain.getThisObject();
                        int resourceId = (Integer) chain.getArg(0);
                        rememberPluginDrawable(result,
                                drawableSurface(resourceEntryName(resources, resourceId)));
                        patchPluginDrawable(resources, resourceId, result);
                        return result;
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not install public drawable fallback", throwable);
        }

        try {
            Class<?> resourcesImpl = Class.forName("android.content.res.ResourcesImpl");
            int index = 0;
            for (Method method : resourcesImpl.getDeclaredMethods()) {
                if (!"loadDrawable".equals(method.getName())
                        || !Drawable.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                final String hookId = "plugin-drawable-loader-" + index++;
                hook(method)
                        .setId(hookId)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Resources resources = null;
                            Integer resourceId = null;
                            for (int position = 0; position < parameterTypes.length; position++) {
                                Object argument = chain.getArg(position);
                                if (resources == null && argument instanceof Resources) {
                                    resources = (Resources) argument;
                                }
                                if (resourceId == null && parameterTypes[position] == int.class
                                        && argument instanceof Integer) {
                                    resourceId = (Integer) argument;
                                }
                            }
                            // Plugin resources are frequently resolved through a SystemUI-owned
                            // Context.  In that case Android reports com.android.systemui as the
                            // resource package even though the entry itself came from the plugin
                            // APK.  The drawable entry name is the stable identity here.
                            ControlCenterSurface surface = resources == null || resourceId == null
                                    ? null : drawableSurface(resourceEntryName(resources, resourceId));
                            if (surface != null) {
                                INFLATING_PLUGIN_DRAWABLE.set(surface);
                            }
                            try {
                                ensureLoaded();
                                Object result = chain.proceed();
                                if (resources != null && resourceId != null) {
                                    rememberPluginDrawable(result, surface);
                                    patchPluginDrawable(resources, resourceId, result);
                                }
                                return result;
                            } finally {
                                if (surface != null) {
                                    INFLATING_PLUGIN_DRAWABLE.remove();
                                }
                            }
                        });
            }
        } catch (Throwable throwable) {
            // Android/HyperOS revisions may hide or rename this implementation detail.
            Log.w(TAG, "Could not install ResourcesImpl drawable hooks", throwable);
        }
    }

    /** Reproduces the edited miui_media_session_normal.xml ConstraintSet in the reference APK. */
    private static void patchMediaConstraintSet(Context context, Object constraintSet, boolean island) {
        int parent = 0;
        int mediaBackground = id(context, island ? "media_bg_view" : "media_bg");
        int mediaBackgroundFallback = id(context, "media_bg");
        int albumArt = id(context, "album_art");
        int seamless = id(context, "media_seamless");
        int title = id(context, "header_title");
        int artist = id(context, "header_artist");
        int elapsed = id(context, "media_elapsed_time");
        int progress = id(context, "media_progress_bar");
        int total = id(context, "media_total_time");
        int actions = id(context, "actions");
        int[] action = {id(context, "action0"), id(context, "action1"), id(context, "action2"),
                id(context, "action3"), id(context, "action4")};

        // Do not replace expanded_island_height_dp globally: HyperOS also uses that integer for
        // non-media Super Island cards (for example, 12306).  The media island has its own
        // ConstraintSet, so changing its background here keeps this preference media-only.
        if (island && islandEnabled) {
            setHeight(constraintSet, mediaBackground, dp(context, islandHeight));
            setHeight(constraintSet, mediaBackgroundFallback, dp(context, islandHeight));
        }
        if (!mediaEnabled) return;
        // ConstraintSet.load() only accepts an APK resource id, therefore an XML string saved by
        // the companion app is parsed after the stock set has loaded.  The custom XML is limited
        // to the normal media session; the island set retains its dedicated reference layout.
        if (!island && customMediaConstraintSetEnabled
                && applyCustomMediaConstraintSet(context, constraintSet, customMediaConstraintSetXml)) {
            setAodSeamlessConstraintVisibility(constraintSet, seamless);
            return;
        }
        if (!island || !islandEnabled) {
            setHeight(constraintSet, mediaBackground, dp(context, Math.round(expandedHeight)));
            setHeight(constraintSet, mediaBackgroundFallback, dp(context, Math.round(expandedHeight)));
        }

        Object seamlessLayout = layout(constraintSet, seamless);
        setInt(seamlessLayout, "topMargin", dp(context, 18));
        setInt(seamlessLayout, "endMargin", dp(context, 12));

        Object titleLayout = layout(constraintSet, title);
        Object artistLayout = layout(constraintSet, artist);
        setInt(titleLayout, "topMargin", dp(context, 18));
        if (island) {
            connect(titleLayout, "endToStart", action[4]);
            connect(artistLayout, "endToStart", action[4]);
        }

        Object progressLayout = layout(constraintSet, progress);
        setInt(progressLayout, "mWidth", 0);
        connect(progressLayout, "startToStart", parent);
        connect(progressLayout, "startToEnd", -1);
        connect(progressLayout, "endToEnd", parent);
        connect(progressLayout, "endToStart", -1);
        connect(progressLayout, "topToBottom", albumArt);
        connect(progressLayout, "topToTop", -1);
        connect(progressLayout, "bottomToTop", -1);
        connect(progressLayout, "bottomToBottom", -1);
        setInt(progressLayout, "topMargin", dimen(context, "media_progressbar_margin_top"));
        setInt(progressLayout, "startMargin", dp(context, 46));
        setInt(progressLayout, "endMargin", dp(context, 46));

        Object elapsedLayout = layout(constraintSet, elapsed);
        connect(elapsedLayout, "startToStart", parent);
        connect(elapsedLayout, "startToEnd", -1);
        connect(elapsedLayout, "endToStart", -1);
        connect(elapsedLayout, "endToEnd", -1);
        connect(elapsedLayout, "topToTop", progress);
        connect(elapsedLayout, "topToBottom", -1);
        connect(elapsedLayout, "bottomToBottom", progress);
        connect(elapsedLayout, "bottomToTop", -1);
        setInt(elapsedLayout, "startMargin", dp(context, 8));

        Object totalLayout = layout(constraintSet, total);
        connect(totalLayout, "endToEnd", parent);
        connect(totalLayout, "endToStart", -1);
        connect(totalLayout, "startToStart", -1);
        connect(totalLayout, "startToEnd", -1);
        connect(totalLayout, "topToTop", progress);
        connect(totalLayout, "topToBottom", -1);
        connect(totalLayout, "bottomToBottom", progress);
        connect(totalLayout, "bottomToTop", -1);
        setInt(totalLayout, "endMargin", dp(context, 8));

        Object actionsLayout = layout(constraintSet, actions);
        connect(actionsLayout, "topToBottom", progress);
        connect(actionsLayout, "topToTop", -1);
        setInt(actionsLayout, "topMargin", dp(context, 14));

        setActionConstraints(context, constraintSet, action, actions);
        setAodSeamlessConstraintVisibility(constraintSet, seamless);
    }

    /**
     * Applies the geometry-bearing attributes from a user supplied ConstraintSet XML document.
     * The stock set is loaded first, so omitted attributes retain MIUI's original value.  This
     * deliberately does not inflate views or accept arbitrary classes from XML.
     */
    private static boolean applyCustomMediaConstraintSet(Context context, Object constraintSet,
                                                         String source) {
        if (source == null || source.trim().isEmpty() || source.length() > 64 * 1024) {
            return false;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(source));
            boolean changed = false;
            int constraintCount = 0;
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG
                        || !"Constraint".equals(parser.getName())) {
                    continue;
                }
                if (++constraintCount > 48) {
                    throw new IllegalArgumentException("Too many Constraint elements");
                }
                int viewId = parseConstraintTarget(context, parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android", "id"));
                Object targetLayout = layout(constraintSet, viewId);
                if (targetLayout == null) {
                    continue;
                }
                for (int index = 0; index < parser.getAttributeCount(); index++) {
                    applyCustomConstraintAttribute(context, targetLayout,
                            parser.getAttributeName(index), parser.getAttributeValue(index));
                }
                changed = true;
            }
            return changed;
        } catch (Throwable throwable) {
            Log.w(TAG, "Invalid custom media ConstraintSet XML; using bundled layout", throwable);
            return false;
        }
    }

    private static void applyCustomConstraintAttribute(Context context, Object targetLayout,
                                                       String name, String value) {
        if (name == null || value == null) return;
        switch (name) {
            case "layout_width": setInt(targetLayout, "mWidth", parseDimension(context, value)); return;
            case "layout_height": setInt(targetLayout, "mHeight", parseDimension(context, value)); return;
            case "layout_margin":
                int margin = parseDimension(context, value);
                setInt(targetLayout, "leftMargin", margin); setInt(targetLayout, "rightMargin", margin);
                setInt(targetLayout, "topMargin", margin); setInt(targetLayout, "bottomMargin", margin);
                return;
            case "layout_marginStart": setInt(targetLayout, "startMargin", parseDimension(context, value)); return;
            case "layout_marginEnd": setInt(targetLayout, "endMargin", parseDimension(context, value)); return;
            case "layout_marginLeft": setInt(targetLayout, "leftMargin", parseDimension(context, value)); return;
            case "layout_marginRight": setInt(targetLayout, "rightMargin", parseDimension(context, value)); return;
            case "layout_marginTop": setInt(targetLayout, "topMargin", parseDimension(context, value)); return;
            case "layout_marginBottom": setInt(targetLayout, "bottomMargin", parseDimension(context, value)); return;
            case "layout_constraintStart_toStartOf": connect(targetLayout, "startToStart", parseConstraintTarget(context, value)); return;
            case "layout_constraintStart_toEndOf": connect(targetLayout, "startToEnd", parseConstraintTarget(context, value)); return;
            case "layout_constraintEnd_toStartOf": connect(targetLayout, "endToStart", parseConstraintTarget(context, value)); return;
            case "layout_constraintEnd_toEndOf": connect(targetLayout, "endToEnd", parseConstraintTarget(context, value)); return;
            case "layout_constraintLeft_toLeftOf": connect(targetLayout, "leftToLeft", parseConstraintTarget(context, value)); return;
            case "layout_constraintLeft_toRightOf": connect(targetLayout, "leftToRight", parseConstraintTarget(context, value)); return;
            case "layout_constraintRight_toLeftOf": connect(targetLayout, "rightToLeft", parseConstraintTarget(context, value)); return;
            case "layout_constraintRight_toRightOf": connect(targetLayout, "rightToRight", parseConstraintTarget(context, value)); return;
            case "layout_constraintTop_toTopOf": connect(targetLayout, "topToTop", parseConstraintTarget(context, value)); return;
            case "layout_constraintTop_toBottomOf": connect(targetLayout, "topToBottom", parseConstraintTarget(context, value)); return;
            case "layout_constraintBottom_toTopOf": connect(targetLayout, "bottomToTop", parseConstraintTarget(context, value)); return;
            case "layout_constraintBottom_toBottomOf": connect(targetLayout, "bottomToBottom", parseConstraintTarget(context, value)); return;
            case "layout_constraintBaseline_toBaselineOf": connect(targetLayout, "baselineToBaseline", parseConstraintTarget(context, value)); return;
            case "layout_constraintHorizontal_bias": setFloat(targetLayout, "horizontalBias", parseFloat(value)); return;
            case "layout_constraintVertical_bias": setFloat(targetLayout, "verticalBias", parseFloat(value)); return;
            case "layout_constraintHorizontal_chainStyle": setInt(targetLayout, "horizontalChainStyle", parseChainStyle(value)); return;
            case "layout_constraintVertical_chainStyle": setInt(targetLayout, "verticalChainStyle", parseChainStyle(value)); return;
            case "layout_constraintDimensionRatio": setString(targetLayout, "dimensionRatio", value); return;
            case "layout_constraintWidth_percent": setFloat(targetLayout, "matchConstraintPercentWidth", parseFloat(value)); return;
            case "layout_constraintHeight_percent": setFloat(targetLayout, "matchConstraintPercentHeight", parseFloat(value)); return;
            case "layout_constraintGuide_begin": setInt(targetLayout, "guideBegin", parseDimension(context, value)); return;
            case "layout_constraintGuide_end": setInt(targetLayout, "guideEnd", parseDimension(context, value)); return;
            case "layout_constraintGuide_percent": setFloat(targetLayout, "guidePercent", parseFloat(value)); return;
            default:
                // Text styling and any unknown/new MIUI ConstraintLayout attributes remain stock.
        }
    }

    private static int parseConstraintTarget(Context context, String value) {
        if (value == null || "parent".equals(value)) return 0;
        if ("-1".equals(value)) return -1;
        String name = value.startsWith("@") ? value.substring(value.indexOf('/') + 1) : value;
        return id(context, name);
    }

    private static int parseDimension(Context context, String value) {
        String dimension = value.trim();
        if ("wrap_content".equals(dimension)) return -2;
        if ("match_parent".equals(dimension) || "fill_parent".equals(dimension)) return -1;
        if (dimension.startsWith("@dimen/")) return dimen(context, dimension.substring(7));
        float multiplier = 1f;
        if (dimension.endsWith("dip") || dimension.endsWith("dp")) {
            multiplier = context.getResources().getDisplayMetrics().density;
            dimension = dimension.replaceFirst("(dip|dp)$", "");
        } else if (dimension.endsWith("sp")) {
            multiplier = context.getResources().getDisplayMetrics().scaledDensity;
            dimension = dimension.substring(0, dimension.length() - 2);
        } else if (dimension.endsWith("px")) {
            dimension = dimension.substring(0, dimension.length() - 2);
        }
        return Math.round(Float.parseFloat(dimension) * multiplier);
    }

    private static float parseFloat(String value) {
        return Float.parseFloat(value.trim());
    }

    private static int parseChainStyle(String value) {
        if ("spread_inside".equals(value)) return 1;
        if ("packed".equals(value)) return 2;
        return 0;
    }

    private static void setActionConstraints(Context context, Object constraintSet, int[] action, int actions) {
        if (action[0] == 0) {
            return;
        }
        Object first = layout(constraintSet, action[0]);
        connect(first, "topToTop", actions);
        connect(first, "topToBottom", -1);
        connect(first, "bottomToBottom", actions);
        connect(first, "bottomToTop", -1);
        connect(first, "leftToLeft", actions);
        connect(first, "leftToRight", -1);
        connect(first, "rightToLeft", action[1]);
        connect(first, "rightToRight", -1);
        setInt(first, "topMargin", 0);
        setInt(first, "startMargin", 0);
        setInt(first, "horizontalChainStyle", 0);

        for (int index = 1; index < action.length; index++) {
            Object current = layout(constraintSet, action[index]);
            connect(current, "topToTop", action[0]);
            connect(current, "topToBottom", -1);
            connect(current, "bottomToBottom", action[0]);
            connect(current, "bottomToTop", -1);
        }
        setInt(layout(constraintSet, action[1]), "endMargin", dp(context, 5));
        setInt(layout(constraintSet, action[3]), "startMargin", dp(context, 5));
        setInt(layout(constraintSet, action[4]), "endMargin", 0);
    }

    /** Mirrors the reference dex: hide in full AOD and restore as soon as it exits. */
    private static void setAodActionsVisibility(Object controller, boolean inFullAod) {
        if (!hideAodActions) return;
        Object holder = fieldValue(controller, "holder");
        for (int index = 0; index < 5; index++) {
            Object action = fieldValue(holder, "action" + index);
            if (action instanceof View) {
                ((View) action).setVisibility(inFullAod ? View.GONE : View.VISIBLE);
            }
        }
    }

    /** Keeps media transfer hidden through AOD state changes and media-data refreshes. */
    private static void setAodSeamlessVisibility(Object controller, boolean inFullAod) {
        if (!hideAodSeamless) return;
        Object holder = fieldValue(controller, "holder");
        Object seamless = fieldValue(holder, "seamless");
        if (seamless instanceof View) {
            ((View) seamless).setVisibility(inFullAod ? View.GONE : View.VISIBLE);
        }
    }

    /** Applies the same policy to a ConstraintSet loaded while Full AOD is already active. */
    private static void setAodSeamlessConstraintVisibility(Object constraintSet, int seamless) {
        if (hideAodSeamless && inFullAod && seamless != 0) {
            setInt(layout(constraintSet, seamless), "mVisibility", View.GONE);
        }
    }

    /** Copies the island SeekProgressBar's runtime attributes onto the standard media seek bar. */
    private static void configureSeekBar(Object holder) {
        Object seekBar = fieldValue(holder, "seekBar");
        if (!(seekBar instanceof View)) {
            return;
        }
        View view = (View) seekBar;
        view.setFocusable(true);
        invokeBoolean(seekBar, "setIndeterminate", false);
        invokeBoolean(seekBar, "setMirrorForRtl", true);
        invokeInt(seekBar, "setBackgroundPrimaryColor", Color.argb(0x1a, 0xff, 0xff, 0xff));
        invokeInt(seekBar, "setForegroundPrimaryColor", Color.WHITE);
        setInt(seekBar, "mProgressAlpha", 153);
        setInt(seekBar, "mDrawProgressAlpha", 153);
        setInt(seekBar, "mProgressPressedAlpha", 230);
        view.invalidate();
    }

    /**
     * Runtime equivalent of replacing the normal layout's SeekProgressBar with the island one.
     * This runs only after XML inflation and immediately before attach() wires touch handling,
     * avoiding a global LayoutInflater/constructor hook during SystemUI startup.
     */
    private static void replaceNormalSeekBar(Object holder) {
        Object current = fieldValue(holder, "seekBar");
        if (!(current instanceof SeekBar) || intField(current, "mProgressMode", -1) != 0) {
            return;
        }

        View oldView = (View) current;
        if (!(oldView.getParent() instanceof ViewGroup)) {
            return;
        }

        try {
            Object replacement = current.getClass().getConstructor(Context.class)
                    .newInstance(oldView.getContext());
            if (!(replacement instanceof SeekBar)) {
                return;
            }

            SeekBar oldSeekBar = (SeekBar) current;
            SeekBar newSeekBar = (SeekBar) replacement;
            View newView = (View) replacement;
            ViewGroup parent = (ViewGroup) oldView.getParent();
            int position = parent.indexOfChild(oldView);
            ViewGroup.LayoutParams layoutParams = oldView.getLayoutParams();
            if (position < 0 || layoutParams == null) {
                return;
            }

            newView.setId(oldView.getId());
            newView.setLayoutDirection(oldView.getLayoutDirection());
            newView.setVisibility(oldView.getVisibility());
            newView.setEnabled(oldView.isEnabled());
            newView.setAlpha(oldView.getAlpha());
            newView.setContentDescription(oldView.getContentDescription());
            newView.setPaddingRelative(oldView.getPaddingStart(), oldView.getPaddingTop(),
                    oldView.getPaddingEnd(), oldView.getPaddingBottom());
            newSeekBar.setMax(oldSeekBar.getMax());
            newSeekBar.setProgress(oldSeekBar.getProgress());
            newSeekBar.setSecondaryProgress(oldSeekBar.getSecondaryProgress());

            parent.removeViewAt(position);
            parent.addView(newView, position, layoutParams);
            setFieldValue(holder, "seekBar", replacement);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Leave the original widget untouched if the target SystemUI revision differs.
            Log.w(TAG, "Could not replace normal media seek bar", exception);
        }
    }

    private static int id(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    private static int dimen(Context context, String name) {
        int resourceId = context.getResources().getIdentifier(name, "dimen", context.getPackageName());
        return resourceId == 0 ? 0 : context.getResources().getDimensionPixelSize(resourceId);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void setHeight(Object constraintSet, int viewId, int height) {
        setInt(layout(constraintSet, viewId), "mHeight", height);
    }

}
