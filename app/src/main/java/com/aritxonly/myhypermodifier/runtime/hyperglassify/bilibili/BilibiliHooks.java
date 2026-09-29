package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Official Bilibili 9.13.0 lifecycle, with original TabHost routing retained. */
final class BilibiliHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String MAIN_ACTIVITY =
            "tv.danmaku.bili.MainActivityV2";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile XposedModule logger;

    private BilibiliHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        logger = module;
        try {
            Class<?> mainActivity = Class.forName(MAIN_ACTIVITY, false, classLoader);
            Method onCreate = mainActivity.getDeclaredMethod("onCreate", Bundle.class);

            module.hook(onCreate)
                    .setId("bilibili-floating-navigation-create")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        boolean isMain = mainActivity.isInstance(target) && target instanceof Activity;
                        if (isMain) BilibiliFloatingNavigation.prepare((Activity) target);
                        Object result;
                        try {
                            result = chain.proceed();
                        } catch (Throwable throwable) {
                            if (isMain) BilibiliFloatingNavigation.dispose((Activity) target);
                            throw throwable;
                        }
                        if (isMain) BilibiliFloatingNavigation.attach((Activity) target);
                        return result;
                    });

            installResumeHook(module, mainActivity);
            installDestroyHook(module, mainActivity);
            installTouchHook(module, mainActivity);
            installPauseHook(module, mainActivity);
            installFocusHook(module, mainActivity);
            installTabClickHook(module, classLoader);
            installPublishTouchHook(module, classLoader);
            installHomeInsetsHook(module, classLoader);
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG, "Could not install Bilibili navigation hooks", throwable);
        }
    }

    static void logDiagnostic(String message) {
        XposedModule module = logger;
        if (module != null) module.log(Log.INFO, TAG, "Bilibili: " + message);
    }

    private static void installResumeHook(XposedModule module, Class<?> mainActivity) {
        try {
            module.hook(mainActivity.getDeclaredMethod("onResume"))
                    .setId("bilibili-floating-navigation-resume")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            BilibiliFloatingNavigation.attach((Activity) target);
                            BilibiliFloatingNavigation.setForeground((Activity) target, true);
                        }
                        return result;
                    });

        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili resume hook unavailable", throwable);
        }
    }

    private static void installDestroyHook(XposedModule module, Class<?> mainActivity) {
        try {
            module.hook(mainActivity.getDeclaredMethod("onDestroy"))
                    .setId("bilibili-floating-navigation-destroy")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (mainActivity.isInstance(target) && target instanceof Activity) {
                            BilibiliFloatingNavigation.dispose((Activity) target);
                        }
                        return chain.proceed();
                    });

        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili destroy hook unavailable", throwable);
        }
    }

    private static void installTouchHook(XposedModule module, Class<?> mainActivity) {
        try {
            Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            module.hook(dispatchTouchEvent)
                    .setId("bilibili-floating-navigation-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object target = chain.getThisObject();
                        Object event = chain.getArg(0);
                        if (mainActivity.isInstance(target) && target instanceof Activity
                                && event instanceof MotionEvent) {
                            BilibiliFloatingNavigation.onTouchEvent(
                                    (Activity) target, (MotionEvent) event);
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili touch hook unavailable", throwable);
        }
    }

    private static void installPauseHook(XposedModule module, Class<?> mainActivity) {
        try {
            module.hook(mainActivity.getDeclaredMethod("onPause"))
                    .setId("bilibili-floating-navigation-pause")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (chain.getThisObject() instanceof Activity) {
                            BilibiliFloatingNavigation.setForeground((Activity) chain.getThisObject(), false);
                        }
                        return chain.proceed();
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili pause hook unavailable", throwable);
        }
    }

    private static void installFocusHook(XposedModule module, Class<?> mainActivity) {
        try {
            module.hook(mainActivity.getDeclaredMethod("onWindowFocusChanged", boolean.class))
                    .setId("bilibili-floating-navigation-focus")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getThisObject() instanceof Activity) {
                            BilibiliFloatingNavigation.refresh((Activity) chain.getThisObject());
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili focus hook unavailable", throwable);
        }
    }

    /** Optional app internals must not prevent the Activity lifecycle from attaching the dock. */
    private static void installTabClickHook(XposedModule module, ClassLoader classLoader) {
        try {
            // Hook the app callback, not ViewGroup.dispatchTouchEvent for every scrolling row.
            Class<?> tabClick = Class.forName(
                    "com.bilibili.lib.homepage.widget.TabHost$a", false, classLoader);
            module.hook(tabClick.getDeclaredMethod("onClick", View.class))
                    .setId("bilibili-native-bottom-bar-click")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getArg(0);
                        if (target instanceof View
                                && BilibiliFloatingNavigation.blocksNativeInput((View) target)) {
                            return null;
                        }
                        return chain.proceed();
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili native tab click hook unavailable", throwable);
        }
    }

    private static void installPublishTouchHook(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> publishView = Class.forName(
                    "com.bilibili.lib.homepage.widget.HomeTabPublishView", false, classLoader);
            module.hook(publishView.getDeclaredMethod("onTouch", View.class, MotionEvent.class))
                    .setId("bilibili-native-publish-touch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object target = chain.getThisObject();
                        if (target instanceof View
                                && BilibiliFloatingNavigation.blocksNativeInput((View) target)) {
                            return true;
                        }
                        return chain.proceed();
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili publish touch hook unavailable", throwable);
        }
    }

    private static void installHomeInsetsHook(XposedModule module, ClassLoader classLoader) {
        try {
            // Keep the app's listener authoritative; adjust its bottom inset after it runs.
            Class<?> mainInsets = Class.forName(
                    "tv.danmaku.bili.components.a", false, classLoader);
            // Resolve the host signature from its own class. R8 rewrites Class.forName's
            // AndroidX string to the module's obfuscated class name ("l52" in 1.4.1), which
            // does not exist in Bilibili's ClassLoader and aborted all hook installation.
            Method applyInsets = null;
            for (Method candidate : mainInsets.getDeclaredMethods()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (candidate.getName().equals("onApplyWindowInsets") && parameters.length == 2
                        && View.class.isAssignableFrom(parameters[0])) {
                    applyInsets = candidate;
                    break;
                }
            }
            if (applyInsets == null) throw new NoSuchMethodException(
                    "Bilibili home OnApplyWindowInsetsListener");
            module.hook(applyInsets)
                    .setId("bilibili-home-navigation-insets")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (chain.getArg(0) instanceof View) {
                            BilibiliFloatingNavigation.onHomeInsets((View) chain.getArg(0));
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Bilibili home inset hook unavailable", throwable);
        }
    }
}
