package com.aritxonly.myhypermodifier;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.Collections;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Coerce only registered main-activity windows; avoids native/module flag oscillation. */
final class DouyinWindowGuard {
    private static final Set<Window> WINDOWS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> DECORS = Collections.newSetFromMap(new WeakHashMap<>());
    private static XposedModule hooker;
    private static final Set<Object> STUBS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Class<?>> STUB_HOOKED = new java.util.HashSet<>();
    private static final WeakHashMap<Window, StubState> STATES = new WeakHashMap<>();
    static void register(Window window) {
        WINDOWS.add(window); DECORS.add(window.getDecorView());
        if (STATES.containsKey(window)) return;
        try {
            View decor = window.getDecorView();
            Object stub = method(decor.getClass(), "getDecorViewImmersiveStub").invoke(decor);
            if (stub == null) return;
            Method setter = method(stub.getClass(), "setForceImmersiveNavBar", boolean.class);
            setter.setAccessible(true);
            Method getter = method(stub.getClass(), "isForceImmersive");
            getter.setAccessible(true);
            Method colors = method(decor.getClass(), "updateColorViews", android.view.WindowInsets.class, boolean.class);
            colors.setAccessible(true);
            boolean original = (boolean) getter.invoke(stub);
            if (!STUB_HOOKED.contains(stub.getClass())) {
                hooker.hook(setter).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (STUBS.contains(chain.getThisObject())) return chain.proceed(new Object[] {true});
                    return chain.proceed();
                });
                STUB_HOOKED.add(stub.getClass());
            }
            STUBS.add(stub);
            STATES.put(window, new StubState(stub, decor, original, setter, colors));
            setter.invoke(stub, true);
            colors.invoke(decor, null, false);
            android.util.Log.w("MyHyperModifier", "Douyin HyperOS native navigation immersion active");
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("MyHyperModifier", "Douyin HyperOS immersion unavailable; use standard window flags: " + error);
        }
    }
    static void unregister(Window window) {
        WINDOWS.remove(window); DECORS.remove(window.getDecorView());
        StubState state = STATES.remove(window);
        if (state == null) return;
        Object stub = state.stub.get();
        STUBS.remove(stub);
        try {
            if (stub != null) state.setter.invoke(stub, state.original);
            View decor = state.decor.get();
            if (decor != null) state.colors.invoke(decor, null, false);
        } catch (ReflectiveOperationException ignored) {}
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name, args);
                result.setAccessible(true);
                return result;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static final class StubState {
        final WeakReference<Object> stub;
        final WeakReference<View> decor;
        final boolean original;
        final Method setter, colors;
        StubState(Object stub, View decor, boolean original, Method setter, Method colors) {
            this.stub = new WeakReference<>(stub); this.decor = new WeakReference<>(decor);
            this.original = original; this.setter = setter; this.colors = colors;
        }
    }
    static void install(XposedModule module) throws ReflectiveOperationException {
        hooker = module;
        Class<?> phone = Class.forName("com.android.internal.policy.PhoneWindow");
        for (String name : new String[] {"setNavigationBarColor", "setNavigationBarDividerColor"}) {
            module.hook(phone.getMethod(name, int.class)).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (WINDOWS.contains(chain.getThisObject())) return chain.proceed(new Object[] {0});
                        return chain.proceed();
                    });
        }
        module.hook(phone.getMethod("setNavigationBarContrastEnforced", boolean.class))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (WINDOWS.contains(chain.getThisObject())) return chain.proceed(new Object[] {false});
                    return chain.proceed();
                });
        module.hook(View.class.getMethod("setSystemUiVisibility", int.class))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!DECORS.contains(chain.getThisObject())) return chain.proceed();
                    int flags = (int) chain.getArg(0);
                    return chain.proceed(new Object[] {flags | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION});
                });
        module.hook(Window.class.getMethod("setFlags", int.class, int.class))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!WINDOWS.contains(chain.getThisObject())) return chain.proceed();
                    int translucent = WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
                    int backgrounds = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                    return chain.proceed(new Object[] {((int) chain.getArg(0) & ~translucent) | backgrounds,
                            (int) chain.getArg(1) | translucent | backgrounds});
                });
    }
}
