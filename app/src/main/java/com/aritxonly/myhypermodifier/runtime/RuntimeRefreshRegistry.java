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

/** Shared weak-reference state used to refresh runtime surfaces after settings load. */
final class RuntimeRefreshRegistry {
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
    static final Map<Object, PendingMediaConstraint> PENDING_MEDIA_CONSTRAINTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_ROW_BOTTOM_MARGINS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> PIN_GLASS_ORIGINAL_CONTAINER_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> MILINK_FUSION_BACKGROUND_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    static void rememberControlCenterSetter(View view, Method method) {
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
    static void refreshViewsAfterSettingsLoad() {
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

    static void applyControlCenterSetters(View view, List<Method> methods) {
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

    static void rememberPluginDrawable(Object candidate, ControlCenterSurface surface) {
        if (!(candidate instanceof Drawable) || surface == null) return;
        synchronized (PENDING_PLUGIN_DRAWABLES) {
            PENDING_PLUGIN_DRAWABLES.put((Drawable) candidate, surface);
        }
    }

    static final class PendingMediaConstraint {
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


}
