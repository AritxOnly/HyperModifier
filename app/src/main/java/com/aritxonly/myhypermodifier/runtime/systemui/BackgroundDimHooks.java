package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Customizes native backdrop dim while retaining stock color processing. */
final class BackgroundDimHooks {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SHADE_APPLIED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SHADE_FINAL_APPLIED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_CONTENT_APPLIED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_APPLIED_LOGGED = new AtomicBoolean();
    private static volatile int shadeBlendCalls;
    private static volatile int shadeFinalCalls;
    private static volatile int[] lastShadeColors;
    private static final Map<View, Boolean> SHADE_BACKGROUNDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BackgroundDimHooks() {}

    static void installSystemUi(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> controller = Class.forName(
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl", false, loader);
            Class<?> compat = Class.forName("com.miui.systemui.util.MiBlurCompat", false, loader);
            Method start = controller.getDeclaredMethod("start");
            Method setColors = compat.getDeclaredMethod(
                    "setMiBackgroundBlendColors", View.class, int[].class, float.class);
            Class<?> blendBackground = Class.forName(
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlendBackground",
                    false, loader);
            Field blendView = blendBackground.getDeclaredField("view");
            Field blendRecipe = blendBackground.getDeclaredField("blendColors");
            Field blendRatio = blendBackground.getDeclaredField("blurRatio");
            Field blendEnabled = blendBackground.getDeclaredField("enabled");
            blendView.setAccessible(true);
            blendRecipe.setAccessible(true);
            blendRatio.setAccessible(true);
            blendEnabled.setAccessible(true);
            module.hook(controller.getDeclaredMethod("dump", PrintWriter.class, String[].class))
                    .setId("global-background-dim-dump")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        ensureLoaded();
                        PrintWriter out = (PrintWriter) chain.getArg(0);
                        out.println("  MyHyperModifier backdrop: enabled=" + globalBackgroundDimEnabled
                                + ", percent=" + globalBackgroundDimPercent
                                + ", compatCalls=" + shadeBlendCalls + ", finalCalls=" + shadeFinalCalls);
                        out.println("    lastAppliedColors=" + Arrays.toString(lastShadeColors));
                        for (String name : new String[]{"combinedBlendBackground",
                                "notificationBlendBackground", "controlCenterBlendBackground"}) {
                            Field field = controller.getDeclaredField(name);
                            field.setAccessible(true);
                            Object owner = field.get(chain.getThisObject());
                            if (owner != null) out.println("    " + name + ": enabled="
                                    + blendEnabled.getBoolean(owner) + ", ratio=" + blendRatio.getFloat(owner)
                                    + ", colors=" + Arrays.toString((int[]) blendRecipe.get(owner))
                                    + ", view=" + blendView.get(owner));
                        }
                        return result;
                    });
            Method lazyGet = Class.forName("dagger.Lazy", false, loader).getMethod("get");
            String[] names = {"notificationPanelBackground", "controlCenterBackground",
                    "shadeWindowBackground"};
            Field[] backgrounds = new Field[names.length];
            for (int i = 0; i < names.length; i++) {
                backgrounds[i] = controller.getDeclaredField(names[i]);
                backgrounds[i].setAccessible(true);
            }
            module.hook(start)
                    .setId("global-background-dim-owners")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        // Identify exact injected background Views, not all blur API callers.
                        // Register before start: it creates BlendBackground and launches flows.
                        for (Field field : backgrounds) {
                            try {
                                Object lazy = field.get(chain.getThisObject());
                                Object view = lazy == null ? null : lazyGet.invoke(lazy);
                                if (view instanceof View) SHADE_BACKGROUNDS.put((View) view, true);
                            } catch (ReflectiveOperationException | RuntimeException exception) {
                                Log.w(TAG, "Backdrop owner unavailable: " + field.getName(), exception);
                            }
                        }
                        return chain.proceed();
                    });
            module.hook(setColors)
                    .setId("global-background-dim-shade")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Object view = chain.getArg(0);
                        if (!globalBackgroundDimEnabled || !(view instanceof View)
                                || !SHADE_BACKGROUNDS.containsKey((View) view)) {
                            return chain.proceed();
                        }
                        // Preserve stock color processing and the native animation argument;
                        // also covers force-pass blur, which bypasses BlendBackground.
                        int[] colors = BackgroundDimPolicy.blendColors((int[]) chain.getArg(1),
                                globalBackgroundDimPercent);
                        Object result = chain.proceed(new Object[]{view, colors, chain.getArg(2)});
                        lastShadeColors = colors;
                        shadeBlendCalls++;
                        if (SHADE_APPLIED_LOGGED.compareAndSet(false, true)) {
                            Log.i(TAG, "Shade native backdrop dim applied: "
                                    + globalBackgroundDimPercent + "%");
                        }
                        return result;
                    });
            module.hook(blendBackground.getDeclaredMethod("applyBlendColors"))
                    .setId("global-background-dim-final-blend")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object owner = chain.getThisObject();
                        View view = (View) blendView.get(owner);
                        SHADE_BACKGROUNDS.put(view, true);
                        Object result = chain.proceed();
                        ensureLoaded();
                        if (globalBackgroundDimEnabled && blendEnabled.getBoolean(owner)) {
                            shadeFinalCalls++;
                            // Reapply from the stock recipe after the actual refresh. This
                            // covers late installation and inlined calls to MiBlurCompat.
                            setColors.invoke(null, view, blendRecipe.get(owner), blendRatio.getFloat(owner));
                            if (SHADE_FINAL_APPLIED_LOGGED.compareAndSet(false, true)) {
                                Log.i(TAG, "Shade final blend override applied: "
                                        + globalBackgroundDimPercent + "%, view=" + view.getClass().getName());
                            }
                        }
                        return result;
                    });
            Log.i(TAG, "Global backdrop dim hooks installed (notification/control/combined)");
            HookDiagnostics.available("com.android.systemui", "全局背景压暗");
            deoptimizeCallers(module, loader,
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlendBackground",
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$start$2$1");
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            Log.w(TAG, "Global backdrop dim hook unavailable", throwable);
            HookDiagnostics.failure("com.android.systemui", "全局背景压暗", throwable);
        }
        ShadeWindowDimHooks.install(module, loader);
    }

    static void deoptimizeCallers(XposedModule module, ClassLoader loader, String... names) {
        for (String name : names) {
            try {
                for (Method method : Class.forName(name, false, loader).getDeclaredMethods()) {
                    // Remove compiled inlining of the small blend/scale setters in these
                    // known callers; otherwise native View hooks can be bypassed by ART.
                    module.deoptimize(method);
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Log.w(TAG, "Backdrop caller deoptimization unavailable: " + name, exception);
            }
        }
    }

    static void applyMiLink(View background, float animationRatio) {
        if (!globalBackgroundDimEnabled) return;
        try {
            if (MiLinkBlendApi.CLEAR == null || MiLinkBlendApi.ADD == null) return;
            // Stock MiLink has just rewritten its blend list. Replace that list on the same
            // decor View; the next stock update restores it when customization is disabled.
            MiLinkBlendApi.CLEAR.invoke(background);
            MiLinkBlendApi.ADD.invoke(background,
                    BackgroundDimPolicy.blackColor(globalBackgroundDimPercent, animationRatio),
                    BackgroundDimPolicy.SRC_OVER);
            if (MILINK_APPLIED_LOGGED.compareAndSet(false, true)) {
                Log.i(TAG, "MiLink native backdrop dim applied: " + globalBackgroundDimPercent + "%");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (MILINK_FAILURE_LOGGED.compareAndSet(false, true)) {
                Log.w(TAG, "MiLink native backdrop dim unavailable", exception);
            }
        }
    }

    static void clearMiLinkContentDim(android.app.Activity activity) {
        if (!globalBackgroundDimEnabled) return;
        int id = activity.getResources().getIdentifier(
                "activity_content_view", "id", activity.getPackageName());
        View content = id == 0 ? null : activity.findViewById(id);
        if (content != null) {
            content.setBackgroundColor(0);
            if (MILINK_CONTENT_APPLIED_LOGGED.compareAndSet(false, true)) {
                Log.i(TAG, "MiLink activity content dim layer cleared");
            }
        }
    }

    private static final class MiLinkBlendApi {
        static final Method CLEAR = method("clearMiBackgroundBlendColor");
        static final Method ADD = method("addMiBackgroundBlendColor", int.class, int.class);

        private static Method method(String name, Class<?>... parameters) {
            try {
                return View.class.getMethod(name, parameters);
            } catch (ReflectiveOperationException exception) {
                if (MILINK_FAILURE_LOGGED.compareAndSet(false, true)) {
                    Log.w(TAG, "MiLink background blend API unavailable", exception);
                }
                return null;
            }
        }
    }
}
