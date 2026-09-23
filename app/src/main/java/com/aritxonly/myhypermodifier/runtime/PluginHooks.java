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

import static com.aritxonly.myhypermodifier.RuntimeRefreshRegistry.*;

/** Runtime collaborator extracted from MyHyperModifier. */
final class PluginHooks {
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
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> MILINK_FUSION_BACKGROUND_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final XposedModule module;

    PluginHooks(XposedModule module) {
        this.module = module;
    }

    /**
     * MIUISystemUIPlugin normally runs inside the SystemUI host process but is loaded by its own
     * PathClassLoader.  Consequently PackageReady only gives us SystemUI's class loader on many
     * HyperOS builds; trying to hook plugin classes from it silently misses every slider class.
     * Resolve the actual loader when one of the Control Center view classes is first loaded.
     */
    void installSystemUiPluginClassLoaderResolver() throws NoSuchMethodException {
        if (!PLUGIN_CLASS_LOADER_RESOLVER_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Method loadClass = ClassLoader.class.getDeclaredMethod(
                "loadClass", String.class, boolean.class);
        module.hook(loadClass)
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
                                new SystemUiRuntimeHooks(module).installSystemUiHookForLoadedClass(className, loadedClass);
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
    void installSystemUiPluginLoaderFactoryHook(ClassLoader classLoader) {
        if (!PLUGIN_FACTORY_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> factory = Class.forName(
                    "com.android.systemui.shared.plugins.PluginInstance$PluginFactory",
                    false, classLoader);
            Method createClassLoader = factory.getDeclaredMethod("createClassLoader");
            module.hook(createClassLoader)
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
    void installMiLinkFusionCardHooks(ClassLoader classLoader) {
        if (!MILINK_FUSION_CARD_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> cardLayout = Class.forName(
                    "com.xiaomi.smarthome.fusion.ui.RoundedClipFrameLayout", false, classLoader);
            Method setCornerRadius = cardLayout.getDeclaredMethod("setCornerRadius", float.class);
            module.hook(setCornerRadius)
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
    void installSystemUiPluginCornerHooks(ClassLoader classLoader) {
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
            module.hook(getRadius)
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
            module.hook(method)
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
            module.hook(updateBackground)
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

    static Object declaredFieldValue(Object target, String fieldName) {
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
            module.hook(GradientDrawable.class.getDeclaredMethod("setCornerRadius", float.class))
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
            module.hook(GradientDrawable.class.getDeclaredMethod("setCornerRadii", float[].class))
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
            module.hook(Resources.class.getDeclaredMethod("getDrawable", int.class))
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
            module.hook(Resources.class.getDeclaredMethod("getDrawable", int.class, Resources.Theme.class))
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
                module.hook(method)
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


}
