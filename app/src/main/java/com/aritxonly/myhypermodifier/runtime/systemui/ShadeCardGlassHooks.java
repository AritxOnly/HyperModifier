package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Like HyperChanger's material hook, intercept native View.setMiGlass and classify its caller. */
final class ShadeCardGlassHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean NOTIFICATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CONTROL_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean VISIBILITY_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean SETTINGS_OBSERVER_INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final Map<View, Card> CARDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static Method glassSetter;
    private static final GlassRecipeRefreshState REFRESH_STATE = new GlassRecipeRefreshState();
    private static final Map<Class<?>, RowAccess> ROW_ACCESS = new ConcurrentHashMap<>();

    /** Cache metadata only. Pinned/keyguard values are read anew for every classification. */
    private static final class RowAccess {
        final Field onKeyguard;
        final Method isPinned;

        RowAccess(Class<?> type) {
            Field keyguard = null;
            Method pinned = null;
            try {
                for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
                    try {
                        keyguard = owner.getDeclaredField("mOnKeyguard");
                        keyguard.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
                pinned = type.getMethod("isPinned");
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            onKeyguard = keyguard;
            isPinned = pinned;
        }

        boolean isNormalShadeRow(Object row) {
            if (onKeyguard == null || isPinned == null) return false;
            try {
                return !onKeyguard.getBoolean(row) && !Boolean.TRUE.equals(isPinned.invoke(row));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
    }

    private static final class Card {
        final float[] recipe;
        final AtomicBoolean pending = new AtomicBoolean();
        volatile boolean pendingRecipe;
        Card(float[] recipe) { this.recipe = recipe.clone(); }
    }

    private ShadeCardGlassHooks() {}

    private static String processPackage() {
        String name = android.app.Application.getProcessName();
        return name != null && name.startsWith("miui.systemui.plugin")
                ? "miui.systemui.plugin" : "com.android.systemui";
    }

    static void install(XposedModule module) {
        if (disableShadeGlassHooks) return;
        if (SETTINGS_OBSERVER_INSTALLED.compareAndSet(false, true)) onChanged(ShadeCardGlassHooks::refresh);
        installVisibilityHook(module);
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Method setter = View.class.getMethod("setMiGlass", float[].class);
            glassSetter = setter;
            module.hook(setter).setId("shade-card-glass-parameters")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (disableShadeGlassHooks) return chain.proceed();
                        if (Boolean.TRUE.equals(REPLAYING.get())) return chain.proceed();
                        Object viewObject = chain.getThisObject();
                        Object values = chain.getArg(0);
                        if (!(viewObject instanceof View) || !(values instanceof float[])) {
                            return chain.proceed();
                        }
                        View view = (View) viewObject;
                        if (!isLoaded()) markLoaded(view.getContext());
                        float[] original = (float[]) values;
                        float[] islandRecipe = DynamicIslandGlassTint.rewrite(view, original);
                        if (islandRecipe != null) {
                            return islandRecipe == original ? chain.proceed()
                                    : chain.proceed(new Object[]{islandRecipe});
                        }
                        if (!ShadeCardGlassPolicy.isActiveRecipe(original)) {
                            // A clear call must not keep a stale recipe alive for settings refresh.
                            CARDS.remove(view);
                            return chain.proceed();
                        }
                        int kind = targetKind(view);
                        if (kind == 0) {
                            CARDS.remove(view);
                            return chain.proceed();
                        }
                        Card old = CARDS.get(view);
                        Card card = old;
                        if (card == null || !Arrays.equals(card.recipe, original)) {
                            card = new Card(original);
                            CARDS.put(view, card);
                        }
                        float[] tuned = shadeCardGlassParametersEnabled ? tuneShadeCardGlass(original) : null;
                        Object result = tuned == null ? chain.proceed() : chain.proceed(new Object[]{tuned});
                        AtomicBoolean logged = kind == 2 ? CONTROL_LOGGED : NOTIFICATION_LOGGED;
                        if (logged.compareAndSet(false, true)) {
                            Log.i("MyHyperModifier", "Card glass matched "
                                    + (kind == 2 ? "Control Center" : "notification shade")
                                    + ": " + view.getClass().getName() + ", common recipe");
                        }
                        return result;
                    });
            Log.i("MyHyperModifier", "Shared notification/Control Center card glass hook installed");
            HookDiagnostics.available(processPackage(), "通知及控制中心玻璃卡片");
        } catch (Throwable error) {
            INSTALLED.set(false);
            Log.w("MyHyperModifier", "Shared card glass hook unavailable", error);
            HookDiagnostics.failure(processPackage(), "通知及控制中心玻璃卡片", error);
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
                        if (disableShadeGlassHooks) return result;
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

    /** Precise island ownership changes invalidate a pending generic replay once, not per frame. */
    static void forgetGenericCard(View view) { CARDS.remove(view); }

    static void refresh() {
        if (disableShadeGlassHooks || !REFRESH_STATE.changed(
                shadeCardGlassParametersEnabled, shadeCardGlassRefreshParameters())) return;
        ArrayList<Map.Entry<View, Card>> cards = snapshot();
        for (Map.Entry<View, Card> entry : cards) schedule(entry.getKey(), entry.getValue(), true);
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
        if (disableShadeGlassHooks) return;
        if (recipe) card.pendingRecipe = true;
        if (!card.pending.compareAndSet(false, true)) return;
        view.post(() -> {
            boolean replayRecipe = card.pendingRecipe;
            card.pendingRecipe = false;
            card.pending.set(false);
            if (disableShadeGlassHooks) return;
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
        });
    }

    static boolean isCardBackgroundTarget(View view, StackTraceElement[] frames) {
        return !isPanelBackground(view) && targetKind(view, frames) != 0;
    }

    private static int targetKind(View view) {
        if (isPanelBackground(view)) return 0;
        return targetKind(view, Thread.currentThread().getStackTrace());
    }

    private static int targetKind(View view, StackTraceElement[] frames) {
        boolean controlAncestor = hasControlCenterAncestor(view);
        int kind = ShadeCardGlassPolicy.callKind(frames, controlAncestor);
        // A reused row can update through NotificationUtil/compat/visibility callbacks.
        if (kind == 0 && CARDS.containsKey(view)) {
            for (StackTraceElement frame : frames) {
                String owner = frame.getClassName();
                if (owner.contains("HeadsUp") || owner.contains("ShadeBlendBlurController")
                        || owner.startsWith("com.android.keyguard.")
                        || owner.startsWith("com.miui.keyguard.")) return 0;
            }
            if (controlAncestor) kind = 2;
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
            return ROW_ACCESS.computeIfAbsent(parent.getClass(), RowAccess::new).isNormalShadeRow(parent);
        }
        return false;
    }
}
