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

import static com.aritxonly.myhypermodifier.MediaConstraintCustomizer.*;
import static com.aritxonly.myhypermodifier.RuntimeRefreshRegistry.*;

/** Runtime collaborator extracted from MyHyperModifier. */
final class SystemUiRuntimeHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String SYSTEM_UI_PLUGIN = "miui.systemui.plugin";
    private static final String MILINK = "com.milink.service";
    private static final String MILINK_FUSION_ACTIVITY =
            "com.miui.circulate.world.CirculateWorldActivity";
    private static final int MILINK_FUSION_MATERIAL_BLUR_RADIUS = 110;
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
    private static final AtomicBoolean CONTROL_CENTER_MILINK_MATERIAL_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean CONTROL_CENTER_MILINK_MATERIAL_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean CONTROL_CENTER_MILINK_MATERIAL_FAILURE_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_BLUR_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_OWNER_HOOK_INSTALLED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_OWNER_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_BLUR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_BACKGROUND_RADIUS_FAILURE_LOGGED =
            new AtomicBoolean();
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
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> MILINK_FUSION_BACKGROUND_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, int[]> CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final XposedModule module;

    SystemUiRuntimeHooks(XposedModule module) {
        this.module = module;
    }

    void installSystemUiHooks(ClassLoader classLoader) throws Throwable {
        if (!SYSTEM_UI_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            // This class is present in the target build and is the password-page control point.
            // Register it before optional media/island classes so their startup timing can never
            // suppress the credential hook.
            if (LOCKSCREEN_PASSWORD_BACKGROUND_EXPERIMENT_ENABLED) {
                new LockscreenHooks(module).installLockscreenBouncerBlurCompatHook(classLoader);
                new LockscreenHooks(module).installLockscreenPasswordWallpaperRatioHook(classLoader);
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
            installControlCenterMiLinkBackgroundMaterialHook(classLoader);
            new LockscreenHooks(module).installLockscreenNotificationHooks(classLoader);
            new LockscreenHooks(module).installLockscreenFingerprintHooks(classLoader);
            new LockscreenHooks(module).installLockscreenCredentialHooks(classLoader);
            StatusBarNetworkType.install(module, classLoader);
        } catch (Throwable throwable) {
            // Do not consume the one-shot marker if a boot-time class is not visible yet. The
            // class-load observer can then install its hook when the real class appears.
            SYSTEM_UI_HOOKS_INSTALLED.set(false);
            throw throwable;
        }
    }

    /** Installs only methods declared by a class that has already been returned by loadClass. */
    void installSystemUiHookForLoadedClass(String className, Class<?> loadedClass) {
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
            module.hook(apply)
                    .setId(dark ? "heads-up-glass-dark-parameters"
                            : "heads-up-glass-parameters")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        if (headsUpGlassParametersEnabled) {
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
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Adjacent HyperOS versions can rename these view bridges; stock rendering stays intact.
        }
    }

    /**
     * Scale SystemUI shade and Control Center blur calls. Xiaomi uses different stock radii on
     * each surface, so retaining the original argument and multiplying it keeps their relative
     * appearance stable across device builds.
     */
    void installGlobalBackgroundBlurHook() {
        if (!GLOBAL_BACKGROUND_BLUR_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Method method = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            module.hook(method)
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

    /**
     * The installed SystemUI uses the same blend colors as stock MiLink for both classic and
     * bionics material, but its Control Center backdrop uses a 100px blur baseline and a
     * background scale ratio. MiLink's backdrop uses 110px and does not set that scale. Alter
     * only the Control Center BlurProvider, leaving notification shade and card blur untouched.
     */
    private void installControlCenterMiLinkBackgroundMaterialHook(ClassLoader classLoader) {
        if (!CONTROL_CENTER_MILINK_MATERIAL_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> provider = Class.forName(
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlurProvider",
                    false, classLoader);
            Field viewField = provider.getDeclaredField("view");
            Field radiusField = provider.getDeclaredField("maxRadius");
            Field smallGlassRadiusField = provider.getDeclaredField("maxGlassSmallBlurRadius");
            Field bigGlassRadiusField = provider.getDeclaredField("maxGlassBigBlurRadius");
            Field scaleField = provider.getDeclaredField("enableScale");
            viewField.setAccessible(true);
            radiusField.setAccessible(true);
            smallGlassRadiusField.setAccessible(true);
            bigGlassRadiusField.setAccessible(true);
            scaleField.setAccessible(true);
            module.hook(provider.getDeclaredMethod("setBlurRatio", float.class))
                    .setId("control-center-follow-milink-background-material")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object instance = chain.getThisObject();
                            Object view = viewField.get(instance);
                            if (view instanceof View && isControlCenterBackgroundView((View) view)) {
                                ensureLoaded();
                                if (controlCenterFollowMiLinkBackgroundMaterial) {
                                    synchronized (CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS) {
                                        if (!CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS
                                                .containsKey(instance)) {
                                            CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS.put(
                                                    instance, new int[]{radiusField.getInt(instance),
                                                            smallGlassRadiusField.getInt(instance),
                                                            bigGlassRadiusField.getInt(instance),
                                                            scaleField.getBoolean(instance) ? 1 : 0});
                                        }
                                    }
                                    radiusField.setInt(instance, MILINK_FUSION_MATERIAL_BLUR_RADIUS);
                                    smallGlassRadiusField.setInt(instance,
                                            MILINK_FUSION_MATERIAL_BLUR_RADIUS);
                                    bigGlassRadiusField.setInt(instance,
                                            MILINK_FUSION_MATERIAL_BLUR_RADIUS);
                                    scaleField.setBoolean(instance, false);
                                    if (CONTROL_CENTER_MILINK_MATERIAL_LOGGED
                                            .compareAndSet(false, true)) {
                                        Log.i(TAG, "Control Center backdrop follows stock MiLink "
                                                + "material: radius 110, background scale off");
                                    }
                                } else {
                                    int[] defaults;
                                    synchronized (CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS) {
                                        defaults = CONTROL_CENTER_BACKGROUND_MATERIAL_DEFAULTS
                                                .remove(instance);
                                    }
                                    if (defaults != null) {
                                        radiusField.setInt(instance, defaults[0]);
                                        smallGlassRadiusField.setInt(instance, defaults[1]);
                                        bigGlassRadiusField.setInt(instance, defaults[2]);
                                        scaleField.setBoolean(instance, defaults[3] != 0);
                                    }
                                }
                            }
                        } catch (ReflectiveOperationException | RuntimeException exception) {
                            if (CONTROL_CENTER_MILINK_MATERIAL_FAILURE_LOGGED
                                    .compareAndSet(false, true)) {
                                Log.w(TAG, "Control Center MiLink material adjustment unavailable",
                                        exception);
                            }
                        }
                        return chain.proceed();
                    });
            Log.i(TAG, "Control Center MiLink background material hook installed");
        } catch (Throwable throwable) {
            CONTROL_CENTER_MILINK_MATERIAL_HOOK_INSTALLED.set(false);
            Log.w(TAG, "Control Center MiLink background material hook unavailable", throwable);
        }
    }

    private static boolean isControlCenterBackgroundView(View view) {
        if ("com.miui.systemui.controlcenter.container.ControlCenterContainer"
                .equals(view.getClass().getName())) return true;
        try {
            return view.getId() != View.NO_ID && "control_center_container".equals(
                    view.getResources().getResourceEntryName(view.getId()));
        } catch (Resources.NotFoundException ignored) {
            return false;
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
     * SurfaceControl fallback. Both paths converge on BlurControllerImpl.setBlurRatio. On the
     * material path the ratio controls both radius and blend alpha, so leave the ratio alone and
     * override only the resulting radius, matching the SystemUI slider's behavior. The material
     * and its background scale remain stock MiLink, which is the opt-in Control Center baseline.
     */
    void installMiLinkFusionBackgroundBlurHook(ClassLoader classLoader) {
        installMiLinkFusionBackgroundOwnerHook(classLoader);
        if (!MILINK_FUSION_BACKGROUND_BLUR_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Method radiusMethod = null;
            try {
                radiusMethod = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            } catch (NoSuchMethodException exception) {
                Log.w(TAG, "MiLink background radius API unavailable; using ratio fallback");
            }
            final Method backgroundRadiusMethod = radiusMethod;
            Class<?> blurController = Class.forName(
                    "com.miui.circulate.world.utils.BlurUtils$BlurControllerImpl",
                    false, classLoader);
            Method setBlurRatio = blurController.getDeclaredMethod("setBlurRatio", float.class);
            module.hook(setBlurRatio)
                    .setId("milink-fusion-background-blur")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object target = PluginHooks.declaredFieldValue(chain.getThisObject(), "b");
                        Object ratio = chain.getArg(0);
                        if (!(target instanceof View) || !(ratio instanceof Float)
                                || !isMiLinkFusionDeviceCenterView((View) target)) {
                            return chain.proceed();
                        }
                        int percent = globalBackgroundBlurPercent;
                        float original = (Float) ratio;
                        float adjusted = Math.max(0f, original * percent / 100f);
                        // MaterialUtils.z uses 110 * ratio for radius, but its blend alpha uses
                        // the same ratio. Replacing the ratio would unintentionally wash out the
                        // tint when the global blur slider is below 100%.
                        boolean materialPath = chain.getThisObject().getClass().getName()
                                .equals("com.miui.circulate.world.utils.BlurUtils$f")
                                && backgroundRadiusMethod != null;
                        Object result = materialPath || percent == 100 ? chain.proceed()
                                : chain.proceed(new Object[]{adjusted});
                        if (materialPath && percent != 100) {
                            int stockRadius = Math.max(0,
                                    (int) (MILINK_FUSION_MATERIAL_BLUR_RADIUS * original));
                            int radius = Math.max(0, Math.min(500,
                                    Math.round(stockRadius * percent / 100f)));
                            try {
                                backgroundRadiusMethod.invoke(target, radius);
                            } catch (ReflectiveOperationException | RuntimeException exception) {
                                if (MILINK_FUSION_BACKGROUND_RADIUS_FAILURE_LOGGED
                                        .compareAndSet(false, true)) {
                                    Log.w(TAG, "MiLink background radius override unavailable",
                                            exception);
                                }
                            }
                        }
                        if (percent != 100
                                && MILINK_FUSION_BACKGROUND_BLUR_LOGGED.compareAndSet(false, true)) {
                            Log.i(TAG, "Fusion Device Center blur " + original + " -> " + adjusted
                                    + " (global " + percent + "%, material=" + materialPath + ")");
                        }
                        return result;
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
            module.hook(onStart)
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
            module.hook(method).setId("media-constraint-set")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        int xmlId = (Integer) chain.getArg(1);
                        if (xmlId == XML_MEDIA_NORMAL || xmlId == XML_MEDIA_ISLAND_NORMAL) {
                            Context context = (Context) chain.getArg(0);
                            boolean island = xmlId == XML_MEDIA_ISLAND_NORMAL;
                            synchronized (PENDING_MEDIA_CONSTRAINTS) {
                                PENDING_MEDIA_CONSTRAINTS.put(chain.getThisObject(),
                                        new RuntimeRefreshRegistry.PendingMediaConstraint(context, island));
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
            module.hook(method).setId("media-constraint-set-apply")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        RuntimeRefreshRegistry.PendingMediaConstraint pending;
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
            module.hook(method).setId("media-island-measured-height")
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
            module.hook(method).setId("full-aod-actions")
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
            module.hook(method).setId("full-aod-seamless-visibility")
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
            module.hook(method).setId("media-seekbar-style")
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


}
