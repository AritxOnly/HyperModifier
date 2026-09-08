package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.StringReader;
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

    private static final int XML_MEDIA_ISLAND_NORMAL = 0x7f180018;
    private static final int XML_MEDIA_NORMAL = 0x7f180019;

    private static final AtomicBoolean RESOURCE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SYSTEM_UI_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_CORNER_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_DRAWABLE_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PLUGIN_CLASS_LOADER_RESOLVER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FUSION_CARD_HOOKS_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLICATION_SETTINGS_HOOK_INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<ControlCenterSurface> INFLATING_PLUGIN_DRAWABLE = new ThreadLocal<>();

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SYSTEM_UI.equals(packageName) && !SYSTEM_UI_PLUGIN.equals(packageName)
                && !MILINK.equals(packageName)) {
            return;
        }

        try {
            installSettingsLoader();
            installResourceValueHooks();
            if (SYSTEM_UI.equals(packageName)) {
                installSystemUiHooks(param.getClassLoader());
                installSystemUiPluginClassLoaderResolver();
            } else if (SYSTEM_UI_PLUGIN.equals(packageName)) {
                installSystemUiPluginCornerHooks(param.getClassLoader());
            } else {
                installMiLinkFusionCardHooks(param.getClassLoader());
            }
            log(Log.INFO, TAG, "Installed for " + packageName);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Could not install hooks for " + packageName, throwable);
        }
    }

    /** Reads saved appearance options after the target process receives its base context. */
    private void installSettingsLoader() throws NoSuchMethodException {
        if (!SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) return;
        hook(ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class))
                .setId("settings-loader")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    markLoaded((Context) chain.getArg(0));
                    return result;
                });

        // PackageReady is delivered after Application.attach() on some HyperOS builds.  onCreate
        // is still ahead of Control Center view inflation and is the reliable settings hand-off.
        if (APPLICATION_SETTINGS_HOOK_INSTALLED.compareAndSet(false, true)) {
            hook(Application.class.getDeclaredMethod("onCreate"))
                    .setId("application-settings-loader")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        markLoaded((Context) chain.getThisObject());
                        return result;
                    });
        }
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

        hook(Resources.class.getDeclaredMethod("getInteger", int.class))
                .setId("expanded-island-height")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    Object result = chain.proceed();
                    Resources resources = (Resources) chain.getThisObject();
                    String name = resourceEntryName(resources, (Integer) chain.getArg(0));
                    return "expanded_island_height_dp".equals(name) && islandEnabled ? islandHeight : result;
                });
    }

    private void installSystemUiHooks(ClassLoader classLoader) throws Throwable {
        if (!SYSTEM_UI_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        Class<?> constraintSet = Class.forName(
                "androidx.constraintlayout.widget.ConstraintSet", false, classLoader);
        Method load = constraintSet.getDeclaredMethod("load", Context.class, int.class);
        hook(load)
                .setId("media-constraint-set")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    int xmlId = (Integer) chain.getArg(1);
                    if (xmlId == XML_MEDIA_NORMAL || xmlId == XML_MEDIA_ISLAND_NORMAL) {
                        patchMediaConstraintSet((Context) chain.getArg(0), chain.getThisObject(),
                                xmlId == XML_MEDIA_ISLAND_NORMAL);
                    }
                    return result;
                });

        Class<?> controller = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
                false, classLoader);
        Method onFullAodStateChanged = controller.getDeclaredMethod("onFullAodStateChanged", boolean.class);
        hook(onFullAodStateChanged)
                .setId("full-aod-actions")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    inFullAod = (Boolean) chain.getArg(0);
                    Object result = chain.proceed();
                    setAodActionsVisibility(chain.getThisObject(), inFullAod);
                    setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                    return result;
                });

        for (Method method : controller.getDeclaredMethods()) {
            if (!"setSeamless".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            hook(method)
                    .setId("full-aod-seamless-visibility")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        // A media-data refresh normally makes the transfer entry visible again.
                        // Reapply the AOD policy after every such refresh.
                        setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                        return result;
                    });
        }

        Class<?> holder = Class.forName(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder",
                false, classLoader);
        Method attach = controller.getDeclaredMethod("attach", holder);
        hook(attach)
                .setId("media-seekbar-style")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (islandProgressBar) replaceNormalSeekBar(chain.getArg(0));
                    Object result = chain.proceed();
                    if (islandProgressBar) configureSeekBar(chain.getArg(0));
                    setAodSeamlessVisibility(chain.getThisObject(), inFullAod);
                    return result;
                });
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
                    if (requestedName instanceof String && isPluginCornerClass((String) requestedName)
                            && result instanceof Class<?>) {
                        ClassLoader pluginClassLoader = ((Class<?>) result).getClassLoader();
                        if (pluginClassLoader != null) {
                            installSystemUiPluginCornerHooks(pluginClassLoader);
                        }
                    }
                    return result;
                });
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
        if (!PLUGIN_CORNER_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.qs.tileview.QSTileItemIconView",
                "setCornerRadius", ControlCenterSurface.TILE);
        hookPluginCornerSetter(classLoader,
                "miui.systemui.controlcenter.qs.tileview.QSCardItemView",
                "setCornerRadius", ControlCenterSurface.CARD);
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

    private void hookPluginCornerSetter(ClassLoader classLoader, String className, String methodName,
                                        ControlCenterSurface surface) {
        hookPluginCornerSetter(classLoader, className, methodName, surface, false);
    }

    /** Advanced-only setters are for private detail-panel dimensions, not universal_corner_radius. */
    private void hookPluginAdvancedCornerSetter(ClassLoader classLoader, String className,
                                                String methodName, ControlCenterSurface surface) {
        hookPluginCornerSetter(classLoader, className, methodName, surface, true);
    }

    private void hookPluginCornerSetter(ClassLoader classLoader, String className, String methodName,
                                        ControlCenterSurface surface, boolean advancedOnly) {
        try {
            Method method = Class.forName(className, false, classLoader)
                    .getMethod(methodName, float.class);
            hook(method)
                    .setId("plugin-" + className + "-" + methodName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (!controlCenterEnabled || (advancedOnly && !advancedControlCenterCorners)) {
                            return chain.proceed();
                        }
                        // Secondary brightness tiles (auto brightness, reading mode, etc.) are
                        // non-card icon views.  MIUI deliberately gives them half their tile
                        // size, i.e. a circle.  Replacing that value turns them into rounded
                        // rectangles after a panel refresh.  Only card tiles use the configurable
                        // corner radius.
                        if (surface == ControlCenterSurface.TILE
                                && !booleanDeclaredField(chain.getThisObject(), "card", true)) {
                            return chain.proceed();
                        }
                        Object argument = chain.getArg(0);
                        float originalPixels = argument instanceof Float ? (Float) argument : 0f;
                        float replacement = cornerPixels(chain.getThisObject(), surface, originalPixels);
                        return chain.proceed(new Object[]{replacement});
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Control-centre hook unavailable: " + className + '#' + methodName, throwable);
        }
    }

    /**
     * The secondary volume panel owns a SystemUI VolumeColumn rather than the plugin's
     * ToggleSliderView.  VolumeColumn asks this resolver again for every panel show and during
     * its transition animation, so changing only its initial drawable is immediately overwritten.
     */
    private void hookSecondaryVolumeRadiusResolver(ClassLoader classLoader) {
        try {
            Class<?> resolver = Class.forName(
                    "com.android.systemui.miui.volume.VolumeColumnRes", false, classLoader);
            Method getRadius = resolver.getDeclaredMethod(
                    "getRadius", Context.class, boolean.class, boolean.class);
            hook(getRadius)
                    .setId("secondary-volume-slider-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ensureLoaded();
                        // The first flag is true for the regular system volume dialog.  Limit
                        // this replacement to the Control Center-owned panel so normal volume
                        // dialogs retain MIUI's own geometry.
                        Object showDialog = chain.getArg(1);
                        if (!controlCenterEnabled || !(showDialog instanceof Boolean)
                                || (Boolean) showDialog) {
                            return result;
                        }
                        Context context = (Context) chain.getArg(0);
                        return Math.round(controlCenterRadius(ControlCenterSurface.DETAIL_SLIDER)
                                * context.getResources().getDisplayMetrics().density);
                    });
        } catch (Throwable throwable) {
            Log.w(TAG, "Secondary volume slider hook unavailable", throwable);
        }
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
                        patchPluginDrawable((Resources) chain.getThisObject(), (Integer) chain.getArg(0), result);
                        return result;
                    });
            hook(Resources.class.getDeclaredMethod("getDrawable", int.class, Resources.Theme.class))
                    .setId("plugin-drawable-public-themed")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        patchPluginDrawable((Resources) chain.getThisObject(), (Integer) chain.getArg(0), result);
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

        if (!mediaEnabled) return;
        // ConstraintSet.load() only accepts an APK resource id, therefore an XML string saved by
        // the companion app is parsed after the stock set has loaded.  The custom XML is limited
        // to the normal media session; the island set retains its dedicated reference layout.
        if (!island && customMediaConstraintSetEnabled
                && applyCustomMediaConstraintSet(context, constraintSet, customMediaConstraintSetXml)) {
            setAodSeamlessConstraintVisibility(constraintSet, seamless);
            return;
        }
        setHeight(constraintSet, mediaBackground, dp(context, Math.round(expandedHeight)));
        setHeight(constraintSet, mediaBackgroundFallback, dp(context, Math.round(expandedHeight)));

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
