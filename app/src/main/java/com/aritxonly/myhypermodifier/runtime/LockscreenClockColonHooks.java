package com.aritxonly.myhypermodifier;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Keeps the colon in the horizontal AllInOne clock's style and layout calculations. */
final class LockscreenClockColonHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String CLOCK_BEAN = "com.miui.clock.module.ClockBean";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private LockscreenClockColonHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> clockBean = Class.forName(CLOCK_BEAN, false, classLoader);
            Method isColonShow = clockBean.getDeclaredMethod("isColonShow");
            module.hook(isColonShow)
                    .setId("lockscreen-clock-force-colon")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ModuleSettings.ensureLoaded();
                        return ModuleSettings.forceLockscreenClockColon
                                ? Boolean.TRUE : chain.proceed();
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.WARN, TAG, "Lockscreen clock colon hook unavailable", throwable);
        }
    }
}
