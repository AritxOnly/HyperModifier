package com.aritxonly.myhypermodifier;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Changes only the hour formatter while AllInOne builds its display/layout strings. */
final class LockscreenClockLeadingZeroHooks {
    private static final String FEATURE = "锁屏时钟小时前导零";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> NEXT_IS_HOUR = new ThreadLocal<>();

    private LockscreenClockLeadingZeroHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> base = Class.forName("com.miui.clock.MiuiBaseClock2", false, loader);
            Class<?> clock = Class.forName("com.miui.clock.allInOne.AllInOneBase", false, loader);
            Class<?> utils = Class.forName("com.miui.clock.utils.ClassicClockTimeUtils", false, loader);
            Field is24Hour = base.getDeclaredField("m24HourFormat");
            is24Hour.setAccessible(true);
            Method refresh = clock.getDeclaredMethod("refreshFullTimeStrings");
            Method format = utils.getDeclaredMethod("getTimeString", int.class, boolean.class);
            // Install the formatter first: without an active refresh scope this is a no-op.
            module.hook(format)
                    .setId("lockscreen-clock-hour-no-padding")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (!Boolean.TRUE.equals(NEXT_IS_HOUR.get())) return chain.proceed();
                        NEXT_IS_HOUR.set(false);
                        return chain.proceed(new Object[]{chain.getArg(0), Boolean.FALSE});
                    });
            module.hook(refresh)
                    .setId("lockscreen-clock-hour-format-scope")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ModuleSettings.ensureLoaded();
                        Boolean previous = NEXT_IS_HOUR.get();
                        NEXT_IS_HOUR.set(ModuleSettings.hideLockscreenClockLeadingZero
                                && is24Hour.getBoolean(chain.getThisObject()));
                        try {
                            // Stock refresh formats hours first, minutes second, and updates
                            // mColonIdx and both full-time strings before measuring the layout.
                            return chain.proceed();
                        } finally {
                            if (previous == null) NEXT_IS_HOUR.remove();
                            else NEXT_IS_HOUR.set(previous);
                        }
                    });
            HookDiagnostics.available("com.android.systemui", FEATURE);
        } catch (Throwable throwable) {
            module.log(Log.WARN, "MyHyperModifier", "Lockscreen hour padding hook unavailable", throwable);
            HookDiagnostics.failure("com.android.systemui", FEATURE, throwable);
        }
    }
}
