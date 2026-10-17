package com.aritxonly.myhypermodifier;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Adapters verified against InCallUI 40.01.56 (6018), main UI process only. */
@SuppressWarnings("deprecation")
final class InCallMaterialHooks {
    private static final String PACKAGE = ModuleScopePackage.INCALL_UI;
    private static final Map<View, RadiusState> TARGETS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();
    private static final ThreadLocal<Generation> GENERATION_CONFIG = new ThreadLocal<>();
    private static final ThreadLocal<BitmapPass> BITMAP_PASS = new ThreadLocal<>();
    private static final AtomicInteger GLASS_CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final AtomicInteger CONFIG = new AtomicInteger(Integer.MIN_VALUE);
    private static final AtomicBoolean BITMAP_PENDING = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile WeakReference<Object> manager = new WeakReference<>(null);
    private static volatile int appliedBitmapConfig = 100;
    private static volatile boolean legacyBitmap = true;
    private static Handler main;
    private static Method radiusSetter;
    private static Method generate;
    private static Field cache;
    private static Field lock;
    private static Field generating;

    private InCallMaterialHooks() {}

    private static final class RadiusState {
        int stock;
        int applied;
        boolean hasStock;
        boolean glassContainer;
        final AtomicBoolean pending = new AtomicBoolean();
    }

    private static final class Generation {
        final int config;
        boolean legacy;
        Generation(int config) { this.config = config; }
    }

    private static final class BitmapPass {
        final int percent;
        final int dimAlpha;
        Allocation input;
        Object script;
        BitmapPass(int config) {
            percent = InCallBlurPolicy.blurPercent(config);
            dimAlpha = InCallBlurPolicy.dimAlpha(config);
        }
    }

    private static int percent() { return moduleHooksEnabled ? globalBackgroundBlurPercent : 100; }

