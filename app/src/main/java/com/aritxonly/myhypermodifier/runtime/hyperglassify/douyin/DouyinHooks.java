package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Main-process-only lifecycle and app-owned tab input hooks for the supplied 40.7.0 APK. */
final class DouyinHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    // Main-thread lifecycle/touch state only; never retain an Activity through a static value.
    private static final Set<Activity> CREATED = Collections.newSetFromMap(new WeakHashMap<>());
    private DouyinHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            DouyinWindowGuard.install(module);
            DouyinPlayerTextureHooks.install(module, loader);
            Class<?> main = Class.forName("com.ss.android.ugc.aweme.main.MainActivity", false, loader);
            installChatVisibility(module, loader, main);
            installCollectionClearance(module, loader);
            hook(module, main.getDeclaredMethod("onCreate", Bundle.class), "create", chain -> {
                Activity activity = (Activity) chain.getThisObject();
                // AppCompat must create its own content/sub-decor first. Reading window.decorView
                // before this call forces framework decor installation ahead of its factory.
                Object result = chain.proceed();
                CREATED.add(activity);
                DouyinFloatingNavigation.attach(activity);
                return result;
            });
            hook(module, main.getDeclaredMethod("onResume"), "resume", chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (CREATED.contains(activity)) DouyinFloatingNavigation.attach(activity);
                return result;
            });
            hook(module, main.getDeclaredMethod("onPause"), "pause", chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (CREATED.contains(activity)) DouyinFloatingNavigation.pause(activity);
                return chain.proceed();
            });
            hook(module, main.getDeclaredMethod("onDestroy"), "destroy", chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (CREATED.remove(activity)) DouyinFloatingNavigation.dispose(activity);
                return chain.proceed();
            });
            hook(module, main.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class), "touch", chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (CREATED.contains(activity)) DouyinFloatingNavigation.onTouchEvent(activity, (MotionEvent) chain.getArg(0));
                return result;
            });
            hook(module, main.getDeclaredMethod("onWindowFocusChanged", boolean.class), "focus", chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (CREATED.contains(activity)) DouyinFloatingNavigation.focus(activity, (boolean) chain.getArg(0));
                return result;
            });
            Class<?> space = Class.forName("com.ss.android.ugc.aweme.feed.ui.bottom.BottomSpace", false, loader);
            for (java.lang.reflect.Constructor<?> constructor : space.getDeclaredConstructors()) {
                module.hook(constructor).setId("douyin-feed-space-" + constructor.getParameterCount())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            // Register before inflation supplies LayoutParams / the first measure.
                            // This path never touches Decor or the AppCompat content hierarchy.
                            DouyinFeedExpansion.registerSpacer((View) chain.getThisObject());
                            return result;
                        });
            }
            hook(module, space.getDeclaredMethod("LIZIZ", int.class, String.class), "feed-space-visibility", chain -> {
                int requested = (int) chain.getArg(0);
                int effective = DouyinFeedExpansion.nativeVisibility((View) chain.getThisObject(), requested);
                if (effective == requested) return chain.proceed();
                return chain.proceed(new Object[] {effective, chain.getArg(1)});
            });
            for (String viewClass : new String[] {
                    "com.ss.android.ugc.aweme.ad.feed.VideoViewHolderRootView",
                    "com.ss.android.ugc.aweme.o.seekbar.DProSeekBar",
                    "com.ss.android.ugc.aweme.feed.widget.LineProgressBar",
                    "com.ss.android.ugc.aweme.feed.ui.seekbar.CustomizedUISeekBar"}) {
                Class<?> page = Class.forName(viewClass, false, loader);
                for (java.lang.reflect.Constructor<?> constructor : page.getDeclaredConstructors()) {
                    module.hook(constructor).setId("douyin-feed-controls-" + viewClass + constructor.getParameterCount())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                                Object result = chain.proceed();
                                if (ModuleSettings.moduleHooksEnabled && ModuleSettings.douyinFloatingNavigationEnabled)
                                    DouyinFloatingNavigation.onFeedPageCreated((View) chain.getThisObject());
                                return result;
                            });
                }
            }
            Class<?> bar = Class.forName("X.0gnN", false, loader);
            // Stop input before the app-owned OnTouchListeners run on invisible tabs.
            hook(module, nativeTouchMethod(bar), "native-bar-touch", chain -> {
                if (!CREATED.isEmpty() && DouyinFloatingNavigation.blocksNativeInput((View) chain.getThisObject())) return true;
                return chain.proceed();
            });
        } catch (Throwable error) {
            module.log(Log.ERROR, "MyHyperModifier", "Could not install Douyin navigation", error);
            HookDiagnostics.failure("com.ss.android.ugc.aweme", "抖音柔光底栏", error);
        }
    }

    private static void installChatVisibility(XposedModule module, ClassLoader loader, Class<?> main) {
        try {
            Class<?> chat = Class.forName("com.ss.android.ugc.aweme.im.business.chat.root.RipsChatRoomFullScreenFragment", false, loader);
            Method getActivity = chat.getMethod("getActivity");
            hook(module, chat.getDeclaredMethod("onViewCreated", View.class, Bundle.class), "chat-view", chain -> {
                Object result = chain.proceed();
                Object owner = getActivity.invoke(chain.getThisObject());
                if (main.isInstance(owner))
                    DouyinFloatingNavigation.onChatViewCreated((Activity) owner, (View) chain.getArg(0));
                return result;
            });
        } catch (ReflectiveOperationException error) {
            module.log(Log.WARN, "MyHyperModifier", "Douyin chat visibility unavailable; follow native bar state", error);
        }
    }

    private static void installCollectionClearance(XposedModule module, ClassLoader loader) {
        try {
            XposedInterface.Hooker created = chain -> {
                Object result = chain.proceed();
                if (result instanceof View) DouyinFloatingNavigation.onCollectionCreated((View) result);
                return result;
            };
            Class<?> mix = Class.forName("com.ss.android.ugc.aweme.feed.quick.uimodule.FeedBottomMixModule", false, loader);
            hook(module, mix.getDeclaredMethod("onCreateView", android.content.Context.class, android.view.ViewGroup.class),
                    "collection-view", created);
            Class<?> flux = Class.forName("com.ss.android.ugc.aweme.flux.register.bottom.bar.FeedBottomMixComponentRegister", false, loader);
            hook(module, collectionFluxMethod(flux), "collection-flux-view", created);
        } catch (ReflectiveOperationException error) {
            module.log(Log.WARN, "MyHyperModifier", "Douyin collection anchor unavailable; follow native footer", error);
        }
    }

    private static Method collectionFluxMethod(Class<?> type) throws NoSuchMethodException {
        // Avoid feeding the app's non-Java obfuscated parameter name to Lint's type parser.
        // This metadata search runs once at installation, never during creation/layout/drawing.
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals("LJIIIIZZ") && method.getParameterCount() == 3 &&
                    method.getReturnType() == View.class) return method;
        }
        throw new NoSuchMethodException("Douyin collection view factory");
    }

    // Keep app-owned obfuscated types out of Lint's Java source type parser.
    private static Method nativeTouchMethod(Class<?> bar) throws NoSuchMethodException {
        return bar.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
    }

    private static void hook(XposedModule module, Method method, String id, XposedInterface.Hooker hooker) {
        module.hook(method).setId("douyin-floating-navigation-" + id)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }
}
