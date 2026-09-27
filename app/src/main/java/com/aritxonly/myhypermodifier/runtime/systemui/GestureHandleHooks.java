package com.aritxonly.myhypermodifier;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Hook points verified against the local September 24 HyperOS SystemUI reference. */
final class GestureHandleHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String HANDLE = "com.android.systemui.navigationbar.gestural.NavigationHandle";
    private static final String TOP_OBSERVER = "com.miui.systemui.functions.MiuiTopActivityObserver";
    private static final String CONTROLLER = "com.android.systemui.navigationbar.NavigationBarControllerImpl";
    private static final Set<Method> HOOKED = ConcurrentHashMap.newKeySet();
    private static final Map<View, GestureHandleFade> HANDLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final GestureHandlePolicy POLICY = new GestureHandlePolicy();
    private static WeakReference<Object> controller = new WeakReference<>(null);
    private static Handler main;
    private static ClassLoader loader;
    private static boolean receiverInstalled;
    private static boolean rulesPresent;
    private static boolean stockHidden;
    private static Context runtimeContext;
    private static long foregroundSequence;
    private static final Map<String, Boolean> SYSTEM_APPS = new ConcurrentHashMap<>();
    private static final ExecutorService APP_LOOKUP = Executors.newSingleThreadExecutor(action -> {
        Thread thread = new Thread(action, "mhm-gesture-app-info");
        thread.setDaemon(true);
        return thread;
    });
    private static final Runnable HIDE = GestureHandleHooks::invalidateHandles;

    private GestureHandleHooks() {}

    static void install(XposedModule module, ClassLoader classLoader) {
        loader = classLoader;
        try {
            Class<?> handle = Class.forName(HANDLE, false, classLoader);
            hook(module, handle.getDeclaredMethod("onDraw", Canvas.class), GestureHandleHooks::draw);
            hook(module, handle.getDeclaredMethod("onAttachedToWindow"), chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                HANDLES.put(view, null);
                initialize(view.getContext());
                ModuleSettings.ensureLoaded();
                refresh();
                return result;
            });
            hook(module, handle.getDeclaredMethod("onDetachedFromWindow"), chain -> {
                HANDLES.remove((View) chain.getThisObject());
                return chain.proceed();
            });
            Class<?> observer = Class.forName(TOP_OBSERVER, false, classLoader);
            hook(module, observer.getDeclaredMethod("updateTopActivity", boolean.class,
                    ActivityManager.RunningTaskInfo.class), chain -> {
                Object result = chain.proceed();
                ActivityManager.RunningTaskInfo info = (ActivityManager.RunningTaskInfo) chain.getArg(1);
                // The stock observer already resolves the foreground task on its background handler.
                dispatch(() -> foreground(info));
                return result;
            });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle / foreground hook unavailable", error);
        }
        try {
            // Landscape quick-switch uses an overriding draw method rather than the base pill.
            Class<?> rotated = Class.forName(
                    "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle", false, classLoader);
            hook(module, rotated.getDeclaredMethod("onDraw", Canvas.class), GestureHandleHooks::draw);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Rotated gesture handle hook unavailable", error);
        }
        try {
            installHostHooks(module, classLoader);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle host override unavailable", error);
        }
    }

    private static Object draw(XposedInterface.Chain chain) throws Throwable {
        View view = (View) chain.getThisObject();
        if (view.getDisplay() == null || view.getDisplay().getDisplayId() != 0) return chain.proceed();
        long now = SystemClock.uptimeMillis();
        boolean systemHidden = rulesPresent && stockHidden;
        boolean hidden = appliesTo(view)
                ? POLICY.hidden(now, systemHidden) : systemHidden;
        GestureHandleFade fade;
        synchronized (HANDLES) {
            fade = HANDLES.get(view);
            if (fade == null) {
                // A newly attached handle starts at its effective state without a hidden flash.
                fade = new GestureHandleFade(hidden);
                HANDLES.put(view, fade);
            }
        }
        float alpha = fade.alpha(hidden, now);
        if (fade.running(now)) view.postInvalidateOnAnimation();
        if (alpha <= 0f) return null;
        if (alpha >= 1f) return chain.proceed();
        // Apply opacity only to the stock pill drawing. Leave View alpha and the native
        // navigation / quick-switch animations, touch handling and insets untouched.
        Canvas canvas = (Canvas) chain.getArg(0);
        int save = canvas.saveLayerAlpha(0f, 0f, view.getWidth(), view.getHeight(), Math.round(alpha * 255f));
        try {
            return chain.proceed();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static void installHostHooks(XposedModule module, ClassLoader classLoader) throws Exception {
        Class<?> type = Class.forName(CONTROLLER, false, classLoader);
        for (Method method : type.getDeclaredMethods()) {
            if ("createNavigationBar".equals(method.getName())) {
                hook(module, method, chain -> {
                    Object owner = chain.getThisObject();
                    controller = new WeakReference<>(owner);
                    Context context = (Context) ReflectiveAccess.fieldValue(owner, "mContext");
                    if (context != null) initialize(context);
                    Object injector = injector(owner);
                    stockHidden = bool(injector, "mHideGestureLine");
                    boolean override = hasPolicies()
                            && bool(injector, "mIsFsgMode") && stockHidden;
                    // Keep a stock host available even if the system-wide hint is hidden. Never
                    // write Settings.Global or change navigation mode / the user's saved setting.
                    if (override) setHiddenFlag(injector, false);
                    try {
                        return chain.proceed();
                    } finally {
                        if (override) {
                            setHiddenFlag(injector, true);
                            stockHidden = true;
                            invalidateHandles();
                        }
                    }
                });
            } else if ("removeNavigationBar".equals(method.getName())) {
                hook(module, method, chain -> {
                    Object owner = chain.getThisObject();
                    Object injector = injector(owner);
                    stockHidden = bool(injector, "mHideGestureLine");
                    Object result = chain.proceed();
                    // Allow stock teardown (including theme / fold changes). Recreate through
                    // the stock creation path, which still checks display and tiny-screen support.
                    if (((Number) chain.getArg(0)).intValue() == 0
                            && hasPolicies()
                            && bool(injector, "mIsFsgMode") && stockHidden) {
                        new Handler(Looper.getMainLooper()).post(GestureHandleHooks::refresh);
                    }
                    return result;
                });
            }
        }
    }

    private static void hook(XposedModule module, Method method,
            XposedInterface.Hooker hooker) {
        if (!HOOKED.add(method)) return;
        module.hook(method).setId("gesture-handle-" + method.getDeclaringClass().getSimpleName() + "-" + method.getName())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }

    private static void initialize(Context context) {
        if (receiverInstalled) return;
        main = new Handler(Looper.getMainLooper());
        runtimeContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        receiverInstalled = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    POLICY.reveal(SystemClock.uptimeMillis());
                    scheduleHide();
                } else {
                    main.removeCallbacks(HIDE);
                }
                invalidateHandles();
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
        // Attach can happen after the observer's initial event, so obtain one bootstrap snapshot.
        new Thread(() -> {
            try {
                Class<?> wrapper = Class.forName(
                        "com.android.systemui.shared.system.ActivityManagerWrapper", false, loader);
                Object instance = wrapper.getField("sInstance").get(null);
                Object result = wrapper.getMethod("getRunningTasks", int.class).invoke(instance, 0);
                if (result instanceof ActivityManager.RunningTaskInfo[]) {
                    ActivityManager.RunningTaskInfo[] tasks = (ActivityManager.RunningTaskInfo[]) result;
                    // Do not overwrite a newer foreground callback with an old async snapshot.
                    dispatch(() -> { if (!foregroundObserved) foreground(tasks.length == 0 ? null : tasks[0]); });
                }
            } catch (Throwable error) {
                Log.w(TAG, "Gesture handle initial task snapshot unavailable", error);
            }
        }, "mhm-gesture-task").start();
    }

    private static boolean foregroundObserved;

    private static void foreground(ActivityManager.RunningTaskInfo info) {
        foregroundObserved = true;
        ComponentName top = info == null ? null : info.topActivity;
        long sequence = ++foregroundSequence;
        // topActivityInfo is a hidden TaskInfo field; access it without linking SDK stubs.
        Object activityInfo = ReflectiveAccess.fieldValue(info, "topActivityInfo");
        ApplicationInfo app = activityInfo instanceof ActivityInfo ? ((ActivityInfo) activityInfo).applicationInfo : null;
        if (app != null && top != null) SYSTEM_APPS.put(top.getPackageName(), isSystemApp(app));
        boolean systemApp = top != null && Boolean.TRUE.equals(SYSTEM_APPS.get(top.getPackageName()));
        POLICY.foreground(top == null ? null : top.getPackageName(),
                top == null ? null : top.flattenToString(), info == null ? -1 : info.taskId,
                systemApp, SystemClock.uptimeMillis());
        refresh();
        if (top != null && app == null && !SYSTEM_APPS.containsKey(top.getPackageName()) && runtimeContext != null) {
            Context context = runtimeContext;
            // Sparse OEM task callbacks omit metadata. Resolve once off the UI thread, and
            // discard a stale result if the user has switched Activity in the meantime.
            APP_LOOKUP.execute(() -> {
                try {
                    ApplicationInfo resolved = context.getPackageManager().getApplicationInfo(
                            top.getPackageName(), PackageManager.ApplicationInfoFlags.of(0));
                    boolean system = isSystemApp(resolved);
                    SYSTEM_APPS.put(top.getPackageName(), system);
                    dispatch(() -> {
                        if (foregroundSequence != sequence) return;
                        POLICY.foreground(top.getPackageName(), top.flattenToString(), info.taskId,
                                system, SystemClock.uptimeMillis());
                        refresh();
                    });
                } catch (PackageManager.NameNotFoundException ignored) {
                    // The app may have been removed between the task callback and lookup.
                } catch (Throwable error) {
                    Log.w(TAG, "Gesture handle app classification unavailable", error);
                }
            });
        }
    }

    private static boolean isSystemApp(ApplicationInfo app) {
        return (app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    /** Called by the settings refresh registry on every remote preference snapshot. */
    static void refresh() {
        dispatch(() -> {
            POLICY.configure(ModuleSettings.gestureHandleAppModes, ModuleSettings.gestureHandleModulePreset,
                    ModuleSettings.gestureHandleScopePackages, SystemClock.uptimeMillis());
            boolean nextPresent = hasPolicies();
            Object owner = controller.get();
            if (owner != null) {
                Object injector = injector(owner);
                stockHidden = bool(injector, "mHideGestureLine");
                if (stockHidden && bool(injector, "mIsFsgMode")) {
                    try {
                        if (nextPresent) {
                            Object view = owner.getClass().getMethod("getDefaultNavigationBarView").invoke(owner);
                            if (view == null) owner.getClass().getMethod("addDefaultNavigationBar").invoke(owner);
                        } else if (rulesPresent) {
                            owner.getClass().getMethod("removeNavigationBar", int.class).invoke(owner, 0);
                        }
                    } catch (Throwable error) {
                        Log.w(TAG, "Gesture handle host refresh unavailable", error);
                    }
                }
            }
            rulesPresent = nextPresent;
            scheduleHide();
            invalidateHandles();
        });
    }

    private static void scheduleHide() {
        if (main == null) return;
        main.removeCallbacks(HIDE);
        long remaining = POLICY.remaining(SystemClock.uptimeMillis());
        if (remaining > 0L) main.postDelayed(HIDE, remaining);
    }

    private static boolean hasPolicies() {
        return ModuleSettings.gestureHandleModulePreset || !ModuleSettings.gestureHandleAppModes.isEmpty();
    }

    private static boolean appliesTo(View view) {
        if (view.getDisplay() == null || view.getDisplay().getDisplayId() != 0) return false;
        KeyguardManager keyguard = view.getContext().getSystemService(KeyguardManager.class);
        return keyguard == null || !keyguard.isKeyguardLocked();
    }

    private static void invalidateHandles() {
        ArrayList<View> views;
        synchronized (HANDLES) { views = new ArrayList<>(HANDLES.keySet()); }
        for (View view : views) if (view != null && view.isAttachedToWindow()) view.invalidate();
    }

    private static void dispatch(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else new Handler(Looper.getMainLooper()).post(action);
    }

    private static Object injector(Object owner) {
        return ReflectiveAccess.fieldValue(owner, "mNavigationModeControllerInjector");
    }

    private static boolean bool(Object owner, String field) {
        return Boolean.TRUE.equals(ReflectiveAccess.fieldValue(owner, field));
    }

    private static void setHiddenFlag(Object owner, boolean value) throws Exception {
        owner.getClass().getField("mHideGestureLine").setBoolean(owner, value);
    }
}
