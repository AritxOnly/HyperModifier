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
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.view.Choreographer;
import android.view.ViewParent;
import android.view.ViewConfiguration;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Hook points for the local HyperOS 2 and OS3 SystemUI references. */
final class GestureHandleHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String HANDLE = "com.android.systemui.navigationbar.gestural.NavigationHandle";
    private static final String TOP_OBSERVER = "com.miui.systemui.functions.MiuiTopActivityObserver";
    private static final String CONTROLLER = "com.android.systemui.navigationbar.NavigationBarControllerImpl";
    private static final Set<Method> HOOKED = ConcurrentHashMap.newKeySet();
    private static final Map<View, HandleVisual> HANDLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final GestureHandlePolicy POLICY = new GestureHandlePolicy();
    private static final GestureHandleMotion MOTION = new GestureHandleMotion();
    private static final AtomicBoolean SETTINGS_OBSERVER_INSTALLED = new AtomicBoolean();
    private static WeakReference<Object> controller = new WeakReference<>(null);
    private static Handler main;
    private static ClassLoader loader;
    private static boolean receiverInstalled;
    private static boolean inputMonitorInstalled;
    private static Object inputMonitor;
    private static Object inputReceiver;
    private static boolean rulesPresent;
    private static boolean stockHidden;
    private static Context runtimeContext;
    private static long foregroundSequence;
    private static View activeTouchHandle;
    private static boolean activeSwipeRevealed;
    private static final Runnable DETACHED_GESTURE_TIMEOUT = () -> finishActiveGesture(SystemClock.uptimeMillis());
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
        if (SETTINGS_OBSERVER_INSTALLED.compareAndSet(false, true)) {
            ModuleSettings.onChanged(GestureHandleHooks::refresh);
        }
        try {
            Class<?> handle = Class.forName(HANDLE, false, classLoader);
            hook(module, handle.getDeclaredMethod("onDraw", Canvas.class), GestureHandleHooks::draw);
            // OS3 inherits these View methods instead of declaring them. Its first draw is
            // also a valid host-registration point, so lifecycle hooks are optional.
            try {
                hook(module, handle.getDeclaredMethod("onAttachedToWindow"), chain -> {
                    Object result = chain.proceed();
                    if (ModuleSettings.gestureHandleEnabled) trackHandle((View) chain.getThisObject());
                    return result;
                });
                hook(module, handle.getDeclaredMethod("onDetachedFromWindow"), chain -> {
                    View view = (View) chain.getThisObject();
                    HANDLES.remove(view);
                    if (activeTouchHandle == view && main != null) {
                        main.removeCallbacks(DETACHED_GESTURE_TIMEOUT);
                        main.postDelayed(DETACHED_GESTURE_TIMEOUT, 750L);
                    }
                    return chain.proceed();
                });
            } catch (NoSuchMethodException ignored) {
                // OS3 has no NavigationHandle lifecycle overrides.
            }
            HookDiagnostics.available("com.android.systemui", "手势提示线绘制");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle draw hook unavailable", error);
            HookDiagnostics.failure(
                    "com.android.systemui", "手势提示线绘制", error);
        }
        try {
            Class<?> observer = Class.forName(TOP_OBSERVER, false, classLoader);
            for (Method method : observer.getDeclaredMethods()) {
                if (!"updateTopActivity".equals(method.getName())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                int taskArg = parameters.length - 1;
                if (taskArg < 0 || parameters[taskArg] != ActivityManager.RunningTaskInfo.class) continue;
                hook(module, method, chain -> {
                    Object result = chain.proceed();
                    ActivityManager.RunningTaskInfo info =
                            (ActivityManager.RunningTaskInfo) chain.getArg(taskArg);
                    // Both revisions resolve the foreground task on their background handler.
                    dispatch(() -> foreground(info));
                    return result;
                });
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle foreground hook unavailable", error);
            HookDiagnostics.failure(
                    "com.android.systemui", "手势提示线前台识别", error);
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
            Class<?> frame = Class.forName("com.android.systemui.navigationbar.views.NavigationBarFrame",
                    false, classLoader);
            hook(module, frame.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class), chain -> {
                try {
                    observeTouch((View) chain.getThisObject(), (MotionEvent) chain.getArg(0));
                } catch (Throwable error) {
                    Log.w(TAG, "Gesture handle touch observation unavailable", error);
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle touch observer unavailable", error);
        }
        try {
            installHostHooks(module, classLoader);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Gesture handle host override unavailable", error);
        }
    }

    private static Object draw(XposedInterface.Chain chain) throws Throwable {
        if (!ModuleSettings.gestureHandleEnabled) return chain.proceed();
        View view = (View) chain.getThisObject();
        if (view.getDisplay() == null || view.getDisplay().getDisplayId() != 0) return chain.proceed();
        trackHandle(view);
        long now = SystemClock.uptimeMillis();
        boolean systemHidden = rulesPresent && stockHidden;
        boolean hidden = appliesTo(view)
                ? POLICY.hidden(now, systemHidden) : systemHidden;
        HandleVisual visual;
        synchronized (HANDLES) {
            visual = HANDLES.get(view);
            if (visual == null) {
                // A newly attached handle starts at its effective state without a hidden flash.
                visual = new HandleVisual(hidden);
                HANDLES.put(view, visual);
            }
        }
        float alpha = visual.fade.alpha(hidden, now,
                ModuleSettings.gestureHandleSwipeMotion && POLICY.swipeRevealActive(now));
        if (visual.fade.running(now) || MOTION.running(now)) view.postInvalidateOnAnimation();
        if (alpha <= 0f) return null;
        // Apply opacity only to the stock pill drawing. Leave View alpha and the native
        // navigation / quick-switch animations, touch handling and insets untouched.
        Canvas canvas = (Canvas) chain.getArg(0);
        int save = alpha >= 1f ? canvas.save() : canvas.saveLayerAlpha(
                0f, 0f, view.getWidth(), view.getHeight(), Math.round(alpha * 255f));
        try {
            if (ModuleSettings.gestureHandleSwipeMotion && appliesTo(view)) {
                canvas.translate(MOTION.x(now), MOTION.y(now));
            }
            return chain.proceed();
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private static void trackHandle(View view) {
        synchronized (HANDLES) {
            if (HANDLES.containsKey(view)) return;
            HANDLES.put(view, null);
        }
        MOTION.rebaseForNewHost(SystemClock.uptimeMillis());
        initialize(view.getContext());
        ModuleSettings.ensureLoaded();
        refresh();
    }

    private static void observeTouch(View frame, MotionEvent event) {
        if (!ModuleSettings.gestureHandleEnabled
                || (!ModuleSettings.gestureHandleTouchReveal && !ModuleSettings.gestureHandleSwipeMotion)
                || (frame != null && !appliesTo(frame))) return;
        int action = event.getActionMasked();
        long now = SystemClock.uptimeMillis();
        if (main != null) main.removeCallbacks(DETACHED_GESTURE_TIMEOUT);
        if (action == MotionEvent.ACTION_DOWN) {
            if (activeTouchHandle != null) finishActiveGesture(now);
            View handle = handleAt(frame, event.getRawX(), event.getRawY());
            activeTouchHandle = handle;
            activeSwipeRevealed = false;
            if (handle == null || !appliesTo(handle)) return;
            if (ModuleSettings.gestureHandleTouchReveal) {
                POLICY.touchDown(now);
                scheduleHide();
            }
            if (ModuleSettings.gestureHandleSwipeMotion) {
                MOTION.start(event.getRawX(), event.getRawY(), now);
            }
            invalidateHandles();
        } else {
            View handle = activeTouchHandle;
            if (handle == null) return;
            boolean terminal = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_POINTER_DOWN;
            if (ModuleSettings.gestureHandleTouchReveal) {
                if (terminal) {
                    POLICY.touchUp(now);
                    scheduleHide();
                } else {
                    POLICY.touchEvent(now);
                }
            }
            if (action == MotionEvent.ACTION_MOVE && ModuleSettings.gestureHandleSwipeMotion) {
                revealForSwipe(handle, MOTION, event, now);
                if (activeSwipeRevealed) POLICY.swipeEvent(now);
                updateMotion(handle, MOTION, event);
                invalidateHandles();
            } else if (terminal) {
                if (ModuleSettings.gestureHandleSwipeMotion && action != MotionEvent.ACTION_POINTER_DOWN) {
                    revealForSwipe(handle, MOTION, event, now);
                    // A competing system gesture can cancel our passive monitor before its last
                    // batched MOVE. Keep the final coordinates for a visible return animation.
                    updateMotion(handle, MOTION, event);
                }
                finishActiveGesture(now);
            }
        }
    }

    private static void finishActiveGesture(long now) {
        if (activeTouchHandle == null) return;
        if (main != null) main.removeCallbacks(DETACHED_GESTURE_TIMEOUT);
        if (ModuleSettings.gestureHandleTouchReveal) POLICY.touchUp(now);
        if (activeSwipeRevealed) POLICY.swipeUp(now);
        MOTION.release(now);
        activeTouchHandle = null;
        activeSwipeRevealed = false;
        scheduleHide();
        invalidateHandles();
    }

    private static void revealForSwipe(View handle, GestureHandleMotion motion,
            MotionEvent event, long now) {
        if (activeSwipeRevealed) return;
        float slop = ViewConfiguration.get(handle.getContext()).getScaledTouchSlop();
        if (!motion.movedBeyond(event.getRawX(), event.getRawY(), slop)) return;
        activeSwipeRevealed = true;
        POLICY.swipeDown(now);
        scheduleHide();
    }

    private static void updateMotion(View handle, GestureHandleMotion motion, MotionEvent event) {
        RectF pill = pillRect(handle);
        if (pill == null) return;
        float density = handle.getResources().getDisplayMetrics().density;
        float margin = 2f * density;
        float maxHorizontal = 24f * density;
        motion.move(event.getRawX(), event.getRawY(),
                Math.min(maxHorizontal, Math.max(0f, pill.left - margin)),
                Math.min(maxHorizontal, Math.max(0f, handle.getWidth() - pill.right - margin)),
                Math.max(0f, pill.top - margin),
                Math.max(0f, handle.getHeight() - pill.bottom - margin),
                SystemClock.uptimeMillis());
    }

    private static View handleAt(View frame, float rawX, float rawY) {
        ArrayList<View> views;
        synchronized (HANDLES) { views = new ArrayList<>(HANDLES.keySet()); }
        for (View view : views) {
            if (view == null || !view.isAttachedToWindow() || view.getVisibility() != View.VISIBLE
                    || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
            ViewParent parent = view.getParent();
            while (parent != null && parent != frame) parent = parent.getParent();
            if (frame != null && parent != frame) continue;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            float density = view.getResources().getDisplayMetrics().density;
            RectF pill = pillRect(view);
            boolean onPill = pill != null && rawX >= location[0] + pill.left - 12f * density
                    && rawX <= location[0] + pill.right + 12f * density
                    && rawY >= location[1] + pill.top - 12f * density
                    && rawY <= location[1] + pill.bottom + 12f * density;
            Point display = new Point();
            view.getDisplay().getRealSize(display);
            boolean inBottomGestureArea = GestureHandleTouchArea.contains(rawX, rawY,
                    display.x, display.y, location[1] + view.getHeight(), density,
                    ModuleSettings.gestureHandleTouchAreaDp);
            if (onPill || inBottomGestureArea) return view;
        }
        return null;
    }

    private static RectF pillRect(View handle) {
        try {
            return (RectF) handle.getClass().getMethod("getPillRect").invoke(handle);
        } catch (ReflectiveOperationException ignored) {
            // OS3 draws across the full handle width and no longer exposes getPillRect().
            Object radiusValue = ReflectiveAccess.fieldValue(handle, "mRadius");
            Object bottomValue = ReflectiveAccess.fieldValue(handle, "mBottom");
            if (!(radiusValue instanceof Number) || !(bottomValue instanceof Number)) return null;
            float radius = ((Number) radiusValue).floatValue();
            float bottom = ((Number) bottomValue).floatValue();
            float bottomEdge = handle.getHeight() - bottom;
            return new RectF(0f, bottomEdge - 2f * radius, handle.getWidth(), bottomEdge);
        }
    }

    private static final class HandleVisual {
        final GestureHandleFade fade;

        HandleVisual(boolean hidden) { fade = new GestureHandleFade(hidden); }
    }

    private static void installHostHooks(XposedModule module, ClassLoader classLoader) throws Exception {
        Class<?> type = Class.forName(CONTROLLER, false, classLoader);
        for (Method method : type.getDeclaredMethods()) {
            if ("createNavigationBar".equals(method.getName())) {
                hook(module, method, chain -> {
                    Object owner = chain.getThisObject();
                    controller = new WeakReference<>(owner);
                    Context context = (Context) ReflectiveAccess.fieldValue(owner, "mContext");
                    if (context != null && ModuleSettings.gestureHandleEnabled) initialize(context);
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
        installInputMonitor();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!ModuleSettings.gestureHandleEnabled) return;
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    POLICY.reveal(SystemClock.uptimeMillis());
                    scheduleHide();
                } else {
                    POLICY.clearTouchReveal();
                    POLICY.clearSwipeReveal();
                    clearMotion();
                    activeTouchHandle = null;
                    main.removeCallbacks(DETACHED_GESTURE_TIMEOUT);
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

    private static void installInputMonitor() {
        if (!ModuleSettings.gestureHandleEnabled || hasInputMonitor() || loader == null) return;
        Object monitor = null;
        try {
            // The navigation-bar window does not receive every gestural input stream. Use the
            // same passive monitor wrapper as SystemUI, without pilfering or consuming events.
            Class<?> monitorType = Class.forName(
                    "com.android.systemui.shared.system.InputMonitorCompat", false, loader);
            Class<?> listenerType = Class.forName(
                    "com.android.systemui.shared.system.InputChannelCompat$InputEventListener", false, loader);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[] { listenerType },
                    (proxy, method, args) -> {
                        if ("onInputEvent".equals(method.getName()) && args != null
                                && args.length == 1 && args[0] instanceof MotionEvent) {
                            try {
                                observeTouch(null, (MotionEvent) args[0]);
                            } catch (Throwable error) {
                                Log.w(TAG, "Gesture handle monitored input unavailable", error);
                            }
                        }
                        return null;
                    });
            monitor = monitorType.getConstructor(String.class, int.class)
                    .newInstance("mhm-gesture-handle", 0);
            Object receiver = monitorType.getMethod("getInputReceiver", Looper.class,
                    Choreographer.class, listenerType).invoke(monitor,
                    Looper.getMainLooper(), Choreographer.getInstance(), listener);
            inputMonitor = monitor;
            inputReceiver = receiver;
            inputMonitorInstalled = true;
            Log.i(TAG, "Gesture handle input monitor installed");
        } catch (Throwable error) {
            if (monitor != null) {
                try { monitor.getClass().getMethod("dispose").invoke(monitor); }
                catch (Throwable ignored) { }
            }
            Log.w(TAG, "Gesture handle input monitor unavailable; using view events", error);
        }
    }

    private static boolean hasInputMonitor() {
        return inputMonitorInstalled && inputMonitor != null && inputReceiver != null;
    }

    private static void stopInputMonitor() {
        Object receiver = inputReceiver;
        Object monitor = inputMonitor;
        inputReceiver = null;
        inputMonitor = null;
        inputMonitorInstalled = false;
        if (receiver != null) {
            try { receiver.getClass().getMethod("dispose").invoke(receiver); }
            catch (Throwable error) { Log.w(TAG, "Gesture handle input receiver disposal unavailable", error); }
        }
        if (monitor != null) {
            try { monitor.getClass().getMethod("dispose").invoke(monitor); }
            catch (Throwable error) { Log.w(TAG, "Gesture handle input monitor disposal unavailable", error); }
        }
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
            POLICY.configure(ModuleSettings.gestureHandleAppModes, ModuleSettings.gestureHandlePreset,
                    ModuleSettings.gestureHandleScopePackages, SystemClock.uptimeMillis());
            if (!ModuleSettings.gestureHandleEnabled) {
                POLICY.clearTouchReveal();
                POLICY.clearSwipeReveal();
                activeTouchHandle = null;
                activeSwipeRevealed = false;
                MOTION.reset();
                stopInputMonitor();
            } else {
                if (receiverInstalled) installInputMonitor();
            }
            if (!ModuleSettings.gestureHandleTouchReveal) POLICY.clearTouchReveal();
            if (!ModuleSettings.gestureHandleSwipeMotion) {
                POLICY.clearSwipeReveal();
                clearMotion();
            }
            boolean nextPresent = hasPolicies();
            Object owner = controller.get();
            if (owner != null) {
                if (nextPresent && !receiverInstalled) {
                    Context context = (Context) ReflectiveAccess.fieldValue(owner, "mContext");
                    if (context != null) initialize(context);
                }
                Object injector = injector(owner);
                stockHidden = bool(injector, "mHideGestureLine");
                if (stockHidden && bool(injector, "mIsFsgMode")) {
                    try {
                        Object view = defaultNavigationBar(owner);
                        if (nextPresent) {
                            if (view == null) owner.getClass().getMethod("addDefaultNavigationBar").invoke(owner);
                        } else if (view != null) {
                            // The stock controller has no bar in this state. Remove the host
                            // previously kept alive for our rules when the master switch is off.
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

    private static Object defaultNavigationBar(Object owner) throws ReflectiveOperationException {
        try {
            return owner.getClass().getMethod("getDefaultNavigationBarView").invoke(owner);
        } catch (NoSuchMethodException ignored) {
            return owner.getClass().getMethod("getDefaultNavigationBar").invoke(owner);
        }
    }

    private static void scheduleHide() {
        if (main == null) return;
        main.removeCallbacks(HIDE);
        if (!ModuleSettings.gestureHandleEnabled) return;
        long remaining = POLICY.remaining(SystemClock.uptimeMillis());
        if (remaining > 0L) main.postDelayed(HIDE, remaining);
    }

    private static boolean hasPolicies() {
        return ModuleSettings.gestureHandleEnabled && ((GestureHandlePresets.valid(ModuleSettings.gestureHandlePreset)
                && !GestureHandlePresets.STOCK.equals(ModuleSettings.gestureHandlePreset))
                || !ModuleSettings.gestureHandleAppModes.isEmpty()
                || ModuleSettings.gestureHandleTouchReveal || ModuleSettings.gestureHandleSwipeMotion);
    }

    private static void clearMotion() {
        if (!ModuleSettings.gestureHandleTouchReveal) activeTouchHandle = null;
        activeSwipeRevealed = false;
        MOTION.reset();
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