    private static int bitmapConfig() {
        return InCallBlurPolicy.bitmapConfig(percent(), moduleHooksEnabled && globalBackgroundDimEnabled,
                globalBackgroundDimPercent);
    }

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        main = new Handler(Looper.getMainLooper());
        try {
            radiusSetter = View.class.getMethod("setMiBackgroundBlurRadius", int.class);
            module.hook(radiusSetter).setId("incall-background-radius")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (Boolean.TRUE.equals(REPLAYING.get())) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        if (!isLoaded()) markLoaded(view.getContext());
                        int stock = (Integer) chain.getArg(0);
                        RadiusState state;
                        synchronized (TARGETS) {
                            state = TARGETS.get(view);
                            if (state == null) {
                                state = new RadiusState();
                                TARGETS.put(view, state);
                            }
                        }
                        state.stock = stock;
                        state.hasStock = true;
                        int adjusted = desiredRadius(state);
                        Object result = adjusted == stock ? chain.proceed()
                                : chain.proceed(new Object[]{adjusted});
                        state.applied = adjusted;
                        return result;
                    });
            HookDiagnostics.available(PACKAGE, "通话背景模糊");
        } catch (Throwable error) {
            HookDiagnostics.failure(PACKAGE, "通话背景模糊", error);
        }
        try {
            installContainer(module, loader);
            HookDiagnostics.available(PACKAGE, "通话柔光背景半径");
        } catch (Throwable error) {
            HookDiagnostics.failure(PACKAGE, "通话柔光背景半径", error);
        }
        try {
            installBitmap(module, loader);
            HookDiagnostics.available(PACKAGE, "通话壁纸模糊");
        } catch (Throwable error) {
            generate = null;
            HookDiagnostics.failure(PACKAGE, "通话壁纸模糊", error);
        }
        InCallDimHooks.install(module, loader);
        // The container wrapper is small enough for ART to inline into this sole lifecycle caller.
        try {
            module.deoptimize(Class.forName(
                    "com.android.incallui.background.InCallBackgroundCallback$1", false, loader)
                    .getDeclaredMethod("run"));
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w("MyHyperModifier", "InCall backdrop caller deoptimization unavailable", error);
        }
        onChanged(InCallMaterialHooks::refresh);
    }

    private static int glassConfig() {
        return moduleHooksEnabled && !disableShadeGlassHooks && globalGlassBlurEnabled
                ? GlobalGlassBlurState.clamp(shadeCardGlassBlurRadius) : -1;
    }

    private static int desiredRadius(RadiusState state) {
        return InCallBlurPolicy.containerRadius(state.stock, percent(), state.glassContainer, glassConfig());
    }

    private static void installContainer(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> material = Class.forName("com.android.incallui.util.MaterialUtils", false, loader);
        Class<?> token = Class.forName("D1.j", false, loader);
        Method glassMode = material.getDeclaredMethod("isHyperGlassEnable");
        module.hook(material.getDeclaredMethod("applyContainer", View.class, token))
                .setId("incall-glass-container-radius")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View view = (View) chain.getArg(0);
                    if (view == null) return chain.proceed();
                    if (!isLoaded()) markLoaded(view.getContext());
                    if (chain.getArg(1) == null) {
                        // Invalidate queued radius replays before the stock container is cleared.
                        synchronized (TARGETS) { TARGETS.remove(view); }
                        return chain.proceed();
                    }
                    RadiusState state;
                    synchronized (TARGETS) {
                        state = TARGETS.get(view);
                        if (state == null) {
                            state = new RadiusState();
                            TARGETS.put(view, state);
                        }
                    }
                    // This build disables element Glass and renders Mask_Glass_Light through
                    // MiBackgroundBlur instead. Classify before stock writes its radius.
                    state.glassContainer = Boolean.TRUE.equals(glassMode.invoke(null));
                    Object result = chain.proceed();
                    // Covers recipes that preserve an existing sampler without calling its setter.
                    if (state.hasStock) applyRadius(view, state);
                    return result;
                });
    }

    private static void installBitmap(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.android.incallui.background.InCallBackgroundManager", false, loader);
        cache = type.getDeclaredField("mInCallBg");
        lock = type.getDeclaredField("mLock");
        generating = type.getDeclaredField("mIsGeneratingBackground");
        cache.setAccessible(true);
        lock.setAccessible(true);
        generating.setAccessible(true);
        generate = type.getDeclaredMethod("generateInCallBackground");
        Method destination = type.getDeclaredMethod("getDestinationBitmap", Bitmap.class);
        // Resolve every platform method before installing the dependent interceptors.
        Method setRadius = ScriptIntrinsicBlur.class.getDeclaredMethod("setRadius", float.class);
        Method setInput = ScriptIntrinsicBlur.class.getDeclaredMethod("setInput", Allocation.class);
        Method forEach = ScriptIntrinsicBlur.class.getDeclaredMethod("forEach", Allocation.class);
        Class<?> task = Class.forName("com.android.incallui.background.InCallBackgroundManager$2", false, loader);
        Method background = null;
        for (Method method : task.getDeclaredMethods()) {
            if (method.getName().equals("doInBackground") && method.getReturnType() == Void.class) {
                background = method;
                break;
            }
        }
        if (background == null) throw new NoSuchMethodException("InCall background task");
        module.hook(setRadius).setId("incall-bitmap-radius")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    BitmapPass pass = BITMAP_PASS.get();
                    if (pass == null) return chain.proceed();
                    pass.script = chain.getThisObject();
                    float stock = (Float) chain.getArg(0);
                    float adjusted = InCallBlurPolicy.bitmapRadius(stock, pass.percent);
                    return stock == adjusted ? chain.proceed() : chain.proceed(new Object[]{adjusted});
                });
        module.hook(setInput).setId("incall-bitmap-input")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    BitmapPass pass = BITMAP_PASS.get();
                    if (pass != null && pass.script == chain.getThisObject()) pass.input = (Allocation) chain.getArg(0);
                    return result;
                });
        module.hook(forEach).setId("incall-bitmap-zero-blur")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    BitmapPass pass = BITMAP_PASS.get();
                    if (pass == null || pass.percent != 0 || pass.input == null
                            || pass.script != chain.getThisObject()) return chain.proceed();
                    ((Allocation) chain.getArg(0)).copyFrom(pass.input);
                    return null;
                });
        module.hook(Paint.class.getDeclaredMethod("setAlpha", int.class)).setId("incall-bitmap-dim")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    BitmapPass pass = BITMAP_PASS.get();
                    if (pass == null || pass.dimAlpha < 0) return chain.proceed();
                    Paint paint = (Paint) chain.getThisObject();
                    int stockAlpha = (Integer) chain.getArg(0);
                    int rgb = paint.getColor() & 0x00ffffff;
                    // Verified final wallpaper mask: dark black/80, light white/166.
                    if (!((rgb == 0 && stockAlpha == 80) || (rgb == 0x00ffffff && stockAlpha == 166))) {
                        return chain.proceed();
                    }
                    paint.setColor(0xff000000);
                    return chain.proceed(new Object[]{pass.dimAlpha});
                });
        module.hook(destination).setId("incall-bitmap-pass")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Generation generation = GENERATION_CONFIG.get();
                    if (generation != null) generation.legacy = true;
                    int config = generation == null ? bitmapConfig() : generation.config;
                    BitmapPass previous = BITMAP_PASS.get();
                    // No allocation or ThreadLocal write for the default path.
                    if (config != 100) BITMAP_PASS.set(new BitmapPass(config));
                    try {
                        return chain.proceed();
                    } finally {
                        if (config != 100) {
                            if (previous == null) BITMAP_PASS.remove(); else BITMAP_PASS.set(previous);
                        }
                    }
                });
        // Keep the scoped Paint/RenderScript calls observable inside the original bitmap worker.
        module.deoptimize(destination);
        module.hook(generate).setId("incall-background-owner")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    manager = new WeakReference<>(chain.getThisObject());
                    // Bootstrap the asynchronous settings observer before the stock worker starts.
                    if (!isLoaded()) ensureLoaded();
                    return chain.proceed();
                });
        module.hook(background).setId("incall-background-complete")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    int config = bitmapConfig();
                    Generation generation = new Generation(config);
                    GENERATION_CONFIG.set(generation);
                    try {
                        Object result = chain.proceed();
                        // The material path skips getDestinationBitmap entirely; still mark this
                        // generation consumed, or a custom percentage would regenerate forever.
                        legacyBitmap = generation.legacy;
                        appliedBitmapConfig = config;
                        if (config != bitmapConfig()) scheduleBitmap();
                        return result;
                    } finally { GENERATION_CONFIG.remove(); }
                });
        module.hook(type.getDeclaredMethod("release")).setId("incall-background-release")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (manager.get() == chain.getThisObject()) manager = new WeakReference<>(null);
                    return chain.proceed();
                });
    }

    private static void refresh() {
        int amount = percent();
        boolean backgroundChanged = CONFIG.getAndSet(amount) != amount;
        int glass = glassConfig();
        boolean glassChanged = GLASS_CONFIG.getAndSet(glass) != glass;
        if (backgroundChanged || glassChanged) refreshRadii(!backgroundChanged);
        if (appliedBitmapConfig != bitmapConfig()) scheduleBitmap();
    }

    private static void refreshRadii(boolean onlyGlass) {
        ArrayList<View> targets;
        synchronized (TARGETS) { targets = new ArrayList<>(TARGETS.keySet()); }
        for (View view : targets) {
            if (view == null) continue;
            RadiusState state;
            synchronized (TARGETS) { state = TARGETS.get(view); }
            if (state == null || !state.hasStock || (onlyGlass && !state.glassContainer)
                    || !state.pending.compareAndSet(false, true)) continue;
            if (!view.post(() -> {
                state.pending.set(false);
                synchronized (TARGETS) { if (TARGETS.get(view) != state) return; }
                applyRadius(view, state);
            })) state.pending.set(false);
        }
    }

    private static void applyRadius(View view, RadiusState state) {
        int radius = desiredRadius(state);
        if (state.applied == radius || radiusSetter == null) return;
        try {
            REPLAYING.set(true);
            radiusSetter.invoke(view, radius);
            state.applied = radius;
        } catch (ReflectiveOperationException error) { report(error); }
        finally { REPLAYING.remove(); }
    }

    private static void scheduleBitmap() {
        // Native material owns dim and blur directly; it never needs a wallpaper decode replay.
        if (!legacyBitmap || generate == null || manager.get() == null
                || !BITMAP_PENDING.compareAndSet(false, true)) return;
        // Slider updates coalesce; the stock asynchronous worker performs bitmap processing.
        if (!main.postDelayed(() -> AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            BITMAP_PENDING.set(false);
            Object owner = manager.get();
            if (!legacyBitmap || owner == null || appliedBitmapConfig == bitmapConfig()) return;
            try {
                synchronized (lock.get(owner)) {
                    if (!legacyBitmap || manager.get() != owner || appliedBitmapConfig == bitmapConfig()) return;
                    if (generating.getBoolean(owner)) return; // Completion hook retries, no polling.
                    cache.set(owner, null);
                    // Reentrant stock lock keeps release from interleaving between invalidation
                    // and scheduling. No bitmap work or disk access runs in this call.
                    generate.invoke(owner);
                }
            } catch (ReflectiveOperationException error) { report(error); }
        }), 120)) BITMAP_PENDING.set(false);
    }

    private static void report(Throwable error) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) Log.w("MyHyperModifier", "InCall blur refresh failed", error);
    }
}
