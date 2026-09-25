package com.aritxonly.myhypermodifier;

import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Adjusts the AllInOne clock's AOD animation target while preserving its stock transition. */
final class AodClockWeightHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String ANIMATION =
            "com.android.keyguard.clock.animation.allinone.AllInOneClockAnimation";
    private static final String STYLE =
            "com.android.keyguard.clock.animation.allinone.ClockStyleParams";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    // ClockStyleParams implements value equality; records must be matched by object identity.
    private static final List<AppliedWeight> ORIGINAL_WEIGHTS = new ArrayList<>();

    private AodClockWeightHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> animation = Class.forName(ANIMATION, false, classLoader);
            Class<?> style = Class.forName(STYLE, false, classLoader);
            Field timeWeight = style.getField("timeWeight");
            Method fontAnimState = animation.getDeclaredMethod(
                    "fontAnimState", String.class, style);
            module.hook(fontAnimState)
                    .setId("all-in-one-aod-clock-weight")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object stateName = chain.getArg(0);
                        if ("all_in_one_aod".equals(stateName)
                                || "all_in_one_full_aod".equals(stateName)) {
                            ModuleSettings.ensureLoaded();
                            applyWeight(chain.getArg(1), timeWeight);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.WARN, TAG, "AllInOne AOD clock weight hook unavailable", throwable);
        }
    }

    private static void applyWeight(Object style, Field field) {
        if (style == null) return;
        try {
            int current = field.getInt(style);
            synchronized (ORIGINAL_WEIGHTS) {
                AppliedWeight previous = null;
                for (Iterator<AppliedWeight> iterator = ORIGINAL_WEIGHTS.iterator();
                        iterator.hasNext(); ) {
                    AppliedWeight candidate = iterator.next();
                    Object tracked = candidate.style.get();
                    if (tracked == null) iterator.remove();
                    else if (tracked == style) previous = candidate;
                }
                if (ModuleSettings.aodClockWeightEnabled) {
                    if (previous == null || current != previous.applied) {
                        if (previous != null) ORIGINAL_WEIGHTS.remove(previous);
                        previous = new AppliedWeight(style, current);
                        ORIGINAL_WEIGHTS.add(previous);
                    }
                    int target = Math.max(100, Math.min(700, ModuleSettings.aodClockWeight));
                    field.setInt(style, target);
                    previous.applied = target;
                } else if (previous != null) {
                    if (current == previous.applied) field.setInt(style, previous.original);
                    ORIGINAL_WEIGHTS.remove(previous);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Adjacent SystemUI revisions keep their original clock style.
        }
    }

    private static final class AppliedWeight {
        final WeakReference<Object> style;
        final int original;
        int applied;

        AppliedWeight(Object style, int original) {
            this.style = new WeakReference<>(style);
            this.original = original;
        }
    }
}
