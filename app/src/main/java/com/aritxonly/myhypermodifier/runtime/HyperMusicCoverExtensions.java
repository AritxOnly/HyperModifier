package com.aritxonly.myhypermodifier;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Optional HMC extension living in SystemUI, independent of HMC's APK/settings format. */
final class HyperMusicCoverExtensions {
    private static final String FEATURE = "HyperMusicCover 扩展";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static HyperMusicCoverAdapter adapter;
    private static HyperMusicCoverFontHeight font;
    private static HyperMusicCoverNotificationFlow notifications;
    private static ClassLoader systemLoader;
    private static boolean attempted, refreshing, editor, goingAway;
    private static final java.util.concurrent.atomic.AtomicBoolean INSTALLED = new java.util.concurrent.atomic.AtomicBoolean();
    private static Integer shade;
    private static volatile boolean expanded;
    private static volatile View guarded;
    private static final java.util.concurrent.atomic.AtomicBoolean ATTACH_PENDING = new java.util.concurrent.atomic.AtomicBoolean();
    private static float lastFontRatio = Float.NaN;
    private static boolean lastFontRatioEnabled;
    private static float lastFontProgress = 0f, lastFontHeight = Float.NaN;
    private static final WeakHashMap<Object, Integer> FRAMES = new WeakHashMap<>();
    private static final WeakHashMap<Object, Integer> LAST_FRAMES = new WeakHashMap<>();
    private static final ViewTreeObserver.OnPreDrawListener PRE_DRAW = () -> { refreshNow(); return true; };
    private HyperMusicCoverExtensions() {}

