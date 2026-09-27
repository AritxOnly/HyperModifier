package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Like HyperChanger's material hook, intercept native View.setMiGlass and classify its caller. */
final class ShadeCardGlassHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean NOTIFICATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CONTROL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean BACKGROUND_BLUR_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean GLASS_BLUR_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean VISIBILITY_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean NOTIFICATION_BLUR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CONTROL_BLUR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_OBSERVER_INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final Map<View, Integer> STOCK_BACKGROUND_RADII = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Card> CARDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, NativeRadius> NATIVE_GLASS_TARGETS = Collections.synchronizedMap(new WeakHashMap<>());
    private static Method glassSetter;
    private static Method radiusSetter;
    private static Method backgroundSetter;

    private static final class NativeRadius {
        final int small;
        final int big;
        final int kind;
        boolean modified;
        NativeRadius(int small, int big, int kind) {
            this.small = small; this.big = big; this.kind = kind;
        }
    }

    private static final class Card {
        final float[] recipe;
        final AtomicBoolean pending = new AtomicBoolean();
        volatile boolean pendingRecipe;
        boolean backgroundApplied;
        Card(float[] recipe) { this.recipe = recipe.clone(); }
    }

    private ShadeCardGlassHooks() {}

    static void install(XposedModule module) {
        if (SETTINGS_OBSERVER_INSTALLED.compareAndSet(false, true)) onChanged(ShadeCardGlassHooks::refresh);
        installBlurHook(module, "setMiBackgroundBlurRadius", BACKGROUND_BLUR_INSTALLED, false);
        installBlurHook(module, "setMiGlassBlurRadius", GLASS_BLUR_INSTALLED, true);
        installVisibilityHook(module);
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Method setter = View.class.getMethod("setMiGlass", float[].class);
            glassSetter = setter;
            module.hook(setter).setId("shade-card-glass-parameters")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (Boolean.TRUE.equals(REPLAYING.get())) return chain.proceed();
                        Object viewObject = chain.getThisObject();
                        Object values = chain.getArg(0);
                        if (!(viewObject instanceof View) || !(values instanceof float[])) {
                            return chain.proceed();
                        }
                        View view = (View) viewObject;
                        float[] original = (float[]) values;
                        if (ShadeCardGlassPolicy.tune(original, ShadeCardGlassPolicy.defaults()) == null) {
                            // A clear call must not keep a stale recipe alive for settings refresh.
                            Card old = CARDS.remove(view);
                            if (old != null) applyOwnBackgroundRadius(view, old, 100);
                            return chain.proceed();
                        }
                        int kind = targetKind(view);
                        if (kind == 0) {
                            Card old = CARDS.remove(view);
                            if (old != null) applyOwnBackgroundRadius(view, old, 100);
                            return chain.proceed();
                        }
                        Card old = CARDS.get(view);
                        Card card = new Card(original);
                        card.backgroundApplied = old != null && old.backgroundApplied;
                        CARDS.put(view, card);
                        float[] tuned = shadeCardGlassParametersEnabled ? tuneShadeCardGlass(original) : null;
                        Object result = tuned == null ? chain.proceed() : chain.proceed(new Object[]{tuned});
                        applyOwnBackgroundRadius(view, card,
                                globalGlassBlurEnabled ? shadeCardBackgroundBlurPercent : 100);
                        AtomicBoolean logged = kind == 2 ? CONTROL_LOGGED : NOTIFICATION_LOGGED;
                        if (logged.compareAndSet(false, true)) {
                            Log.i("MyHyperModifier", "Card glass matched "
                                    + (kind == 2 ? "Control Center" : "notification shade")
                                    + ": " + view.getClass().getName() + ", common recipe");
                        }
                        return result;
                    });
            Log.i("MyHyperModifier", "Shared notification/Control Center card glass hook installed");
        } catch (Throwable error) {
            INSTALLED.set(false);
            Log.w("MyHyperModifier", "Shared card glass hook unavailable", error);
        }
    }

    private static void installBlurHook(XposedModule module, String name, AtomicBoolean installed, boolean glass) {
        if (!installed.compareAndSet(false, true)) return;
        try {
            Method setter = glass ? View.class.getMethod(name, int.class, int.class)
                    : View.class.getMethod(name, int.class);
            if (glass) radiusSetter = setter;
            else backgroundSetter = setter;
            module.hook(setter).setId("shade-card-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (Boolean.TRUE.equals(REPLAYING.get())) return chain.proceed();
                        Object receiver = chain.getThisObject();
                        if (!(receiver instanceof View)) return chain.proceed();
                        View view = (View) receiver;
                        int original = (Integer) chain.getArg(0);
                        if (glass) {
                            int big = (Integer) chain.getArg(1);
                            int kind = nativeRadiusKind(view);
                            if (kind == 0) return chain.proceed();
                            NativeRadius state = new NativeRadius(original, big, kind);
                            NATIVE_GLASS_TARGETS.put(view, state);
                            if (nativeRadiusEnabled(view, state)) {
                                int[] radii = nativeRadii(view, state);
                                Object result = chain.proceedWith(receiver, new Object[]{radii[0], radii[1]});
                                state.modified = true;
                                logNativeRadius(view, state, radii);
                                return result;
                            }
                            return chain.proceed();
                        }
                        STOCK_BACKGROUND_RADII.put(view, original);
                        if (!globalGlassBlurEnabled || shadeCardBackgroundBlurPercent == 100
                                || targetKind(view) == 0) return chain.proceed();
                        Card card = CARDS.get(view);
                        if (card != null) card.backgroundApplied = true;
                        return chain.proceed(new Object[]{ShadeCardGlassPolicy.scaleBackgroundRadius(
                                original, shadeCardBackgroundBlurPercent)});
                    });
            Log.i("MyHyperModifier", "Card blur hook installed: " + name);
        } catch (Throwable error) {
            installed.set(false);
            Log.w("MyHyperModifier", "Card blur API unavailable: " + name, error);
        }
    }

    private static void installVisibilityHook(XposedModule module) {
        if (!VISIBILITY_INSTALLED.compareAndSet(false, true)) return;
        try {
            module.hook(View.class.getMethod("onVisibilityAggregated", boolean.class))
                    .setId("shade-card-material-visible")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (Boolean.TRUE.equals(chain.getArg(0)) && chain.getThisObject() instanceof View) {
                            View view = (View) chain.getThisObject();
                            Card card = CARDS.get(view);
                            if (card != null) schedule(view, card, true);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            VISIBILITY_INSTALLED.set(false);
            Log.w("MyHyperModifier", "Card visibility refresh unavailable", error);
        }
    }

    static void refresh() {
        ArrayList<Map.Entry<View, Card>> cards = snapshot();
        if (shadeCardGlassParametersEnabled || globalGlassBlurEnabled) Log.i("MyHyperModifier", "Card settings refresh: tracked="
                + cards.size() + ", nativeGlassTargets=" + NATIVE_GLASS_TARGETS.size()
                + ", blurPercent=" + shadeCardBackgroundBlurPercent
                + ", customRadius=" + shadeCardGlassBlurEnabled + "/" + shadeCardGlassBlurRadius
                + ", globalGlassBlur=" + globalGlassBlurEnabled);
        for (Map.Entry<View, Card> entry : cards) schedule(entry.getKey(), entry.getValue(), true);
        refreshNativeRadii();
    }

    private static ArrayList<Map.Entry<View, Card>> snapshot() {
        ArrayList<Map.Entry<View, Card>> result = new ArrayList<>();
        synchronized (CARDS) {
            for (Map.Entry<View, Card> entry : CARDS.entrySet()) {
                View view = entry.getKey();
                if (view != null) result.add(new AbstractMap.SimpleImmutableEntry<>(view, entry.getValue()));
            }
        }
        return result;
    }

    private static void schedule(View view, Card card, boolean recipe) {
        if (recipe) card.pendingRecipe = true;
        if (!card.pending.compareAndSet(false, true)) return;
        view.post(() -> {
            boolean replayRecipe = card.pendingRecipe;
            card.pendingRecipe = false;
            card.pending.set(false);
            if (CARDS.get(view) != card || !view.isAttachedToWindow() || !view.isShown()) return;
            if (!hasControlCenterAncestor(view) && !isShadeNotificationBackground(view)) return;
            try {
                REPLAYING.set(true);
                if (replayRecipe && glassSetter != null) {
                    float[] tuned = shadeCardGlassParametersEnabled ? tuneShadeCardGlass(card.recipe) : null;
                    glassSetter.invoke(view, (Object) (tuned == null ? card.recipe.clone() : tuned));
                }
            } catch (ReflectiveOperationException error) {
                Log.w("MyHyperModifier", "Card material refresh failed", error);
            } finally { REPLAYING.remove(); }
            applyOwnBackgroundRadius(view, card,
                    globalGlassBlurEnabled ? shadeCardBackgroundBlurPercent : 100);
        });
    }

    private static int nativeRadiusKind(View view) {
        String name = view.getClass().getName();
        if (name.startsWith("com.android.keyguard.") || name.startsWith("com.miui.keyguard.")) return 0;
        if (name.contains("NotificationBackgroundView") && !isShadeNotificationBackground(view)) return 0;
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        String[] owners = new String[frames.length];
        for (int i = 0; i < frames.length; i++) owners[i] = frames[i].getClassName();
        // HyperChanger intentionally includes com.android.systemui.shade and
        // com.miui.systemui.shade here. These calls are not card recipe calls.
        int kind = ShadeCardGlassPolicy.radiusCallKind(owners);
        if (kind != 0) return kind;
        for (String owner : owners) if (owner.contains("HeadsUp")) return 0;
        if (isShadeNotificationBackground(view)) return 1;
        return hasControlCenterAncestor(view) ? 2 : 0;
    }

    static void refreshNativeRadii() {
        ArrayList<Map.Entry<View, NativeRadius>> targets = new ArrayList<>();
        synchronized (NATIVE_GLASS_TARGETS) {
            for (Map.Entry<View, NativeRadius> entry : NATIVE_GLASS_TARGETS.entrySet()) {
                View view = entry.getKey();
                if (view != null) targets.add(new AbstractMap.SimpleImmutableEntry<>(view, entry.getValue()));
            }
        }
        for (Map.Entry<View, NativeRadius> entry : targets) {
            View view = entry.getKey();
            NativeRadius state = entry.getValue();
            view.post(() -> {
                if (NATIVE_GLASS_TARGETS.get(view) != state || !view.isAttachedToWindow() || radiusSetter == null) return;
                boolean enabled = nativeRadiusEnabled(view, state);
                if (!enabled && !state.modified) return;
                if (view.getClass().getName().contains("NotificationBackgroundView")
                        && !isShadeNotificationBackground(view)) return;
                int[] radii = enabled ? nativeRadii(view, state) : new int[]{state.small, state.big};
                try {
                    REPLAYING.set(true);
                    radiusSetter.invoke(view, radii[0], radii[1]);
                    state.modified = enabled;
                    if (enabled) logNativeRadius(view, state, radii);
                } catch (ReflectiveOperationException error) {
                    Log.w("MyHyperModifier", "Native Glass provider radius refresh failed", error);
                } finally { REPLAYING.remove(); }
            });
        }
    }

    private static void logNativeRadius(View view, NativeRadius state, int[] radii) {
        AtomicBoolean logged = state.kind == 2 ? CONTROL_BLUR_LOGGED : NOTIFICATION_BLUR_LOGGED;
        if (logged.compareAndSet(false, true)) Log.i("MyHyperModifier", "Native Glass radius matched: "
                + view.getClass().getName() + ", sharedGlass=true, stock=" + state.small + "/" + state.big
                + ", applied=" + radii[0] + "/" + radii[1]);
    }

    private static boolean nativeRadiusEnabled(View view, NativeRadius state) {
        return globalGlassBlurEnabled && (shadeCardGlassBlurEnabled || shadeCardBackgroundBlurPercent != 100);
    }

    private static int[] nativeRadii(View view, NativeRadius state) {
        return ShadeCardGlassPolicy.effectiveGlassRadii(state.small, state.big,
                shadeCardGlassBlurEnabled, shadeCardGlassBlurRadius, shadeCardBackgroundBlurPercent);
    }

    private static void applyOwnBackgroundRadius(View view, Card card, int percent) {
        Integer own = STOCK_BACKGROUND_RADII.get(view);
        if (own == null || backgroundSetter == null || (percent == 100 && !card.backgroundApplied)) return;
        try {
            REPLAYING.set(true);
            backgroundSetter.invoke(view, ShadeCardGlassPolicy.scaleBackgroundRadius(own, percent));
            card.backgroundApplied = percent != 100;
        } catch (ReflectiveOperationException error) {
            Log.w("MyHyperModifier", "Card own background radius refresh failed", error);
        } finally { REPLAYING.remove(); }
    }

    static boolean isCardBackgroundTarget(View view) { return targetKind(view) != 0; }

    private static int targetKind(View view) {
        if (isPanelBackground(view)) return 0;
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        String[] owners = new String[frames.length];
        for (int i = 0; i < frames.length; i++) owners[i] = frames[i].getClassName();
        int kind = ShadeCardGlassPolicy.callKind(owners, hasControlCenterAncestor(view));
        // A reused row can update through NotificationUtil/compat/visibility callbacks.
        if (kind == 0 && CARDS.containsKey(view)) {
            for (String owner : owners) {
                if (owner.contains("HeadsUp") || owner.contains("ShadeBlendBlurController")
                        || owner.startsWith("com.android.keyguard.")
                        || owner.startsWith("com.miui.keyguard.")) return 0;
            }
            if (hasControlCenterAncestor(view)) kind = 2;
            else if (isShadeNotificationBackground(view)) kind = 1;
        }
        return kind == 1 && !isShadeNotificationBackground(view) ? 0 : kind;
    }

    private static boolean isPanelBackground(View view) {
        String name = view.getClass().getName();
        if (name.contains("ControlCenterContainer") || name.contains("NotificationPanelView")
                || name.contains("NotificationShadeWindowView")) return true;
        try {
            if (view.getId() == View.NO_ID) return false;
            String id = view.getResources().getResourceEntryName(view.getId());
            return id.equals("control_center_container") || id.equals("control_center_background")
                    || id.equals("notification_panel_background") || id.equals("shade_window_background");
        } catch (android.content.res.Resources.NotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasControlCenterAncestor(View view) {
        // Animation callbacks can retain only a MiBackgroundStyle frame, after the original
        // panel caller has returned. Require a real Control Center owner in that case.
        for (View candidate = view; candidate != null;) {
            if (candidate.getClass().getName().startsWith("miui.systemui.controlcenter.")) return true;
            ViewParent parent = candidate.getParent();
            candidate = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean isShadeNotificationBackground(View view) {
        if (!view.getClass().getName().equals(
                "com.android.systemui.statusbar.notification.row.NotificationBackgroundView")) return false;
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (!parent.getClass().getName().contains("ExpandableNotificationRow")) continue;
            // Normal rows share their effect implementation with keyguard. Keep that scope stock.
            if (ReflectiveAccess.booleanDeclaredField(parent, "mOnKeyguard", true)) return false;
            try {
                return !Boolean.TRUE.equals(parent.getClass().getMethod("isPinned").invoke(parent));
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }
}
