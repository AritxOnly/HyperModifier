package com.aritxonly.myhypermodifier;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** The phone monitors count space *before* the island. A docked island needs icons after it. */
final class FoldIslandIconHooks {
    private static final String PREFIX = "com.android.systemui.statusbar.";
    private static boolean installed;
    private final int[] location = new int[2]; // monitor callbacks run on the UI thread
    private final Rect clearance = new Rect();
    private final WeakHashMap<Object, Inset> reserved = new WeakHashMap<>();
    private final Class<?> bar;
    private final Field container, batteryGroup, statusIcons;
    private final Class<?> batteryType;
    private final Method getContainer;
    private final boolean notification;
    private final Method update;

    private static final class Inset implements View.OnAttachStateChangeListener {
        final WeakReference<View> target;
        final boolean left, relative, start;
        int stock, written;

        Inset(View target, boolean left) {
            this.target = new WeakReference<>(target);
            this.left = left;
            relative = target.isPaddingRelative();
            start = left != (target.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
            stock = written = padding(target);
            target.addOnAttachStateChangeListener(this);
        }

        int padding(View v) {
            if (relative) return start ? v.getPaddingStart() : v.getPaddingEnd();
            return left ? v.getPaddingLeft() : v.getPaddingRight();
        }

        void set(View v, int value) {
            if (relative) {
                v.setPaddingRelative(start ? value : v.getPaddingStart(), v.getPaddingTop(),
                        start ? v.getPaddingEnd() : value, v.getPaddingBottom());
            } else if (left) v.setPadding(value, v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());
            else v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), value, v.getPaddingBottom());
        }

        @Override public void onViewAttachedToWindow(View view) {}

        @Override public void onViewDetachedFromWindow(View view) { restore(); }

        void restore() {
            View v = target.get();
            if (v == null) return;
            target.clear();
            v.removeOnAttachStateChangeListener(this);
            if (padding(v) == written && stock != written) set(v, stock);
        }
    }

    private FoldIslandIconHooks(ClassLoader loader, boolean notification) throws ReflectiveOperationException {
        this.notification = notification;
        Class<?> monitor = Class.forName(PREFIX + "IslandMonitor$" + (notification
                ? "NotificationContainerIslandMonitor" : "RealContainerIslandMonitor"), false, loader);
        bar = Class.forName(PREFIX + "phone.MiuiPhoneStatusBarView", false, loader);
        container = notification ? monitor.getDeclaredField("container") : null;
        getContainer = notification ? null : monitor.getMethod("getContainer");
        batteryGroup = bar.getDeclaredField("mStatusBatteryContainer");
        batteryType = batteryGroup.getType();
        statusIcons = bar.getDeclaredField("mStatusBarStatusIcons");
        update = monitor.getDeclaredMethod("updateContainerSize", Rect.class, boolean.class, boolean.class);
    }

    static synchronized void install(XposedModule module, ClassLoader loader) throws Throwable {
        if (installed) return;
        FoldIslandIconHooks notifications = new FoldIslandIconHooks(loader, true);
        FoldIslandIconHooks status = new FoldIslandIconHooks(loader, false);
        XposedInterface.HookHandle first = notifications.bind(module);
        try {
            status.bind(module);
        } catch (Throwable e) {
            first.unhook();
            throw e;
        }
        installed = true;
        // The plugin republishes its stock bounds on a settings change. Also release our
        // padding when a monitor detached before it received the final empty rectangle.
        Handler main = new Handler(Looper.getMainLooper());
        Runnable restore = () -> {
            if (FoldIslandLayoutHooks.mode() == 0) {
                notifications.clear();
                status.clear();
            }
        };
        ModuleSettings.onChanged(() -> {
            if (FoldIslandLayoutHooks.mode() == 0 && (notifications.hasInsets || status.hasInsets)) {
                main.removeCallbacks(restore);
                main.post(restore);
            }
        });
    }

    private volatile boolean hasInsets;

    private XposedInterface.HookHandle bind(XposedModule module) {
        return module.hook(update).setId("fold-island-icons-" + (notification ? "start" : "end"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    int mode = FoldIslandLayoutHooks.mode();
                    if (mode == 0 && !hasInsets) return chain.proceed();
                    Object monitor = chain.getThisObject();
                    Rect rect = (Rect) chain.getArg(0);
                    if (mode != 0 && !rect.isEmpty() && reserve(monitor, rect, mode)) {
                        // The reserved padding already accounts for the full occupied region.
                        return chain.proceed(new Object[]{clearance, chain.getArg(1), chain.getArg(2)});
                    }
                    release(monitor);
                    return chain.proceed();
                });
    }

    private boolean reserve(Object monitor, Rect rect, int mode) throws ReflectiveOperationException {
        View child = (View) (notification ? container.get(monitor) : getContainer.invoke(monitor));
        if (child == null || !child.isAttachedToWindow() || !FoldIslandLayoutHooks.inner(child)) return false;
        View parent = child, headerGroup = null;
        while (!bar.isInstance(parent)) {
            if (batteryType.isInstance(parent)) headerGroup = parent;
            ViewParent next = parent.getParent();
            if (!(next instanceof View)) break;
            parent = (View) next;
        }
        boolean home = bar.isInstance(parent);
        int screen = child.getRootView().getWidth();
        if (screen <= 0 || rect.height() > Math.round(56 * child.getResources().getDisplayMetrics().density)) return false;
        boolean left = rect.centerX() < screen / 2;
        boolean rtl = child.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (notification != (left != rtl)) return false;
        if (!notification && mode != FoldIslandPlacement.CAMERA) return false;
        View target;
        if (notification) {
            // The container can override mActualPaddingStart. Reserve on its FrameLayout
            // host, which owns its available width in both fullscreen and drip layouts.
            if (!(child.getParent() instanceof android.widget.FrameLayout wrapper)) return false;
            target = wrapper;
        } else if (home && statusIcons.get(parent) == child) {
            target = (View) batteryGroup.get(parent);
        } else {
            target = headerGroup;
        }
        if (target == null || target.getWidth() <= 0) return false;
        target.getLocationOnScreen(location);
        if (rect.bottom <= location[1] || rect.top >= location[1] + target.getHeight()) return false;
        // Keep the monitor marked showing so it recomputes on subsequent header layouts.
        // Its normal BEFORE-island width limit is redundant once the host reserves space.
        int freeEdge = left ? 2 * screen : -screen;
        clearance.set(freeEdge, rect.top, freeEdge + 1, rect.bottom);
        Inset inset = reserved.get(monitor);
        if (inset == null || inset.target.get() != target || inset.left != left
                || inset.relative != target.isPaddingRelative() || inset.start != (left != rtl)) {
            if (inset != null) inset.restore();
            inset = new Inset(target, left);
            reserved.put(monitor, inset);
            hasInsets = true;
        }
        int current = inset.padding(target);
        if (current != inset.written) inset.stock = current;
        int gap = Math.round(4 * target.getResources().getDisplayMetrics().density);
        int required = left ? rect.right + gap - location[0]
                : location[0] + target.getWidth() - rect.left + gap;
        inset.written = Math.max(inset.stock, Math.min(target.getWidth(), required));
        if (current != inset.written) inset.set(target, inset.written);
        return true;
    }

    private void release(Object monitor) {
        Inset inset = reserved.remove(monitor);
        if (inset != null) inset.restore();
        hasInsets = !reserved.isEmpty();
    }

    private void clear() {
        for (Inset inset : reserved.values()) inset.restore();
        reserved.clear();
        hasInsets = false;
    }
}
