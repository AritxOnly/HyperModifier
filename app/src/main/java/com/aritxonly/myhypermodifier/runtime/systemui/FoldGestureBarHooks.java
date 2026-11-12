package com.aritxonly.myhypermodifier;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Uses the ROM's hide_gesture_line handler to remove the navigation window and its inset. */
final class FoldGestureBarHooks {
    static final String KEY = "fold_inner_hide_gesture_bar";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile boolean hidden;

    static boolean hidden() { return hidden; }

    static void install(XposedModule module, ClassLoader loader) throws Throwable {
        if (!"lhasa".equals(Build.DEVICE) || !INSTALLED.compareAndSet(false, true)) return;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> type = Class.forName("com.android.systemui.statusbar.phone.NavigationModeControllerInjector", false, loader);
            Field context = type.getDeclaredField("context");
            Field hide = type.getDeclaredField("mHideGestureLine");
            Field fsg = type.getDeclaredField("mIsFsgMode");
            Field handler = type.getDeclaredField("mHandler");
            Class<?> dependencies = Class.forName("com.android.systemui.Dependency", false, loader);
            Field dependency = dependencies.getDeclaredField("sDependency");
            Field nav = dependencies.getDeclaredField("mNavigationBarController");
            Method get = nav.getType().getMethod("get");
            Field injector = Class.forName("com.android.systemui.navigationbar.NavigationBarControllerImpl", false, loader)
                    .getDeclaredField("mNavigationModeControllerInjector");
            Binding binding = new Binding(context, hide, fsg, handler, () -> {
                Object container = dependency.get(null);
                if (container == null) return null;
                Object lazy = nav.get(container);
                return lazy == null ? null : injector.get(get.invoke(lazy));
            });
            for (var constructor : type.getDeclaredConstructors()) {
                handles.add(module.hook(constructor).setId("fold-gesture-owner")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            binding.attach(chain.getThisObject());
                            return result;
                        }));
            }
            handles.add(module.hook(type.getDeclaredMethod("updateOverlayManager")).setId("fold-gesture-mode")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        binding.attach(chain.getThisObject());
                        return result;
                    }));
            Class<?> dispatch = handler.getType();
            Field handlerOwner = dispatch.getDeclaredField("this$0");
            handles.add(module.hook(dispatch.getDeclaredMethod("handleMessage", Message.class))
                    .setId("fold-gesture-native-dispatch")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Message original = (Message) chain.getArg(0);
                        if (!hidden || original.what != 0
                                || !fsg.getBoolean(handlerOwner.get(chain.getThisObject()))) return chain.proceed();
                        // A queued stock "show" from before a fold must not recreate the inset.
                        Message remove = Message.obtain(original);
                        remove.what = 1;
                        try { return chain.proceed(new Object[]{remove}); }
                        finally { remove.recycle(); }
                    }));
            ModuleSettings.onChanged(binding::settingsChanged);
            HookDiagnostics.available("com.android.systemui", "内屏隐藏手势条与占位");
        } catch (Throwable error) {
            for (var handle : handles) handle.unhook();
            INSTALLED.set(false);
            throw error;
        }
    }

    private interface OwnerLookup { Object get() throws ReflectiveOperationException; }

    private static final class Binding {
        final Field context, hide, fsg, handler;
        final OwnerLookup lookup;
        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean queued = new AtomicBoolean();
        volatile WeakReference<Object> owner = new WeakReference<>(null);
        WeakReference<Context> application = new WeakReference<>(null);
        Handler worker;
        boolean lastEnabled, observing;
        volatile boolean invalidated = true;
        int lastWidth, lastHeight;
        final Runnable update = this::update;

        Binding(Field context, Field hide, Field fsg, Field handler, OwnerLookup lookup) {
            this.lookup = lookup;
            this.context = context;
            this.hide = hide;
            this.fsg = fsg;
            this.handler = handler;
        }

        void attach(Object target) {
            owner = new WeakReference<>(target);
            // All setup, Settings reads and display queries are off the hook caller's thread.
            main.post(() -> {
                if (!enabled() && !hidden) return;
                if (worker == null) {
                    HandlerThread thread = new HandlerThread("mhm-fold-gesture");
                    thread.start();
                    worker = new Handler(thread.getLooper());
                }
                invalidated = true;
                request();
            });
        }

        void settingsChanged() {
            boolean enabled = enabled();
            if (lastEnabled == enabled) return;
            lastEnabled = enabled;
            invalidated = true;
            main.post(() -> {
                if (owner.get() == null && enabled()) {
                    try {
                        Object target = lookup.get();
                        if (target != null) attach(target);
                    } catch (ReflectiveOperationException e) {
                        Log.w("MyHyperModifier", "Fold gesture owner unavailable", e);
                    }
                } else {
                    Object target = owner.get();
                    if (target != null) attach(target);
                }
            });
        }

        boolean enabled() {
            return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                    && ModuleSettings.foldInnerHideGestureBar;
        }

        void request() {
            if (!enabled() && !hidden) return;
            if (worker != null && queued.compareAndSet(false, true)) worker.post(update);
        }

        void update() {
            queued.set(false);
            Object target = owner.get();
            if (target == null) return;
            try {
                Context app = application.get();
                if (app == null) {
                    app = ((Context) context.get(target)).getApplicationContext();
                    if (app == null) return;
                    application = new WeakReference<>(app);
                }
                if (!observing) observe(app);
                Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
                boolean inner = false;
                int width = 0, height = 0;
                if (display != null) {
                    Display.Mode mode = display.getMode();
                    width = mode.getPhysicalWidth();
                    height = mode.getPhysicalHeight();
                    inner = FoldIslandPlacement.isInnerPanel(width, height);
                }
                if (!invalidated && lastWidth == width && lastHeight == height) return;
                invalidated = false;
                lastWidth = width;
                lastHeight = height;
                boolean stock = Settings.Global.getInt(app.getContentResolver(), "hide_gesture_line", 0) != 0;
                boolean apply = enabled() && inner;
                main.post(() -> apply(target, apply, stock));
            } catch (ReflectiveOperationException | RuntimeException error) {
                Log.w("MyHyperModifier", "Fold gesture state unavailable", error);
            }
        }

        void apply(Object target, boolean apply, boolean stock) {
            if (target != owner.get()) return;
            try {
                // Recheck settings after the background read, so a queued update cannot win
                // over a newly disabled master switch or compatibility mode.
                boolean next = apply && enabled() && fsg.getBoolean(target);
                boolean effective = stock || next;
                boolean changed = hide.getBoolean(target) != effective || hidden != next;
                hidden = next;
                hide.setBoolean(target, effective);
                if (!changed) return;
                Handler nativeHandler = (Handler) handler.get(target);
                nativeHandler.removeMessages(0);
                nativeHandler.removeMessages(1);
                nativeHandler.sendEmptyMessage(fsg.getBoolean(target) && effective ? 1 : 0);
                GestureHandleHooks.refresh();
            } catch (ReflectiveOperationException error) {
                Log.w("MyHyperModifier", "Fold gesture window refresh failed", error);
            }
        }

        void observe(Context app) {
            app.getSystemService(DisplayManager.class).registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) {}
                @Override public void onDisplayRemoved(int id) {}
                @Override public void onDisplayChanged(int id) {
                    if (id == Display.DEFAULT_DISPLAY) request();
                }
            }, worker);
            app.registerComponentCallbacks(new ComponentCallbacks() {
                @Override public void onConfigurationChanged(Configuration config) { request(); }
                @Override public void onLowMemory() {}
            });
            ContentObserver observer = new ContentObserver(worker) {
                @Override public void onChange(boolean selfChange) { invalidated = true; request(); }
            };
            app.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("hide_gesture_line"), false, observer);
            app.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor("force_fsg_nav_bar"), false, observer);
            observing = true;
        }
    }

    private FoldGestureBarHooks() {}
}
