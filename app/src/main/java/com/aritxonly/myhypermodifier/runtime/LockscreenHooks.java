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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

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


/** Runtime collaborator extracted from MyHyperModifier. */
final class LockscreenHooks {
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
    private static final String KEYGUARD_PIN_VIEW_CONTROLLER =
            "com.android.keyguard.KeyguardPinViewController";
    private static final String KEYGUARD_PASSWORD_VIEW_CONTROLLER =
            "com.android.keyguard.KeyguardPasswordViewController";
    private static final String KEYGUARD_SECURITY_CONTAINER =
            "com.android.keyguard.KeyguardSecurityContainer";
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
    private static final Set<Object> CREDENTIAL_SUPPRESSED_FOD_MANAGERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Object> FINGERPRINT_MODE_REQUESTED_MANAGERS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<View, WeakReference<CredentialSwitchState>> CREDENTIAL_SWITCH_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
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

    LockscreenHooks(XposedModule module) {
        this.module = module;
    }

    private boolean claimSystemUiMethod(Method method) {
        return INSTALLED_SYSTEM_UI_METHOD_HOOKS.add(method);
    }

    /**
     * HyperOS normally lowers lock-screen notifications whenever an enrolled UDFPS is present.
     * Keeping the original Flow output retains that behavior; disabling the setting replaces just
     * its UDFPS inputs, so the standard notification position is emitted without breaking the
     * Kotlin coroutine that owns the position calculation.
     */
    void installLockscreenNotificationHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_NOTIFICATION_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> shelfSpaceFlow = Class.forName(
                    "com.android.systemui.statusbar.notification.stack.domain.interactor."
                            + "SharedNotificationContainerInteractor$useExtraShelfSpace$1",
                    false, classLoader);
            module.hook(shelfSpaceFlow.getDeclaredMethod("invokeSuspend", Object.class))
                    .setId("lockscreen-notification-fod-shelf-space")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        // HyperOS 4 sinks notifications when extra shelf space is disabled.
                        return sinkLockscreenNotificationsForFingerprint ? false : chain.proceed();
                    });

            Class<?> notificationPositionFlow = loadFirstAvailableClass(classLoader,
                    "com.android.keyguard.panel.KeyguardPanelViewController"
                            + "$nsslLockYPosition_delegate$lambda$102$$inlined$combine$1$3",
                    "com.android.keyguard.panel.KeyguardPanelViewController"
                            + "$nsslLockYPosition_delegate$lambda$104$$inlined$combine$1$3",
                    "com.android.keyguard.panel.KeyguardPanelViewController"
                            + "$nsslLockYPosition_delegate$lambda$106$$inlined$combine$1$3");
            Field enrolledValues = notificationPositionFlow.getDeclaredField("L$1");
            enrolledValues.setAccessible(true);
            module.hook(notificationPositionFlow.getDeclaredMethod("invokeSuspend", Object.class))
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
            module.log(Log.INFO, TAG, "Installed lockscreen notification-position hooks");
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Could not install lockscreen notification UDFPS hooks", throwable);
        }
    }

    /** Keep the global visual-only option separate from credential-page FOD window suppression. */
    void installLockscreenFingerprintHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_FINGERPRINT_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> iconClass = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiGxzwIconView", false, classLoader);
            Method dismissIcon = iconClass.getMethod("dismissFingerpirntIcon");
            Class<?> managerClass = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiGxzwManager", false, classLoader);
            Class<?> factoryClass = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiFingerPrintFactory", false, classLoader);
            Method getFingerprintManager = factoryClass.getMethod("getFingerPrintManager");
            Field bouncer = managerClass.getDeclaredField("mBouncer");
            Field securityMode = managerClass.getDeclaredField("mSecurityMode");
            bouncer.setAccessible(true);
            securityMode.setAccessible(true);
            Method dismissGxzwView = managerClass.getDeclaredMethod("dismissGxzwView");
            Method onKeyguardShow = managerClass.getDeclaredMethod("onKeyguardShow");
            module.hook(dismissIcon)
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
                module.hook(method)
                        .setId("lockscreen-fingerprint-visual-" + hookIndex)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            ensureLoaded();
                            Object result = chain.proceed();
                            boolean keepForAod = shouldKeepFingerprintIconOnAod(
                                    chain.getThisObject());
                            if (hideLockscreenFingerprintIcon && !keepForAod) {
                                try {
                                    Object manager = getFingerprintManager.invoke(null);
                                    if (!FINGERPRINT_MODE_REQUESTED_MANAGERS.contains(manager)) {
                                        dismissIcon.invoke(chain.getThisObject());
                                    }
                                } catch (ReflectiveOperationException | RuntimeException ignored) {
                                    // A changed FOD manager keeps the stock behavior on this build.
                                }
                            }
                            return result;
                        });
            }
            if (hookedMethods == 0) {
                throw new NoSuchMethodException("No MiuiGxzwIconView display methods found");
            }
            module.hook(managerClass.getDeclaredMethod("showGxzwView", boolean.class))
                    .setId("lockscreen-password-fingerprint-window-show")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object manager = chain.getThisObject();
                        try {
                            if (shouldSuppressCredentialFod(manager, bouncer, securityMode)) {
                                CREDENTIAL_SUPPRESSED_FOD_MANAGERS.add(manager);
                                return null;
                            }
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // An unknown manager revision retains its original show behavior.
                        }
                        return chain.proceed();
                    });
            module.hook(managerClass.getDeclaredMethod("updateGxzwState"))
                    .setId("lockscreen-password-fingerprint-window-state")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object result = chain.proceed();
                        Object manager = chain.getThisObject();
                        try {
                            if (shouldSuppressCredentialFod(manager, bouncer, securityMode)) {
                                CREDENTIAL_SUPPRESSED_FOD_MANAGERS.add(manager);
                                if (booleanDeclaredField(manager, "mShowed", false)) {
                                    dismissGxzwView.invoke(manager);
                                }
                            } else if (CREDENTIAL_SUPPRESSED_FOD_MANAGERS.remove(manager)
                                    && !booleanDeclaredField(manager, "mDozing", false)) {
                                // The bouncer-state callback has returned to the lockscreen.
                                // Re-enter Xiaomi's normal detection-aware window show path.
                                onKeyguardShow.invoke(manager);
                            }
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // Adjacent builds can change FOD manager methods or fields.
                        }
                        return result;
                    });
            module.hook(managerClass.getDeclaredMethod("onKeyguardHide"))
                    .setId("lockscreen-password-fingerprint-window-cleanup")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object manager = chain.getThisObject();
                        CREDENTIAL_SUPPRESSED_FOD_MANAGERS.remove(manager);
                        FINGERPRINT_MODE_REQUESTED_MANAGERS.remove(manager);
                        return chain.proceed();
                    });
            module.log(Log.INFO, TAG, "Installed " + hookedMethods + " lockscreen fingerprint hook(s)");
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Could not install lockscreen fingerprint hooks", throwable);
        }
    }

    private static boolean shouldKeepFingerprintIconOnAod(Object iconView) {
        return hideLockscreenFingerprintIcon
                && showLockscreenFingerprintIconOnAod
                && booleanDeclaredField(iconView, "mDozing", false);
    }

    private static boolean shouldSuppressCredentialFod(
            Object manager, Field bouncer, Field securityMode)
            throws ReflectiveOperationException {
        if (!lowerLockscreenPasswordPage || manager == null || !bouncer.getBoolean(manager)
                || booleanDeclaredField(manager, "mDozing", false)) return false;
        String mode = String.valueOf(securityMode.get(manager));
        return ("PIN".equals(mode) || "Password".equals(mode))
                && !FINGERPRINT_MODE_REQUESTED_MANAGERS.contains(manager);
    }

    /**
     * The coroutine values above are only inputs to this MIUI compatibility bridge. Hooking the
     * bridge gives us a non-coroutine fallback at the exact framework call that applies a blur
     * radius. The stack guard retains the scope to the password-page bouncer consumer.
     */
    void installLockscreenBouncerBlurCompatHook(ClassLoader classLoader) {
        if (!LOCKSCREEN_BOUNCER_BLUR_COMPAT_HOOK_INSTALLED.compareAndSet(false, true)) return;
        Method method = null;
        try {
            Class<?> compat = Class.forName(MI_BLUR_COMPAT, false, classLoader);
            method = compat.getDeclaredMethod("setMiBackgroundBlurRadiusCompat",
                    int.class, View.class);
            if (!claimSystemUiMethod(method)) return;
            module.hook(method)
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
    void installLockscreenPasswordWallpaperRatioHook(ClassLoader classLoader) {
        if (!LOCKSCREEN_PASSWORD_WALLPAPER_RATIO_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> ratioLambda = Class.forName(KEYGUARD_WALLPAPER_BLUR_RATIO_LAMBDA,
                    false, classLoader);
            Method invokeSuspend = ratioLambda.getDeclaredMethod("invokeSuspend", Object.class);
            if (!claimSystemUiMethod(invokeSuspend)) return;
            // Do not call deoptimize() here. On some LSPosed/ART combinations it rejects these
            // generated coroutine methods before a hook has been registered, which previously
            // caused the whole installer to return without installing any of its hooks.
            module.hook(invokeSuspend)
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
            module.hook(invoke)
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
            module.hook(bouncerInvokeSuspend)
                    .setId("lockscreen-password-bouncer-blur-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Integer.valueOf(0));

            Method bouncerInvoke = bouncerRadiusLambda.getDeclaredMethod(
                    "invoke", Object.class, Object.class, Object.class);
            if (!claimSystemUiMethod(bouncerInvoke)) return;
            module.hook(bouncerInvoke)
                    .setId("lockscreen-password-bouncer-blur-radius-bridge")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> Integer.valueOf(0));

            // This collector is the final call site: its class-id 1 branch takes the computed
            // Integer and calls setMiBackgroundBlurRadiusCompat on bouncerContainer. Intercepting
            // it covers an already-created/inlined Flow instance as well as future coroutines.
            Class<?> collector = Class.forName(KEYGUARD_BLUR_COLLECTOR, false, classLoader);
            Method emit = findDeclaredMethod(collector, "emit", 2);
            if (!claimSystemUiMethod(emit)) return;
            module.hook(emit)
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
    void installLockscreenCredentialHooks(ClassLoader classLoader) {
        if (!LOCKSCREEN_CREDENTIAL_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        int hooked = 0;
        try {
            hooked += hookLockscreenCredentialInflation(classLoader, KEYGUARD_PIN_VIEW, true) ? 1 : 0;
            hooked += hookLockscreenCredentialInflation(classLoader, KEYGUARD_PASSWORD_VIEW, false) ? 1 : 0;
            hooked += hookLockscreenCredentialControllerSafely(
                    classLoader, KEYGUARD_PIN_VIEW_CONTROLLER, true);
            hooked += hookLockscreenCredentialControllerSafely(
                    classLoader, KEYGUARD_PASSWORD_VIEW_CONTROLLER, false);
            try {
                hooked += hookLockscreenCredentialSwipe(classLoader) ? 1 : 0;
            } catch (Throwable throwable) {
                module.log(Log.WARN, TAG, "Credential fingerprint swipe unavailable", throwable);
            }
            if (hooked == 0) throw new NoSuchMethodException("No keyguard credential inflation hook");
            module.log(Log.INFO, TAG, "Installed " + hooked + " lockscreen credential hook(s)");
        } catch (Throwable throwable) {
            LOCKSCREEN_CREDENTIAL_HOOKS_INSTALLED.set(false);
            module.log(Log.WARN, TAG, "Could not install lockscreen credential hooks", throwable);
        }
    }

    private int hookLockscreenCredentialControllerSafely(
            ClassLoader classLoader, String className, boolean pin) {
        try {
            return hookLockscreenCredentialController(classLoader, className, pin) ? 1 : 0;
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, className + " fingerprint switch unavailable", throwable);
            return 0;
        }
    }

    private boolean hookLockscreenCredentialInflation(
            ClassLoader classLoader, String className, boolean pin) throws Throwable {
        Class<?> credentialClass = Class.forName(className, false, classLoader);
        Method onFinishInflate = findMethodInHierarchy(credentialClass, "onFinishInflate");
        if (!claimSystemUiMethod(onFinishInflate)) return false;
        module.hook(onFinishInflate)
                .setId(pin ? "lockscreen-pin-credential" : "lockscreen-password-credential")
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
        Method updatePositionForFod = credentialClass.getDeclaredMethod("updatePositionForFod");
        if (claimSystemUiMethod(updatePositionForFod)) {
            module.hook(updatePositionForFod)
                    .setId(pin ? "lockscreen-pin-lower-position"
                            : "lockscreen-password-lower-position")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ensureLoaded();
                        if (lowerLockscreenPasswordPage && chain.getThisObject() instanceof View) {
                            lowerLockscreenCredential((View) chain.getThisObject(), pin);
                        }
                        return result;
                    });
        }
        Method startAppearAnimation = credentialClass.getDeclaredMethod("startAppearAnimation");
        if (claimSystemUiMethod(startAppearAnimation)) {
            module.hook(startAppearAnimation)
                    .setId(pin ? "lockscreen-pin-fingerprint-page-reset"
                            : "lockscreen-password-fingerprint-page-reset")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object value = chain.getThisObject();
                        if (value instanceof View) {
                            CredentialSwitchState state = credentialSwitchState((View) value);
                            if (state != null) state.returnToPassword(false);
                        }
                        return chain.proceed();
                    });
        }
        return true;
    }

    private boolean hookLockscreenCredentialController(
            ClassLoader classLoader, String className, boolean pin) throws Throwable {
        Class<?> controllerClass = Class.forName(className, false, classLoader);
        Method onViewAttached = controllerClass.getDeclaredMethod("onViewAttached");
        if (!claimSystemUiMethod(onViewAttached)) return false;
        module.hook(onViewAttached)
                .setId(pin ? "lockscreen-pin-fingerprint-switch"
                        : "lockscreen-password-fingerprint-switch")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object controller = chain.getThisObject();
                    Object value = readFieldInHierarchy(controller, "mView");
                    if (value instanceof View) {
                        View credentialView = (View) value;
                        onLoaded(() -> credentialView.post(() ->
                                installCredentialFingerprintSwitch(
                                        credentialView, classLoader, pin)));
                    }
                    return result;
                });
        return true;
    }

    private boolean hookLockscreenCredentialSwipe(ClassLoader classLoader) throws Throwable {
        Class<?> containerClass = Class.forName(KEYGUARD_SECURITY_CONTAINER, false, classLoader);
        Method dispatchTouchEvent = containerClass.getDeclaredMethod(
                "dispatchTouchEvent", MotionEvent.class);
        Class<?> flipperClass = Class.forName(
                "com.android.keyguard.KeyguardSecurityViewFlipper", false, classLoader);
        Method getSecurityView = flipperClass.getMethod("getSecurityView");
        if (!claimSystemUiMethod(dispatchTouchEvent)) return false;
        module.hook(dispatchTouchEvent)
                .setId("lockscreen-credential-fingerprint-swipe")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ensureLoaded();
                    if (!lowerLockscreenPasswordPage) return chain.proceed();
                    try {
                        Object flipper = readFieldInHierarchy(
                                chain.getThisObject(), "mSecurityViewFlipper");
                        Object current = flipper == null ? null : getSecurityView.invoke(flipper);
                        if (current instanceof View) {
                            CredentialSwitchState state = credentialSwitchState((View) current);
                            if (state != null && state.handleSwipe((MotionEvent) chain.getArg(0),
                                    (View) chain.getThisObject())) return true;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // A changed bouncer view leaves stock touch dispatch intact.
                    }
                    return chain.proceed();
                });
        return true;
    }

    private static CredentialSwitchState credentialSwitchState(View view) {
        WeakReference<CredentialSwitchState> reference = CREDENTIAL_SWITCH_STATES.get(view);
        return reference == null ? null : reference.get();
    }

    private static void installCredentialFingerprintSwitch(
            View credentialView, ClassLoader classLoader, boolean pin) {
        CredentialSwitchState previous = credentialSwitchState(credentialView);
        if (previous != null) previous.close();
        if (!lowerLockscreenPasswordPage || !credentialView.isAttachedToWindow()) return;
        View cancelButton = findSystemUiView(credentialView, "cancel_button");
        if (!(cancelButton instanceof TextView)) return;
        String[] keyboardIds = pin
                ? new String[] {"row0", "row1", "row2", "row3", "row4"}
                : new String[] {"passwordEntry", "mixed_password_keyboard_view"};
        View[] keyboardParts = new View[keyboardIds.length];
        for (int index = 0; index < keyboardIds.length; index++) {
            keyboardParts[index] = findSystemUiView(credentialView, keyboardIds[index]);
            if (keyboardParts[index] == null) return;
        }
        try {
            Class<?> factory = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiFingerPrintFactory", false, classLoader);
            Object manager = factory.getMethod("getFingerPrintManager").invoke(null);
            Class<?> managerClass = Class.forName(
                    "com.miui.keyguard.biometrics.fod.MiuiGxzwManager", false, classLoader);
            if (!managerClass.isInstance(manager)) return;
            Method dismiss = managerClass.getMethod("dismissGxzwView");
            Method show = managerClass.getMethod("onKeyguardShow");
            CredentialSwitchState state = new CredentialSwitchState(credentialView,
                    (TextView) cancelButton, keyboardParts, manager, dismiss, show);
            CREDENTIAL_SWITCH_STATES.put(credentialView, new WeakReference<>(state));
            credentialView.addOnAttachStateChangeListener(state);
            state.showPasswordButton();
            cancelButton.setOnClickListener(view -> state.toggle());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Keep the stock return button if this build has a different FOD manager.
        }
    }

    private static View findSystemUiView(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", SYSTEM_UI);
        return id == 0 ? null : root.findViewById(id);
    }

    /** The bouncer stays open while its password controls and FOD window trade places. */
    private static final class CredentialSwitchState implements View.OnAttachStateChangeListener {
        private static final long SWITCH_ANIMATION_MS = 240L;

        private final View root;
        private final TextView button;
        private final View[] keyboardParts;
        private final Object fingerprintManager;
        private final Method dismissFingerprint;
        private final Method showFingerprint;
        private final CharSequence originalButtonText;
        private final CharSequence originalContentDescription;
        private final float keyboardTravelPx;
        private final float swipeThresholdPx;
        private boolean fingerprintMode;
        private boolean transitioning;
        private boolean closed;
        private boolean swipeTracking;
        private boolean swipeConsumed;
        private float swipeStartX;
        private float swipeStartY;

        CredentialSwitchState(View root, TextView button, View[] keyboardParts,
                Object fingerprintManager, Method dismissFingerprint, Method showFingerprint) {
            this.root = root;
            this.button = button;
            this.keyboardParts = keyboardParts;
            this.fingerprintManager = fingerprintManager;
            this.dismissFingerprint = dismissFingerprint;
            this.showFingerprint = showFingerprint;
            this.originalButtonText = button.getText();
            this.originalContentDescription = button.getContentDescription();
            float density = root.getResources().getDisplayMetrics().density;
            this.keyboardTravelPx = 24f * density;
            this.swipeThresholdPx = Math.max(64f * density,
                    4f * ViewConfiguration.get(root.getContext()).getScaledTouchSlop());
        }

        void toggle() {
            if (fingerprintMode) returnToPassword(true);
            else enterFingerprintMode();
        }

        void showPasswordButton() {
            button.setText("使用指纹解锁");
            button.setContentDescription("切换到指纹解锁");
        }

        private void showFingerprintButton() {
            button.setText("使用密码解锁");
            button.setContentDescription("返回密码输入");
        }

        private void enterFingerprintMode() {
            if (closed || fingerprintMode || !root.isAttachedToWindow()) return;
            fingerprintMode = true;
            transitioning = true;
            showFingerprintButton();
            for (int index = 0; index < keyboardParts.length; index++) {
                View part = keyboardParts[index];
                part.animate().cancel();
                part.setVisibility(View.VISIBLE);
                android.view.ViewPropertyAnimator animator = part.animate()
                        .alpha(0f).translationY(keyboardTravelPx)
                        .setDuration(SWITCH_ANIMATION_MS);
                if (index == keyboardParts.length - 1) {
                    animator.withEndAction(() -> {
                        if (closed || !fingerprintMode) return;
                        transitioning = false;
                        for (View keyboardPart : keyboardParts) {
                            keyboardPart.setVisibility(View.INVISIBLE);
                        }
                        FINGERPRINT_MODE_REQUESTED_MANAGERS.add(fingerprintManager);
                        CREDENTIAL_SUPPRESSED_FOD_MANAGERS.remove(fingerprintManager);
                        try {
                            showFingerprint.invoke(fingerprintManager);
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            returnToPassword(true);
                        }
                    });
                }
                animator.start();
            }
        }

        void returnToPassword(boolean animated) {
            if (closed || (!fingerprintMode && !transitioning)) return;
            fingerprintMode = false;
            transitioning = false;
            FINGERPRINT_MODE_REQUESTED_MANAGERS.remove(fingerprintManager);
            CREDENTIAL_SUPPRESSED_FOD_MANAGERS.add(fingerprintManager);
            try {
                dismissFingerprint.invoke(fingerprintManager);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A failed dismiss must not strand the user outside password entry.
            }
            showPasswordButton();
            for (View part : keyboardParts) {
                part.animate().cancel();
                part.setVisibility(View.VISIBLE);
                if (animated && root.isAttachedToWindow()) {
                    part.setAlpha(0f);
                    part.setTranslationY(keyboardTravelPx);
                    part.animate().alpha(1f).translationY(0f)
                            .setDuration(SWITCH_ANIMATION_MS).start();
                } else {
                    part.setAlpha(1f);
                    part.setTranslationY(0f);
                }
            }
        }

        boolean handleSwipe(MotionEvent event, View container) {
            if (closed || !root.isAttachedToWindow() || root.getVisibility() != View.VISIBLE) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                swipeTracking = swipeStartY >= container.getHeight() * 0.45f;
                swipeConsumed = false;
                return false;
            }
            if (swipeConsumed) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    swipeConsumed = false;
                    swipeTracking = false;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_DOWN) swipeTracking = false;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                swipeTracking = false;
            }
            if (action != MotionEvent.ACTION_MOVE || !swipeTracking
                    || event.getPointerCount() != 1) return false;
            float deltaY = event.getY() - swipeStartY;
            float deltaX = event.getX() - swipeStartX;
            boolean requestedDirection = fingerprintMode
                    ? deltaY > swipeThresholdPx : deltaY < -swipeThresholdPx;
            if (!requestedDirection || Math.abs(deltaX) > Math.abs(deltaY) * 0.75f) return false;
            swipeConsumed = true;
            swipeTracking = false;
            MotionEvent cancel = MotionEvent.obtain(event);
            try {
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                root.dispatchTouchEvent(cancel);
            } finally {
                cancel.recycle();
            }
            toggle();
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {}

        @Override
        public void onViewDetachedFromWindow(View view) {
            close();
        }

        void close() {
            if (closed) return;
            closed = true;
            if (fingerprintMode && booleanDeclaredField(
                    fingerprintManager, "mBouncer", false)) {
                CREDENTIAL_SUPPRESSED_FOD_MANAGERS.add(fingerprintManager);
                try {
                    dismissFingerprint.invoke(fingerprintManager);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // The keyguard lifecycle can remove this window independently.
                }
            }
            FINGERPRINT_MODE_REQUESTED_MANAGERS.remove(fingerprintManager);
            for (View part : keyboardParts) {
                part.animate().cancel();
                part.setVisibility(View.VISIBLE);
                part.setAlpha(1f);
                part.setTranslationY(0f);
            }
            button.setText(originalButtonText);
            button.setContentDescription(originalContentDescription);
            root.removeOnAttachStateChangeListener(this);
            CREDENTIAL_SWITCH_STATES.remove(root);
        }
    }

    private static Object readFieldInHierarchy(Object target, String name) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // The field can be declared by a superclass.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
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
        if (lowerLockscreenPasswordPage) lowerLockscreenCredential(root, pin);
        if (pin && lockscreenPinKeySoftGlassEnabled) {
            applyLockscreenPinKeyGlass(root, classLoader);
        }
    }

    private static void lowerLockscreenCredential(View root, boolean pin) {
        int fodSpaceId = root.getResources().getIdentifier(pin
                        ? "pin_fod_bottom_distance" : "password_fod_bottom_distance",
                "id", "com.android.systemui");
        if (fodSpaceId == 0) return;
        View fodSpace = root.findViewById(fodSpaceId);
        if (fodSpace != null && fodSpace.getVisibility() != View.GONE) {
            fodSpace.setVisibility(View.GONE);
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


}
