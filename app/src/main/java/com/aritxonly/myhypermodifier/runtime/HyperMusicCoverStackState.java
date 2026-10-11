package com.aritxonly.myhypermodifier;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Reads the OEM stack state, not the presence of a media card or HMC's hold alone. */
final class HyperMusicCoverStackState {
    private static volatile WeakReference<Object> flow = new WeakReference<>(null);
    private static Method value;
    private HyperMusicCoverStackState() {}
    static void install(XposedModule module, ClassLoader loader) {
        try {
            Class<?> owner = loader.loadClass("com.miui.systemui.notification.view.viewmodel.NotificationContainerViewModel");
            Method getter = owner.getDeclaredMethod("getCurrentsStackState");
            value = getter.getReturnType().getMethod("getValue");
            value.setAccessible(true);
            module.hook(getter).setId("hmc-extension-stack-state")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (flow.get() != result) { flow = new WeakReference<>(result); HyperMusicCoverExtensions.refresh(); }
                        return result;
                    });
            module.deoptimize(getter);
            // Also capture an already-constructed model when the next stack transition occurs.
            for (Method method : owner.getDeclaredMethods()) if ("notifStackStateToEvent".equals(method.getName())) {
                module.hook(method).setId("hmc-extension-stack-transition-" + method.getParameterCount())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            getter.invoke(chain.getThisObject());
                            HyperMusicCoverExtensions.refresh();
                            return result;
                        });
                module.deoptimize(method);
            }
            HookDiagnostics.available("com.android.systemui", "HMC 全部卡片展开状态");
        } catch (ReflectiveOperationException | RuntimeException error) {
            HookDiagnostics.failure("com.android.systemui", "HMC 全部卡片展开状态", error);
        }
    }
    static void checkAttached() {
        if (ModuleSettings.hmcExpandedFingerprint && current() == null)
            HookDiagnostics.unavailable("com.android.systemui", "HMC 全部卡片展开状态",
                    "未读取到卡片栈状态，自动隐藏和下沉暂不启用");
    }
    static String current() {
        Object source = flow.get();
        if (source == null || value == null) return null;
        try {
            Object state = value.invoke(source);
            return state instanceof Enum<?> ? ((Enum<?>) state).name() : null;
        } catch (ReflectiveOperationException | RuntimeException error) { return null; }
    }
}
