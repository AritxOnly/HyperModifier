package com.aritxonly.myhypermodifier;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.WindowInsets;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Session-scoped Activity bounds overrides. Does not change requested overrides or display size. */
final class CoverAdaptationHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static Controller controller;

    static void install(XposedModule module, ClassLoader loader, SharedPreferences prefs) throws Throwable {
        // Opt-in at boot: disabled installations add no WM/visibility hooks or callbacks.
        if (!prefs.getBoolean(CoverAdaptationContract.KEY, false)
                || !prefs.getBoolean("module_hooks_enabled", true)
                || prefs.getBoolean("systemui_compatibility_mode", false)
                || !INSTALLED.compareAndSet(false, true)) return;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            CoverWindowAccess access = new CoverWindowAccess(loader);
            Controller c = new Controller(module, access, prefs, loader);
            handles.add(module.hook(access.systemReady).setId("cover-window-ready")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        c.start(chain.getThisObject());
                        return result;
                    }));
            handles.add(module.hook(access.resolve).setId("cover-window-configuration")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (c.targetTask == null) return result;
                        c.resolve(chain.getThisObject(), (Configuration) chain.getArg(0));
                        return result;
                    }));
            handles.add(module.hook(access.focusChanged).setId("cover-window-focus")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (c.enabled) c.focus(chain.getThisObject(), chain.getArg(1));
                        return result;
                    }));
            handles.add(module.hook(access.visibleTypes).setId("cover-status-visible-types")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        int original = (Integer) chain.proceed();
                        return c.hidesBar(chain.getThisObject()) ? original & ~WindowInsets.Type.statusBars() : original;
                    }));
            handles.add(module.hook(access.isVisible).setId("cover-status-visible-query")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        if (!c.hidesBar(chain.getThisObject())) return chain.proceed();
                        int requested = (Integer) chain.getArg(0);
                        int types = requested & ~WindowInsets.Type.statusBars();
                        if (types == requested) return chain.proceed();
                        return types != 0 && (Boolean) chain.proceed(new Object[]{types});
                    }));
            // These small getters are used by bar policy; preserve their hook dispatch.
            module.deoptimize(access.updateBars);
            controller = c;
        } catch (Throwable error) {
            for (var handle : handles) handle.unhook();
            INSTALLED.set(false);
            throw error;
        }
    }

    static final class Controller {
        final XposedModule module;
        final CoverWindowAccess a;
        final SharedPreferences prefs;
        final Handler main = new Handler(Looper.getMainLooper());
        final ClassLoader loader;
        final WeakHashMap<Object, Boolean> modified = new WeakHashMap<>();
        final AtomicBoolean updateQueued = new AtomicBoolean(), publishQueued = new AtomicBoolean();
        final Rect targetBounds = new Rect();
        volatile WeakReference<Object> targetTask;
        volatile WeakReference<Object> barRecord;
        WeakReference<Object> focused = new WeakReference<>(null), lastApp = new WeakReference<>(null);
        WeakReference<Object> policy = new WeakReference<>(null), targetRecord = new WeakReference<>(null);
        Context context;
        Object wm, lock, foldManager, foldListener;
        IBinder lease;
        IBinder.DeathRecipient leaseDeath;
        SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
        volatile boolean enabled = true;
        boolean folded, foldKnown, left, started, visible, broken;
        int width, height;
        String message = "";
        String lastPublished;

        Controller(XposedModule module, CoverWindowAccess a, SharedPreferences prefs, ClassLoader loader) {
            this.module = module; this.a = a; this.prefs = prefs; this.loader = loader;
        }

        void start(Object service) {
            if (started) return;
            started = true;
            try {
                wm = service; lock = a.globalLock.get(service); context = (Context) a.context.get(service);
                // Use the ROM's own folded-state mapping, including device-state properties on
                // new Android versions; a near-square inner screen is never guessed from pixels.
                Class<?> manager = Class.forName("android.hardware.devicestate.DeviceStateManager", false, loader);
                Class<?> callback = Class.forName(manager.getName() + "$DeviceStateCallback", false, loader);
                Class<?> listener = Class.forName(manager.getName() + "$FoldStateListener", false, loader);
                foldManager = context.getSystemService(manager);
                if (foldManager == null) throw new IllegalStateException("No fold state service");
                foldListener = listener.getConstructor(Context.class, Consumer.class)
                        .newInstance(context, (Consumer<Boolean>) this::onFoldChanged);
                manager.getMethod("registerCallback", Executor.class, callback)
                        .invoke(foldManager, (Executor) main::post, foldListener);
                IntentFilter commands = new IntentFilter(CoverAdaptationContract.COMMAND);
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) {
                        String value = intent.getStringExtra("command");
                        if (CoverAdaptationContract.TOGGLE.equals(value) || CoverAdaptationContract.EXIT.equals(value)
                                || CoverAdaptationContract.QUERY.equals(value)) command(intent);
                    }
                }, commands, CoverAdaptationContract.PERMISSION, main, Context.RECEIVER_EXPORTED);
                // Xiaomi's SystemUI uses android.uid.systemui, not the module's signature or
                // necessarily UID 1000. Its separate channel uses an existing platform permission.
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) { command(intent); }
                }, new IntentFilter(CoverAdaptationContract.SYSTEM_COMMAND),
                        "android.permission.MANAGE_ACTIVITY_TASKS", main, Context.RECEIVER_EXPORTED);
                IntentFilter lifecycle = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                lifecycle.addAction("android.intent.action.USER_SWITCHED");
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent intent) { stop(""); }
                }, lifecycle, null, main, Context.RECEIVER_NOT_EXPORTED);
                preferenceListener = (p, key) -> {
                    if (key != null && !key.equals(CoverAdaptationContract.KEY)
                            && !key.equals("module_hooks_enabled") && !key.equals("systemui_compatibility_mode")) return;
                    boolean next = !broken && p.getBoolean(CoverAdaptationContract.KEY, false)
                            && p.getBoolean("module_hooks_enabled", true)
                            && !p.getBoolean("systemui_compatibility_mode", false);
                    if (next == enabled) return;
                    enabled = next;
                    main.post(() -> { if (!enabled) stop(""); else queueUpdate(); });
                };
                prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
                preferenceListener.onSharedPreferenceChanged(prefs, null);
                module.log(Log.INFO, "MyHyperModifier", "Cover adaptation framework ready (experimental)");
            } catch (Throwable error) { fail(error); }
        }

        boolean hidesBar(Object window) throws IllegalAccessException {
            WeakReference<Object> ref = barRecord;
            Object record = ref == null ? null : ref.get();
            return record != null && a.windowActivity.get(window) == record;
        }

        void onFoldChanged(boolean value) {
            try {
                synchronized (lock) {
                    folded = value; foldKnown = true;
                    if (!value) clearLocked();
                }
                queueUpdate();
            } catch (Throwable error) { fail(error); }
        }

        void resolve(Object record, Configuration parent) {
            try {
                WeakReference<Object> task = targetTask;
                if (task == null || a.getTask.invoke(record) != task.get()) return;
                // Configuration callbacks run under the native WM global lock.
                Rect parentBounds = a.bounds(parent);
                if (!enabled || !folded || !a.eligible(record)
                        || parentBounds.width() != width || parentBounds.height() != height
                        || !parentBounds.contains(targetBounds)) {
                    main.post(() -> stop("当前窗口不支持外屏适配"));
                    return;
                }
                Configuration resolved = (Configuration) a.getResolved.invoke(record);
                Configuration backup = new Configuration(resolved);
                try {
                    a.apply(record, parent, targetBounds);
                    modified.put(record, Boolean.TRUE);
                } catch (Throwable error) {
                    resolved.setTo(backup);
                    throw error;
                }
            } catch (Throwable error) { fail(error); }
        }

        void focus(Object owner, Object window) {
            if (lock == null) return;
            try {
                if ((Integer) a.displayId.invoke(a.policyDisplay.get(owner)) != 0) return;
                Object record = window == null ? null : a.windowActivity.get(window);
                policy = new WeakReference<>(owner);
                focused = new WeakReference<>(record);
                if (record != null) lastApp = new WeakReference<>(record);
                // Release bar override immediately for shade/lockscreen/permission windows.
                if (barRecord != null && record != barRecord.get()) barRecord = null;
                if (targetTask != null) queueUpdate();
            } catch (Throwable error) { fail(error); }
        }

        void command(Intent intent) {
            String cmd = intent.getStringExtra("command");
            if (CoverAdaptationContract.ATTACH.equals(cmd)) {
                Bundle extras = intent.getExtras();
                IBinder next = extras == null ? null : extras.getBinder("lease");
                if (next == null || !next.isBinderAlive()) return;
                stop("");
                if (lease != null && leaseDeath != null) lease.unlinkToDeath(leaseDeath, 0);
                lease = next;
                IBinder owned = next;
                leaseDeath = () -> main.post(() -> {
                    if (lease == owned) { lease = null; stop(""); }
                });
                try { next.linkToDeath(leaseDeath, 0); }
                catch (android.os.RemoteException error) { lease = null; }
                publish();
                return;
            }
            if (CoverAdaptationContract.QUERY.equals(cmd)) { publish(); return; }
            if (CoverAdaptationContract.EXIT.equals(cmd)) { stop(""); return; }
            try {
                if (!enabled || lease == null || !lease.isBinderAlive()) {
                    stop("请启用外屏适配实验功能，并重启手机及系统界面"); return;
                }
                KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
                if (keyguard != null && keyguard.isKeyguardLocked()) {
                    stop("请解锁后使用外屏适配"); return;
                }
                if (CoverAdaptationContract.SWITCH.equals(cmd)) {
                    if (targetTask == null || !folded) return;
                    int id = intent.getIntExtra("task", -1);
                    if (id < 0) return;
                    // Only an existing, same-user task selected from SystemUI's recents list.
                    Class<?> atm = Class.forName("android.app.ActivityTaskManager", false, loader);
                    Object service = atm.getMethod("getService").invoke(null);
                    CoverWindowAccess.method(service.getClass(), "startActivityFromRecents", int.class, Bundle.class)
                            .invoke(service, id, null);
                    return;
                }
                synchronized (lock) {
                    if (CoverAdaptationContract.SWAP.equals(cmd)) {
                        if (targetTask != null) { left = !left; applyLocked(targetRecord.get()); }
                    } else if (CoverAdaptationContract.TOGGLE.equals(cmd)) {
                        if (targetTask != null) clearLocked();
                        else if (!foldKnown || !folded) message = "仅支持折叠后的外屏";
                        else {
                            Object record = lastApp.get();
                            if (!a.eligible(record)) message = "请先打开可适配的普通全屏应用";
                            else applyLocked(record);
                        }
                    }
                }
                queueUpdate();
            } catch (Throwable error) { fail(error); }
        }

        void queueUpdate() {
            if (updateQueued.compareAndSet(false, true)) main.post(() -> {
                updateQueued.set(false);
                if (lock == null) return;
                // A failing native refresh must not create a stop -> refresh -> failure loop on
                // system_server's main queue. Recovery recomputes configurations in stop().
                if (broken) { publishIfChanged(); return; }
                try {
                    synchronized (lock) {
                        if (targetTask != null) {
                            Object record = focused.get();
                            if (!enabled || !folded) clearLocked();
                            else if (record == null) { visible = false; barRecord = null; }
                            else if (!a.eligible(record)) clearLocked();
                            else {
                                if (a.getTask.invoke(record) != targetTask.get()
                                        || record != targetRecord.get()) applyLocked(record);
                                visible = targetTask != null;
                                barRecord = visible ? new WeakReference<>(record) : null;
                            }
                        }
                        Object owner = policy.get();
                        if (owner != null) a.updateBars.invoke(owner);
                        a.requestTraversal.invoke(wm);
                    }
                    publishIfChanged();
                } catch (Throwable error) { fail(error); }
            });
        }

        void applyLocked(Object record) throws ReflectiveOperationException {
            if (record == null || !a.eligible(record)) { clearLocked(); return; }
            Object task = a.getTask.invoke(record);
            boolean sameTask = targetTask != null && targetTask.get() == task;
            if (!sameTask) clearLocked();
            Object parent = a.getParent.invoke(record);
            if (parent == null) return;
            Configuration config = (Configuration) a.getConfiguration.invoke(parent);
            Rect full = a.bounds(config);
            if (full.left != 0 || full.top != 0
                    || !CoverAdaptationPolicy.fits(full.width(), full.height(), config.densityDpi)) {
                clearLocked();
                message = "当前外屏尺寸或方向无法容纳 9:16 应用和侧栏"; return;
            }
            width = full.width(); height = full.height();
            int appWidth = CoverAdaptationPolicy.appWidth(height);
            int x = left ? width - appWidth : 0;
            boolean moved = targetBounds.left != x;
            targetBounds.set(x, 0, x + appWidth, height);
            if (!sameTask) targetTask = new WeakReference<>(task);
            targetRecord = new WeakReference<>(record);
            if (!sameTask || moved || !modified.containsKey(record)
                    || !a.bounds((Configuration) a.getResolved.invoke(record)).equals(targetBounds)) {
                a.recompute.invoke(record);
            }
            // A side swap is one configuration update, never a fullscreen-then-narrow replay.
            // Keep underlying Activities in the same task aligned during translucent transitions.
            if (sameTask && moved) {
                for (Object existing : new ArrayList<>(modified.keySet())) {
                    if (existing != record && a.getParent.invoke(existing) != null) a.recompute.invoke(existing);
                }
            }
            // Never advertise an active layout unless the resolved bounds actually match.
            if (!a.bounds((Configuration) a.getResolved.invoke(record)).equals(targetBounds)) {
                clearLocked(); message = "系统未接受应用窗口比例，已恢复原布局"; return;
            }
            visible = focused.get() == record;
            barRecord = visible ? new WeakReference<>(record) : null;
            message = "";
        }

        void clearLocked() throws ReflectiveOperationException {
            targetTask = null; barRecord = null; visible = false;
            ArrayList<Object> records = new ArrayList<>(modified.keySet());
            modified.clear(); targetRecord.clear();
            ReflectiveOperationException failure = null;
            for (Object record : records) {
                try { if (record != null && a.getParent.invoke(record) != null) a.recompute.invoke(record); }
                catch (ReflectiveOperationException error) { failure = error; }
            }
            if (failure != null) throw failure;
        }

        void stop(String reason) {
            try {
                if (lock != null) synchronized (lock) { clearLocked(); message = reason; }
            } catch (Throwable error) { module.log(Log.ERROR, "MyHyperModifier", "Cover restore failed", error); }
            queueUpdate();
        }

        void publish() { lastPublished = null; publishIfChanged(); }

        void publishIfChanged() {
            if (context == null || !publishQueued.compareAndSet(false, true)) return;
            main.post(() -> {
                publishQueued.set(false);
                try {
                    boolean active;
                    int task = -1;
                    String pkg = "";
                    synchronized (lock) {
                        active = targetTask != null;
                        Object record = targetRecord.get();
                        if (active && record != null) {
                            task = a.taskId.getInt(a.getTask.invoke(record));
                            pkg = ((android.content.pm.ActivityInfo) a.activityInfo.get(record)).packageName;
                        }
                    }
                    String key = enabled + ":" + active + ":" + visible + ":" + left + ":" + width + ":" + height
                            + ":" + task + ":" + message;
                    if (key.equals(lastPublished)) return;
                    lastPublished = key;
                    Intent state = new Intent(CoverAdaptationContract.STATE)
                            .putExtra("enabled", enabled).putExtra("active", active).putExtra("visible", visible)
                            .putExtra("left", left).putExtra("width", width).putExtra("height", height)
                            .putExtra("task", task).putExtra("package", pkg).putExtra("message", message);
                    context.sendBroadcast(new Intent(state).setPackage("com.android.systemui"));
                    context.sendBroadcast(state.setPackage(CoverAdaptationContract.PACKAGE));
                } catch (Throwable error) { module.log(Log.ERROR, "MyHyperModifier", "Cover state failed", error); }
            });
        }

        void fail(Throwable error) {
            // No exceptions escape into native WM. Stop new overrides before queuing recovery.
            targetTask = null; barRecord = null;
            enabled = false;
            if (!broken) module.log(Log.ERROR, "MyHyperModifier", "Cover adaptation unavailable", error);
            broken = true;
            main.post(() -> stop("当前系统窗口接口不兼容，外屏适配已退出"));
        }
    }

    private CoverAdaptationHooks() {}
}
