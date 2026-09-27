package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Replaces only native backdrop color mixing; never creates a blur view or window dim layer. */
final class BackgroundDimHooks {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SHADE_APPLIED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MILINK_APPLIED_LOGGED = new AtomicBoolean();
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
                        // Preserve the native animation argument. Replacing the supplied array
                        // also covers force-pass blur, which bypasses BlendBackground entirely.
                        Object result = chain.proceed(new Object[]{view,
                                BackgroundDimPolicy.blendColors(globalBackgroundDimPercent),
                                chain.getArg(2)});
                        if (SHADE_APPLIED_LOGGED.compareAndSet(false, true)) {
                            Log.i(TAG, "Shade native backdrop dim applied: "
                                    + globalBackgroundDimPercent + "%");
                        }
                        return result;
                    });
            Log.i(TAG, "Global backdrop dim hooks installed (notification/control/combined)");
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            Log.w(TAG, "Global backdrop dim hook unavailable", throwable);
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
