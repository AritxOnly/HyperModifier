package com.aritxonly.myhypermodifier;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/** Covers shade window color and self-blur dim, which do not use background blend colors. */
final class ShadeWindowDimHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLIED = new AtomicBoolean();
    private static final Map<View, Integer> BACKGROUNDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> controller = Class.forName(
                    "com.miui.systemui.shade.blur.ShadeWindowBlurControllerImpl", false, loader);
            Field background = controller.getDeclaredField("windowBackground");
            Field dimAlpha = controller.getDeclaredField("dimAlpha");
            Field dimRgb = controller.getDeclaredField("dimRGB");
            background.setAccessible(true);
            dimAlpha.setAccessible(true);
            dimRgb.setAccessible(true);
            Method lazyGet = Class.forName("dagger.Lazy", false, loader).getMethod("get");
            module.hook(controller.getDeclaredMethod("start"))
                    .setId("shade-window-dim-owner")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        View view = (View) lazyGet.invoke(background.get(chain.getThisObject()));
                        int id = view.getResources().getIdentifier("shade_dim_color", "color", "com.android.systemui");
                        if (id != 0) BACKGROUNDS.put(view, id);
                        return chain.proceed();
                    });
            module.hook(View.class.getDeclaredMethod("setBackgroundColor", int.class))
                    .setId("shade-window-dim-color")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        Integer colorId = BACKGROUNDS.get(chain.getThisObject());
                        if (!globalBackgroundDimEnabled || colorId == null) return chain.proceed();
                        View view = (View) chain.getThisObject();
                        int baseAlpha = view.getContext().getColor(colorId) >>> 24;
                        int color = (Integer) chain.getArg(0);
                        int adjusted = BackgroundDimPolicy.windowColor(color, baseAlpha,
                                globalBackgroundDimPercent);
                        Object result = chain.proceed(new Object[]{adjusted});
                        if (APPLIED.compareAndSet(false, true)) {
                            Log.i("MyHyperModifier", "Shade window dim override applied: "
                                    + globalBackgroundDimPercent + "%");
                        }
                        return result;
                    });
            module.hook(controller.getDeclaredMethod("applyMiSelfBlurEffect", List.class, float.class))
                    .setId("shade-window-self-blur-dim")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (!globalBackgroundDimEnabled) return chain.proceed();
                        Object owner = chain.getThisObject();
                        int alpha = dimAlpha.getInt(owner);
                        int rgb = dimRgb.getInt(owner);
                        try {
                            dimAlpha.setInt(owner, BackgroundDimPolicy.blackColor(
                                    globalBackgroundDimPercent, 1f) >>> 24);
                            dimRgb.setInt(owner, 0);
                            return chain.proceed();
                        } finally {
                            dimAlpha.setInt(owner, alpha);
                            dimRgb.setInt(owner, rgb);
                        }
                    });
            BackgroundDimHooks.deoptimizeCallers(module, loader,
                    "com.miui.systemui.shade.blur.ShadeWindowBlurControllerImpl",
                    "com.miui.systemui.shade.blur.ShadeWindowBlurControllerImpl$start$1$1$1");
            Log.i("MyHyperModifier", "Shade window dim hooks installed");
        } catch (Throwable error) {
            INSTALLED.set(false);
            HookDiagnostics.failure("com.android.systemui", "窗口背景压暗", error);
        }
    }
}
