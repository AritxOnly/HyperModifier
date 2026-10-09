package com.aritxonly.myhypermodifier;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Bypasses HMC's normal notification-room correction, preserving its cover clock ownership. */
final class HyperMusicCoverClockHooks {
    private static final String MAIN = "com.os4.musiccover.Main";
    private static final String FEATURE = "绕过 HyperMusicCover 时钟避让";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile HyperMusicCoverClockPolicy policy;
    private static volatile boolean bindingFailed;

    private HyperMusicCoverClockHooks() {}

    static boolean ownsClock() {
        HyperMusicCoverClockPolicy current = policy;
        if (bindingFailed) return true;
        if (current == null) return false;
        try { return !current.shouldBypass(true); }
        catch (ReflectiveOperationException error) { return true; }
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        if (Build.VERSION.SDK_INT < 34) {
            HookDiagnostics.unavailable("com.android.systemui", FEATURE, "需要 Android 14 或更新版本");
            return;
        }
        // HMC has a separate LSPosed loader. Its callback is on the live stack only INSIDE the
        // hook chain. Lowest priority discovers that class regardless of module startup order.
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            watch(module, loader, "com.android.keyguard.clock.KeyguardClockContainer",
                    "notifStateChange", handles);
            watch(module, loader, "com.android.keyguard.interactor.KeyguardClockNotifInteractor",
                    "setNotifY", handles);
            HookDiagnostics.available("com.android.systemui", FEATURE);
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            INSTALLED.set(false);
            throw error;
        }
    }

    private static void watch(XposedModule module, ClassLoader loader, String name, String methodName,
                              List<XposedInterface.HookHandle> handles) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(name, false, loader);
        boolean found = false;
        for (Method method : owner.getDeclaredMethods()) {
            if (!methodName.equals(method.getName()) || method.getParameterCount() == 0
                    || method.getParameterTypes()[0] != float.class) continue;
            found = true;
            ThreadLocal<Float> originalY = new ThreadLocal<>();
            handles.add(module.hook(method).setId("hmc-clock-discover-before-" + methodName)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ModuleSettings.ensureLoaded();
                        if ((!ModuleSettings.bypassHyperMusicCoverClockAdjustment
                                && !ModuleSettings.progressiveLockscreenClockAvoidance) || policy != null
                                || bindingFailed) return chain.proceed();
                        Float previous = originalY.get();
                        originalY.set((Float) chain.getArg(0));
                        try { return chain.proceed(); }
                        finally {
                            if (previous == null) originalY.remove(); else originalY.set(previous);
                        }
                    }));
            handles.add(module.hook(method).setId("hmc-clock-discover-after-" + methodName)
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Float raw = originalY.get();
                        if (raw == null || policy != null || bindingFailed) return chain.proceed();
                        HyperMusicCoverClockPolicy discovered = bindFromStack(module);
                        if (discovered == null || !discovered.shouldBypass(
                                ModuleSettings.bypassHyperMusicCoverClockAdjustment)) return chain.proceed();
                        // The discovery call already passed through HMC's correction. Restore
                        // just this first call; subsequent calls bypass HMC's helpers themselves.
                        discovered.clearRoomEase();
                        Object[] args = chain.getArgs().toArray();
                        args[0] = raw;
                        return chain.proceed(args);
                    }));
            module.deoptimize(method);
        }
        if (!found) throw new NoSuchMethodException(name + '.' + methodName);
    }

    private static synchronized HyperMusicCoverClockPolicy bindFromStack(XposedModule module) {
        if (policy != null || bindingFailed) return policy;
        Class<?> main = StackClasses.findMain();
        if (main == null) return null; // HMC absent or not in this call; leave all arguments alone.
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            HyperMusicCoverClockPolicy candidate = new HyperMusicCoverClockPolicy(main);
            for (Method method : new Method[]{candidate.roomForRows, candidate.roomForRowsNow,
                    candidate.reassertClockRoom}) {
                handles.add(module.hook(method).setId("hmc-clock-bypass-" + method.getName())
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            ModuleSettings.ensureLoaded();
                            if (!candidate.shouldBypass(ModuleSettings.bypassHyperMusicCoverClockAdjustment))
                                return chain.proceed();
                            candidate.clearRoomEase();
                            return method.getReturnType() == float.class ? chain.getArg(0) : null;
                        }));
                module.deoptimize(method);
            }
            policy = candidate;
            module.log(Log.INFO, "MyHyperModifier", "Bound HyperMusicCover notification-room bypass");
            return candidate;
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            bindingFailed = true;
            HookDiagnostics.failure("com.android.systemui", FEATURE, error);
            return null;
        }
    }

    /** Kept separate so Android 13 never resolves StackWalker, introduced in API 34. */
    private static final class StackClasses {
        static Class<?> findMain() {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
                    frames.filter(frame -> MAIN.equals(frame.getClassName()))
                            .map(StackWalker.StackFrame::getDeclaringClass).findFirst().orElse(null));
        }
    }
}