    static boolean enabled() {
        return ModuleSettings.hmcExpandedFingerprint || ModuleSettings.hmcClockFontHeightEnabled;
    }
    static boolean expanded() { return expanded; }
    static boolean bound() { return adapter != null; }
    static int hmcAvoidance() {
        try { return adapter == null ? 0 : adapter.fpAvoid.getInt(null); }
        catch (ReflectiveOperationException error) { return 0; }
    }
    static void install(XposedModule module, ClassLoader loader) {
        if (Build.VERSION.SDK_INT < 34 || !INSTALLED.compareAndSet(false, true)) return;
        systemLoader = loader;
        HyperMusicCoverStackState.install(module, loader);
        try { notifications = new HyperMusicCoverNotificationFlow(module, loader); }
        catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", "HMC 展开通知下沉", error);
        }
        sceneHook(module, loader, "com.android.keyguard.editor.KeyguardEditorHelper", "setEditorState", args -> {
            if (args.length > 0) editor = !"IDEL".equals(String.valueOf(args[0]));
        });
        sceneHook(module, loader, "com.android.keyguard.injector.KeyguardViewMediatorInjector", "keyguardGoingAway", args -> { if (font != null) font.beginUnlock(); goingAway = true; });
        sceneHook(module, loader, "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", "notifyKeyguardState", args -> {
            if (args.length > 0 && Boolean.TRUE.equals(args[0])) { goingAway = false; if (font != null) font.finishUnlock(); }
        });
        sceneHook(module, loader, "com.android.systemui.statusbar.StatusBarStateControllerImpl", "setIsDozing", args -> {
            // Original MiniPlayerScene clears an interrupted unlock when entering the AOD.
            if (args.length > 0 && Boolean.TRUE.equals(args[0])) { goingAway = false; if (font != null) font.finishUnlock(); }
        });
        try {
            Class<?> owner = Class.forName("com.android.systemui.statusbar.StatusBarStateControllerImpl", false, loader);
            Method get = owner.getDeclaredMethod("getState"); get.setAccessible(true);
            for (Method method : owner.getDeclaredMethods()) if ("setState".equals(method.getName())) {
                module.hook(method).setId("hmc-extension-shade-" + method.getParameterCount())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            shade = (Integer) get.invoke(chain.getThisObject()); refresh(); return result;
                        });
                module.deoptimize(method);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", FEATURE + "：通知中心状态", error);
        }
    }

    /** Called with the class discovered on HMC's live Hook stack, not a new DexClassLoader. */
    static synchronized void bind(XposedModule module, Class<?> main) {
        if (attempted || !enabled()) return;
        attempted = true;
        try {
            HyperMusicCoverAdapter candidate = new HyperMusicCoverAdapter(main);
            adapter = candidate;
            // These retained Main methods still exist in both official R8 APKs inspected.
            for (String name : new String[]{"setDepthHidden", "handBackDepth", "onClockReleased"})
                for (Method method : main.getDeclaredMethods()) if (name.equals(method.getName())) {
                    module.hook(method).setId("hmc-extension-state-" + name + '-' + method.getParameterCount())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                                Object result = chain.proceed(); refresh(); return result;
                            });
                    module.deoptimize(method);
                }
            module.hook(candidate.clockRoots).setId("hmc-extension-clock-attach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed(); attach(); return result;
                    });
            module.deoptimize(candidate.clockRoots);
            try {
                font = new HyperMusicCoverFontHeight(module, systemLoader);
                module.hook(candidate.glyphUnit).setId("hmc-extension-baseline-ink")
                        .setPriority(XposedInterface.PRIORITY_LOWEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            float original = (Float) chain.proceed();
                            // HMC maps its target to these units with a uniform scale. Return
                            // unmodified-font units so that mapping preserves the HGHT shape.
                            return font == null ? original : font.baselineUnit(candidate, original);
                        });
                module.deoptimize(candidate.glyphUnit);
                HookDiagnostics.available("com.android.systemui", "HMC 原生字体高度");
            } catch (ReflectiveOperationException | RuntimeException error) {
                font = null; HookDiagnostics.failure("com.android.systemui", "HMC 原生字体高度", error);
            }
            installFingerprint(module);
            HookDiagnostics.available("com.android.systemui", FEATURE);
            attach();
            refresh();
            MAIN.postDelayed(() -> {
                if (notifications != null) notifications.checkAttached();
                HyperMusicCoverStackState.checkAttached();
            }, 5000);
        } catch (ReflectiveOperationException | RuntimeException error) {
            adapter = null;
            HookDiagnostics.failure("com.android.systemui", FEATURE + "：HMC 接口不兼容", error);
        }
    }

    private static void attach() {
        if (adapter == null || refreshing) return;
        try { if (adapter.container.get(null) == guarded) return; }
        catch (ReflectiveOperationException ignored) { return; }
        if (!ATTACH_PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            try {
                View current = (View) adapter.container.get(null);
                if (current == guarded) return;
                if (guarded != null && guarded.getViewTreeObserver().isAlive())
                    guarded.getViewTreeObserver().removeOnPreDrawListener(PRE_DRAW);
                guarded = current;
                if (current != null) current.getViewTreeObserver().addOnPreDrawListener(PRE_DRAW);
                refreshNow();
            } catch (ReflectiveOperationException | RuntimeException error) {
                HookDiagnostics.failure("com.android.systemui", FEATURE + "：时钟视图", error);
            } finally { ATTACH_PENDING.set(false); }
        });
    }
    static void refresh() { MAIN.post(HyperMusicCoverExtensions::refreshNow); }
    static void refreshNow() {
        if (adapter == null || refreshing) return;
        refreshing = true;
        try {
            ModuleSettings.ensureLoaded();
            boolean wanted = HyperMusicCoverExtensionPolicy.active(ModuleSettings.hmcExpandedFingerprint,
                    adapter.bool(adapter.clockHeld), adapter.bool(adapter.locked), adapter.bool(adapter.shown),
                    shade, editor || goingAway || adapter.bool(adapter.controlCenter),
                    HyperMusicCoverStackState.current());
            if (wanted != expanded) {
                expanded = wanted;
                adapter.applyHide.invoke(null); // invalidates HMC's known FOD views; no setting mutation
                if (expanded) redrawFrames();
                else replayFrames();
                if (notifications != null) notifications.refresh();
            }
            if (font != null) { font.observeTargets(adapter); font.syncSession(fontSessionHeld()); }
            float progress = currentFontProgress();
            if (progress != lastFontProgress || ModuleSettings.hmcClockFontHeight != lastFontHeight
                    || ModuleSettings.hmcClockFontRatio != lastFontRatio
                    || ModuleSettings.hmcClockFontRatioEnabled != lastFontRatioEnabled) {
                lastFontRatio = ModuleSettings.hmcClockFontRatio;
                lastFontRatioEnabled = ModuleSettings.hmcClockFontRatioEnabled;
                lastFontProgress = progress;
                lastFontHeight = ModuleSettings.hmcClockFontHeight;
                for (View root : adapter.roots()) invalidateDigits((View) adapter.clockTarget.invoke(null, root));
            }
            // Also covers settings reloads while the expansion state is unchanged.
            if (notifications != null) notifications.refresh();
        } catch (ReflectiveOperationException | RuntimeException error) {
            expanded = false;
            HookDiagnostics.failure("com.android.systemui", FEATURE + "：运行状态", error);
        } finally { refreshing = false; }
    }
    static boolean fontLeaving() { return goingAway; }
    static boolean fontSessionHeld() {
        if (adapter == null || !ModuleSettings.hmcClockFontHeightEnabled) return false;
        try { return (goingAway && font != null && font.retainingExit()) || adapter.bool(adapter.clockHeld) || adapter.holdY.get(null) != null; }
        catch (ReflectiveOperationException | RuntimeException error) { return false; }
    }
    private static Float activeFontProgress() throws ReflectiveOperationException {
        if (font == null || !ModuleSettings.hmcClockFontHeightEnabled || editor || goingAway
                || (adapter.holdY.get(null) == null && !adapter.bool(adapter.clockHeld)) || !adapter.bool(adapter.locked)) return null;
        float progress = ((Number) adapter.progress.invoke(null)).floatValue();
        return Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
    }
    private static float currentFontProgress() throws ReflectiveOperationException {
        Float progress = activeFontProgress();
        return progress == null ? -1f : progress;
    }
    /** Null means OEM owns this glyph; zero still belongs to the entering HMC scene. */
    static Float fontProgress(View tv) {
        if (adapter == null) return null;
        if (goingAway && font != null) return font.exitProgress(tv);
        try {
            Float progress = activeFontProgress();
            if (progress == null) return null;
            for (View root : adapter.roots()) {
                View target = (View) adapter.clockTarget.invoke(null, root);
                for (View v = tv; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null)
                    if (v == target) return progress;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", "HMC 原生字体高度", error);
        }
        return null;
    }
    private static void invalidateDigits(View view) {
        if (view == null) return;
        if ("com.miui.clock.allInOne.TimeView".equals(view.getClass().getName())) view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateDigits(group.getChildAt(i));
        }
    }
    private static void installFingerprint(XposedModule module) throws ReflectiveOperationException {
        int painters = 0;
        for (String name : new String[]{"com.miui.keyguard.biometrics.fod.MiuiGxzwIconView",
                "com.miui.keyguard.biometrics.fod.MiuiGxzwAnimationView"}) {
            Class<?> owner = Class.forName(name, false, systemLoader);
            for (Method method : owner.getDeclaredMethods()) {
                if (!("onDraw".equals(method.getName()) || "draw".equals(method.getName())
                        || "dispatchDraw".equals(method.getName())) || method.getReturnType() != void.class
                        || method.getParameterCount() != 1 || method.getParameterTypes()[0] != android.graphics.Canvas.class) continue;
                painters++;
                module.hook(method).setId("hmc-extension-fod-" + owner.getSimpleName() + '-' + method.getName())
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain ->
                                expanded && adapter.bool(adapter.fodForKeyguard, chain.getThisObject()) ? null : chain.proceed());
                module.deoptimize(method);
            }
        }
        if (painters == 0) throw new NoSuchMethodException("FOD painters unavailable");
        Class<?> animation = Class.forName("com.miui.keyguard.biometrics.fod.MiuiGxzwFrameAnimation", false, systemLoader);
        Method draw = animation.getDeclaredMethod("draw", int.class);
        module.hook(draw).setId("hmc-extension-fod-ring")
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object instance = chain.getThisObject();
                    int resource = (Integer) chain.getArg(0);
                    boolean keyguardFrame = adapter.bool(adapter.fodRing, resource)
                            && adapter.bool(adapter.fodForKeyguard, adapter.fodView.invoke(null, instance));
                    if (keyguardFrame) LAST_FRAMES.put(instance, resource);
                    else LAST_FRAMES.remove(instance);
                    if (expanded && keyguardFrame) {
                        FRAMES.put(instance, resource);
                        Object[] args = chain.getArgs().toArray(); args[0] = android.R.color.transparent;
                        return chain.proceed(args);
                    }
                    FRAMES.remove(instance);
                    return chain.proceed();
                });
        module.deoptimize(draw);
    }
    private static void redrawFrames() throws ReflectiveOperationException {
        for (Object instance : LAST_FRAMES.keySet().toArray()) {
            Integer resource = LAST_FRAMES.get(instance);
            if (instance != null && resource != null
                    && adapter.bool(adapter.fodForKeyguard, adapter.fodView.invoke(null, instance))) {
                Method draw = instance.getClass().getDeclaredMethod("draw", int.class);
                draw.setAccessible(true); draw.invoke(instance, resource);
            }
        }
    }
    private static void replayFrames() throws ReflectiveOperationException {
        if (expanded || adapter.hideFp.getBoolean(null) || ModuleSettings.hideLockscreenFingerprintIcon) return;
        for (Object instance : FRAMES.keySet().toArray()) {
            Integer resource = FRAMES.remove(instance);
            if (instance != null && resource != null
                    && adapter.bool(adapter.fodForKeyguard, adapter.fodView.invoke(null, instance))) {
                Method draw = instance.getClass().getDeclaredMethod("draw", int.class);
                draw.setAccessible(true); draw.invoke(instance, resource);
            }
        }
    }
    private interface SceneChange { void apply(Object[] args); }
    private static void sceneHook(XposedModule module, ClassLoader loader, String name, String wanted, SceneChange change) {
        try {
            Class<?> owner = Class.forName(name, false, loader);
            boolean found = false;
            for (Method method : owner.getDeclaredMethods()) if (wanted.equals(method.getName())) {
                found = true;
                module.hook(method).setId("hmc-extension-scene-" + wanted + '-' + method.getParameterCount())
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            change.apply(chain.getArgs().toArray());
                            Object result = chain.proceed(); refresh(); return result;
                        });
                module.deoptimize(method);
            }
            if (!found) throw new NoSuchMethodException(wanted);
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", FEATURE + "：" + wanted, error);
        }
    }
}
