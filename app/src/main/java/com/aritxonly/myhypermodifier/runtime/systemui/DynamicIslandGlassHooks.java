package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Expanded island backgrounds apply a Glass recipe without setting their own sampling radii. */
final class DynamicIslandGlassHooks {
    private static final String CONTENT =
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MATCH_LOGGED = new AtomicBoolean();
    private static final AtomicInteger OBSERVED_CONDITIONS = new AtomicInteger();
    private static final Map<View, WeakReference<View>> OWNERS = new WeakHashMap<>();
    private static final Map<View, WeakReference<View>> TINT_TARGETS = new WeakHashMap<>();
    private static final Set<Method> CLOSE_INSTALLED = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean TRACKING_INSTALLED = new AtomicBoolean();
    private static final String FEATURE = "超级岛展开背景 Glass 半径";

    private DynamicIslandGlassHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (ModuleSettings.disableShadeGlassHooks || !INSTALLED.compareAndSet(false, true)) return;
        try {
            // Resolve once using the real plugin loader, outside ClassLoader.loadClass's monitor.
            Class<?> content = Class.forName(CONTENT, false, loader);
            DynamicIslandGlassTint.install();
            Method update = content.getDeclaredMethod("updateBackgroundBg", View.class, boolean.class);
            Class<?> window = Class.forName("miui.systemui.dynamicisland.window.DynamicIslandWindowView", false, loader);
            Method currentExpanded = window.getDeclaredMethod("getCurrentExpandedState");
            Class<?> realContent = Class.forName("miui.systemui.dynamicisland.window.content.DynamicIslandContentView", false, loader);
            Class<?> fakeContent = Class.forName("miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView", false, loader);
            Method fakeExpanded = content.getDeclaredMethod("getFakeExpandedView");
            installTracking(module, fakeContent.getDeclaredMethod("onTrackingFakeViewStart"), fakeExpanded, update);
            // Register cleanup first: the shared sampling owner must return to stock when collapsed.
            installClose(module, realContent.getDeclaredMethod("updateViewStateWhenCloseEnd"), currentExpanded);
            installClose(module, fakeContent.getDeclaredMethod("updateViewStateWhenCloseEnd", boolean.class, String.class), currentExpanded);
            Method bionics = Class.forName("miui.systemui.util.MiBackgroundStyle", false, loader)
                    .getDeclaredMethod("isBionicsMode");
            Method materialOpened = Class.forName("miui.systemui.util.MiBlurCompat", false, loader)
                    .getDeclaredMethod("getBackgroundMaterialOpened", Context.class);
            Method viewMode = View.class.getMethod("getMiViewBlurMode");
            Method backgroundMode = View.class.getMethod("getMiBackgroundBlurMode");
            module.hook(update).setId("dynamic-island-expanded-glass-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (ModuleSettings.disableShadeGlassHooks) return chain.proceed();
                        Object target = chain.getArg(0);
                        if (!(target instanceof View)) return chain.proceed();
                        View view = (View) target;
                        // Same conditions as stock updateBackgroundBg. Mode reads use the plugin's
                        // current state and Resources.Configuration; they perform no settings IPC.
                        boolean attachedParent = view.getParent() != null;
                        boolean opened = Boolean.TRUE.equals(materialOpened.invoke(null, view.getContext()));
                        boolean bionic = Boolean.TRUE.equals(bionics.invoke(null));
                        boolean glass = attachedParent && opened && bionic;
                        if (glass) DynamicIslandGlassTint.register(view);
                        else DynamicIslandGlassTint.forget(view);
                        Object result = chain.proceed();
                        // Tint belongs to the expanded consumer, independent of its sampling root.
                        synchronized (OWNERS) {
                            View contentView = (View) chain.getThisObject();
                            WeakReference<View> oldTarget = TINT_TARGETS.get(contentView);
                            View oldView = oldTarget == null ? null : oldTarget.get();
                            if (oldView != view) {
                                if (oldView != null) DynamicIslandGlassTint.forget(oldView);
                                if (glass) TINT_TARGETS.put(contentView, new WeakReference<>(view));
                                else TINT_TARGETS.remove(contentView);
                            }
                        }
                        View root = view.getRootView();
                        boolean ownerFound = window.isInstance(root)
                                && Integer.valueOf(1).equals(backgroundMode.invoke(root));
                        if (glass && ownerFound) {
                            synchronized (OWNERS) {
                                View contentView = (View) chain.getThisObject();
                                WeakReference<View> old = OWNERS.get(contentView);
                                if (old == null || old.get() != root) OWNERS.put(contentView, new WeakReference<>(root));
                            }
                            boolean applied = GlobalGlassBlurHooks.onExpandedMaterialApplied(root);
                            if (applied && ModuleSettings.globalGlassBlurEnabled && MATCH_LOGGED.compareAndSet(false, true)) {
                                HookDiagnostics.observed("com.android.systemui", "超级岛展开采样容器已覆盖",
                                        "owner=" + root.getClass().getName()
                                            + ", rootBackgroundBlurMode=" + backgroundMode.invoke(root)
                                            + ", ownerFound=" + ownerFound
                                            + ", requestedRadius=" + ModuleSettings.shadeCardGlassBlurRadius);
                            }
                        } else {
                            if (!glass) clearOwner((View) chain.getThisObject(), currentExpanded, true);
                        }
                        int conditions = (attachedParent ? 1 : 0) | (opened ? 2 : 0) | (bionic ? 4 : 0);
                        if (ModuleSettings.globalGlassBlurEnabled && markObserved(conditions)) {
                            HookDiagnostics.observed("com.android.systemui",
                                    "超级岛展开背景运行条件 " + conditions,
                                    "target=" + view.getClass().getName()
                                            + ", parent=" + (view.getParent() == null ? "none" : view.getParent().getClass().getName())
                                            + ", materialOpened=" + opened + ", bionics=" + bionic
                                            + ", viewBlurMode=" + viewMode.invoke(view)
                                            + ", backgroundBlurMode=" + backgroundMode.invoke(view)
                                            + ", root=" + root.getClass().getName()
                                            + ", rootBackgroundBlurMode=" + backgroundMode.invoke(root)
                                            + ", ownerFound=" + ownerFound
                                            + ", requestedRadius=" + ModuleSettings.shadeCardGlassBlurRadius);
                        }
                        return result;
                    });
            HookDiagnostics.available("miui.systemui.plugin", FEATURE);
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure("miui.systemui.plugin", FEATURE, error);
            Log.w("MyHyperModifier", "Dynamic island Glass material hook unavailable", error);
        }
    }

    private static void installTracking(XposedModule module, Method start, Method expanded, Method update) throws Throwable {
        if (!TRACKING_INSTALLED.compareAndSet(false, true)) return;
        try {
            module.hook(start).setId("dynamic-island-freeform-tint-start")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (ModuleSettings.moduleHooksEnabled && ModuleSettings.dynamicIslandOpacityEnabled
                                && !ModuleSettings.disableShadeGlassHooks) {
                            Object target = expanded.invoke(chain.getThisObject());
                            if (target instanceof View) update.invoke(chain.getThisObject(), target, false);
                        }
                        return result;
                    });
            HookDiagnostics.available("miui.systemui.plugin", "超级岛下拉小窗背景不透明度");
        } catch (Throwable error) {
            TRACKING_INSTALLED.set(false);
            throw error;
        }
    }

    private static void installClose(XposedModule module, Method close, Method currentExpanded) throws Throwable {
        if (!CLOSE_INSTALLED.add(close)) return;
        try {
            module.hook(close).setId("dynamic-island-glass-close-" + close.getDeclaringClass().getSimpleName())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof View) clearOwner((View) chain.getThisObject(), currentExpanded, false);
                        return result;
                    });
        } catch (Throwable error) {
            CLOSE_INSTALLED.remove(close);
            throw error;
        }
    }

    private static void clearOwner(View content, Method currentExpanded, boolean force) {
        WeakReference<View> reference;
        synchronized (OWNERS) {
            reference = OWNERS.remove(content);
            WeakReference<View> target = TINT_TARGETS.remove(content);
            View tintView = target == null ? null : target.get();
            if (tintView != null) DynamicIslandGlassTint.forget(tintView);
        }
        View owner = reference == null ? null : reference.get();
        if (owner == null) return;
        if (force) {
            GlobalGlassBlurHooks.onMaterialCleared(owner);
            return;
        }
        // One deferred lifecycle task allows the stock expanded-state handler to finish its update.
        owner.post(() -> {
            try {
                if (currentExpanded.invoke(owner) == null) GlobalGlassBlurHooks.onMaterialCleared(owner);
            } catch (ReflectiveOperationException error) {
                Log.w("MyHyperModifier", "Dynamic island sampling cleanup failed", error);
            }
        });
    }

    private static boolean markObserved(int conditions) {
        int bit = 1 << conditions;
        int old;
        do {
            old = OBSERVED_CONDITIONS.get();
            if ((old & bit) != 0) return false;
        } while (!OBSERVED_CONDITIONS.compareAndSet(old, old | bit));
        return true;
    }
}
