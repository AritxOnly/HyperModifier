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

/** Owns sampling scale on the BlurProvider's container, independently of tint and radius. */
final class ShadeBackgroundScaleHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean APPLIED = new AtomicBoolean();
    // Null means the provider is known but no scale write has been observed yet.
    private static final Map<View, Float> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> provider = Class.forName(
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlurProvider", false, loader);
            Field view = provider.getDeclaredField("view");
            Field enabled = provider.getDeclaredField("enableScale");
            Field ratio = provider.getDeclaredField("blurRatio");
            Field maxScale = Class.forName("com.miui.systemui.util.MiBlurCompat", false, loader)
                    .getDeclaredField("MAX_SCALE_RADIUS");
            view.setAccessible(true);
            enabled.setAccessible(true);
            ratio.setAccessible(true);
            maxScale.setAccessible(true);
            Method setter = View.class.getMethod("setMiBackgroundBlurScaleRatio", float.class);
            Method update = provider.getDeclaredMethod("setBlurRatio", float.class);
            module.hook(setter).setId("shade-background-scale-writes")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        View target = (View) chain.getThisObject();
                        if (!OWNERS.containsKey(target)) return chain.proceed();
                        float effective = shadeBackgroundScaleDisabled ? 0f : (Float) chain.getArg(0);
                        Object result = shadeBackgroundScaleDisabled
                                ? chain.proceed(new Object[]{effective}) : chain.proceed();
                        OWNERS.put(target, effective);
                        return result;
                    });
            module.hook(update).setId("shade-background-scale-provider")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        View target = (View) view.get(chain.getThisObject());
                        if (!OWNERS.containsKey(target)) OWNERS.put(target, null);
                        Object result = chain.proceed();
                        ensureLoaded();
                        // Check after stock, which may skip its setter for an unchanged ratio.
                        // Keep stock enableScale so switching off restores it; only supply a
                        // missing/different write instead of replaying the same value per frame.
                        float stock = enabled.getBoolean(chain.getThisObject())
                                ? ratio.getFloat(chain.getThisObject()) * maxScale.getFloat(null) : 0f;
                        float desired = shadeBackgroundScaleDisabled ? 0f : stock;
                        Float written = OWNERS.get(target);
                        if (written == null || Float.compare(written, desired) != 0) {
                            setter.invoke(target, desired);
                        }
                        if (shadeBackgroundScaleDisabled && APPLIED.compareAndSet(false, true)) {
                            Log.i("MyHyperModifier", "Shade background sampling scale disabled on "
                                    + target.getClass().getName());
                        }
                        return result;
                    });
            BackgroundDimHooks.deoptimizeCallers(module, loader,
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl",
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$BlurProvider",
                    "com.miui.systemui.shade.blur.ShadeBlendBlurControllerImpl$start$2$1");
            Log.i("MyHyperModifier", "Independent shade background scale hooks installed");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            INSTALLED.set(false);
            throw exception;
        }
    }
}
