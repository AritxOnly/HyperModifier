package com.aritxonly.myhypermodifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.text.FieldPosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** One shared, non-waking ticker, active only for visible status-bar clocks. Main-thread state. */
final class StatusBarClockSecondsHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean REFRESH_PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<TextView, Binding> CLOCKS = new WeakHashMap<>();
    private static Field mode, controller, is24, demo, amPmStyle;
    private static Method updateTime;
    private static boolean queued, observing, screenOn, lastEnabled;
    private static Context application;
    private static final Runnable TICK = () -> {
        queued = false;
        boolean active = false;
        for (Binding binding : CLOCKS.values()) active |= binding.render();
        if (active) schedule();
    };
    private static final BroadcastReceiver SCREEN = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            screenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            if (screenOn) for (Binding binding : CLOCKS.values()) binding.configure();
            reconcile();
        }
    };

    private StatusBarClockSecondsHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> clock = Class.forName(
                    SYSTEM_UI + ".statusbar.views.MiuiClock", false, loader);
            mode = clock.getField("mClockMode");
            controller = clock.getField("mMiuiStatusBarClockController");
            amPmStyle = clock.getField("mAmPmStyle");
            is24 = controller.getType().getField("mIs24");
            demo = controller.getType().getField("mDemoMode");
            updateTime = clock.getDeclaredMethod("updateTime");
            module.hook(updateTime).setId("status-bar-clock-seconds-update")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Binding binding = CLOCKS.get(chain.getThisObject());
                        if (binding != null && enabled()) {
                            binding.configure();
                            if (binding.render()) schedule();
                        }
                        return result;
                    });
            module.hook(clock.getDeclaredMethod("onAttachedToWindow"))
                    .setId("status-bar-clock-seconds-attach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        TextView view = (TextView) chain.getThisObject();
                        // Resolve IDs at attach only; headers and large lockscreen clocks stay stock.
                        int clockId = view.getResources().getIdentifier("clock", "id", SYSTEM_UI);
                        if (clockId != 0 && view.getId() == clockId && !CLOCKS.containsKey(view)) {
                            Binding binding = new Binding(view);
                            CLOCKS.put(view, binding);
                            view.getViewTreeObserver().addOnGlobalLayoutListener(binding);
                            ModuleSettings.ensureLoaded();
                            reconcile();
                        }
                        return result;
                    });
            module.hook(clock.getDeclaredMethod("onDetachedFromWindow"))
                    .setId("status-bar-clock-seconds-detach")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        TextView view = (TextView) chain.getThisObject();
                        Binding binding = CLOCKS.remove(view);
                        if (binding != null && view.getViewTreeObserver().isAlive()) {
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(binding);
                        }
                        Object result = chain.proceed();
                        reconcile();
                        return result;
                    });
            HookDiagnostics.available(SYSTEM_UI, "状态栏显秒");
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "状态栏显秒", error);
        }
    }

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.statusBarShowSeconds;
    }

    /** Called by the existing settings loader; unrelated changes do not replay any clock. */
    static void refresh() {
        if (!INSTALLED.get() || !REFRESH_PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            REFRESH_PENDING.set(false);
            boolean now = enabled();
            if (now == lastEnabled) return;
            lastEnabled = now;
            for (Binding binding : CLOCKS.values()) {
                TextView view = binding.view.get();
                if (view != null) {
                    if (now) binding.configure();
                    else binding.restore(view);
                }
            }
            reconcile();
        });
    }

    private static void reconcile() {
        MAIN.removeCallbacks(TICK);
        queued = false;
        lastEnabled = enabled();
        if (!lastEnabled || CLOCKS.isEmpty()) {
            stopObservation();
            return;
        }
        if (!observing) {
            TextView view = CLOCKS.keySet().iterator().next();
            Context context = view.getContext().getApplicationContext();
            if (context == null) return;
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                context.registerReceiver(SCREEN, filter, Context.RECEIVER_NOT_EXPORTED);
                application = context;
                observing = true;
                PowerManager power = context.getSystemService(PowerManager.class);
                screenOn = power != null && power.isInteractive();
            } catch (RuntimeException ignored) {
                stopObservation();
                return;
            }
        }
        boolean active = false;
        for (Binding binding : CLOCKS.values()) {
            if (binding.format == null) binding.configure();
            active |= binding.render();
        }
        if (active) schedule();
    }

    private static void stopObservation() {
        if (observing) {
            try { application.unregisterReceiver(SCREEN); }
            catch (RuntimeException ignored) { /* SystemUI may be tearing down. */ }
        }
        observing = false;
        application = null;
        screenOn = false;
    }

    private static void schedule() {
        if (queued) return;
        queued = true;
        MAIN.postAtTime(TICK, SystemClock.uptimeMillis()
                + StatusBarClockSecondsPolicy.delayToNextSecond(System.currentTimeMillis()));
    }

    private static final class Binding implements ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<TextView> view;
        final Date date = new Date();
        final StringBuffer buffer = new StringBuffer(24);
        final FieldPosition position = new FieldPosition(0);
        SimpleDateFormat format;
        Locale locale;
        TimeZone zone;
        String pattern;
        boolean eligible, wasShown, changed;

        Binding(TextView view) { this.view = new WeakReference<>(view); }

        void configure() {
            TextView target = view.get();
            if (target == null) return;
            try {
                eligible = mode.getInt(target) == 0;
                if (!eligible) return;
                Object owner = controller.get(target);
                String desired = StatusBarClockSecondsPolicy.pattern(
                        is24.getBoolean(owner), amPmStyle.getInt(target) == 0);
                Locale nextLocale = target.getResources().getConfiguration().getLocales().get(0);
                TimeZone nextZone = TimeZone.getDefault();
                if (format == null || !desired.equals(pattern) || !nextLocale.equals(locale)
                        || !nextZone.equals(zone)) {
                    pattern = desired;
                    locale = nextLocale;
                    zone = nextZone;
                    format = new SimpleDateFormat(pattern, locale);
                    format.setTimeZone(zone);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                eligible = false;
            }
        }

        boolean render() {
            TextView target = view.get();
            if (target == null || !eligible || format == null) return false;
            boolean demoMode;
            try { demoMode = demo.getBoolean(controller.get(target)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
            if (!StatusBarClockSecondsPolicy.shouldTick(enabled(), screenOn,
                    target.isAttachedToWindow(), target.isShown()
                            && target.getWindowVisibility() == View.VISIBLE, demoMode)) return false;
            date.setTime(System.currentTimeMillis());
            buffer.setLength(0);
            String text = format.format(date, buffer, position).toString();
            if (!TextUtils.equals(target.getText(), text)) target.setText(text);
            changed = true;
            return true;
        }

        void restore(TextView target) {
            if (!changed) return;
            changed = false;
            try { updateTime.invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* Keep stock hooks. */ }
        }

        @Override public void onGlobalLayout() {
            if (!enabled()) return;
            TextView target = view.get();
            boolean shown = target != null && target.isShown()
                    && target.getWindowVisibility() == View.VISIBLE;
            if (shown == wasShown) return;
            wasShown = shown;
            reconcile();
        }
    }
}
