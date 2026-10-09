package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Changes the AllInOne result, retaining SystemUI's drag/Folme animation machinery. */
final class LockscreenClockAvoidanceHooks {
    private static final String FEATURE = "锁屏时钟渐进避让通知";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<View, State> STATES = new WeakHashMap<>();
    private static Method notifStateChange;
    private static Constructor<?> resultConstructor;
    private static boolean failed;

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        if (Build.VERSION.SDK_INT < 34) {
            HookDiagnostics.unavailable("com.android.systemui", FEATURE, "需要 Android 14 或更新版本");
            return;
        }
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            Class<?> interactor = Class.forName(
                    "com.android.keyguard.interactor.KeyguardClockNotifInteractor", false, loader);
            Class<?> container = Class.forName(
                    "com.android.keyguard.clock.KeyguardClockContainer", false, loader);
            Class<?> type = Class.forName(
                    "com.miui.systemui.notification.data.repository.NotificationTopChangeType", false, loader);
            Class<?> result = Class.forName("com.miui.interfaces.keyguard.data.ClockResult", false, loader);
            resultConstructor = result.getConstructor(float.class, int.class, int.class, int.class, float.class);
            notifStateChange = container.getDeclaredMethod("notifStateChange", float.class, boolean.class, type);
            Method calculate = interactor.getDeclaredMethod("calculateClockSqueezeInternal");
            for (String field : new String[]{"context", "clockInjector", "maxEditRect", "isParamsValid",
                    "adaptClockTopRatio", "adaptClockBottomRatio", "adaptTimeHeight", "adaptTimeWidth",
                    "timeMinHeight", "baseClockMaxHeight", "repository", "timeSqueezeRatio", "allowWeightScale"})
                interactor.getField(field);
            handles.add(module.hook(notifStateChange).setId("lockscreen-clock-progressive-input")
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ModuleSettings.ensureLoaded();
                        View view = (View) chain.getThisObject();
                        if (ModuleSettings.progressiveLockscreenClockAvoidance || STATES.containsKey(view)) {
                            State state = stateFor(view);
                            if (!state.replaying) {
                                state.y = (Float) chain.getArg(0);
                                state.drag = (Boolean) chain.getArg(1);
                                state.type = chain.getArg(2);
                            }
                        }
                        return chain.proceed();
                    }));
            handles.add(module.hook(calculate).setId("lockscreen-clock-progressive-result")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object nativeResult = chain.proceed();
                        ModuleSettings.ensureLoaded();
                        if (!ModuleSettings.progressiveLockscreenClockAvoidance || failed
                                || nativeResult == null || HyperMusicCoverClockHooks.ownsClock()) return nativeResult;
                        try {
                            return adjusted(chain.getThisObject(), nativeResult);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            failed = true;
                            HookDiagnostics.failure("com.android.systemui", FEATURE, error);
                            return nativeResult;
                        }
                    }));
            module.deoptimize(calculate);
            module.deoptimize(notifStateChange);
            HookDiagnostics.available("com.android.systemui", FEATURE);
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            INSTALLED.set(false);
            throw error;
        }
    }

    private static Object adjusted(Object interactor, Object nativeResult) throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(ReflectiveAccess.fieldValue(interactor, "isParamsValid"))) return nativeResult;
        Object injector = ReflectiveAccess.fieldValue(interactor, "clockInjector");
        Object clock = ReflectiveAccess.fieldValue(injector, "keyguardClockView");
        if (!(clock instanceof View view) || !eligible(view)) return nativeResult;
        if (!Boolean.TRUE.equals(value(ReflectiveAccess.fieldValue(interactor, "hasVisibleNotifsOnKeyguard"))))
            return nativeResult;
        Rect edit = (Rect) ReflectiveAccess.fieldValue(interactor, "maxEditRect");
        if (edit == null || edit.isEmpty()) return nativeResult;
        float top = notificationTop(view);
        if (Float.isNaN(top)) return nativeResult; // Stack unavailable: preserve native behaviour.
        Object repository = ReflectiveAccess.fieldValue(interactor, "repository");
        Context context = (Context) ReflectiveAccess.fieldValue(interactor, "context");
        LockscreenClockAvoidancePolicy.Result result = LockscreenClockAvoidancePolicy.calculate(
                edit.top, edit.height(), number(interactor, "adaptClockTopRatio"),
                number(interactor, "adaptClockBottomRatio"), (int) number(interactor, "adaptTimeHeight"),
                (int) number(interactor, "adaptTimeWidth"), (int) number(interactor, "timeMinHeight"),
                (int) number(interactor, "baseClockMaxHeight"),
                ((Number) value(ReflectiveAccess.fieldValue(repository, "timeWeight"))).intValue(),
                ((Number) value(ReflectiveAccess.fieldValue(repository, "magazineHeight"))).floatValue(),
                number(interactor, "timeSqueezeRatio"),
                Boolean.TRUE.equals(ReflectiveAccess.fieldValue(interactor, "allowWeightScale")),
                top, context.getResources().getDisplayMetrics().density);
        if (result == null) return nativeResult;
        stateFor(view).applied = true;
        // Do not overwrite the interactor's native cache: disabling the switch restores it.
        return resultConstructor.newInstance(result.translation(), result.width(), result.height(),
                result.weight(), result.magazineTranslation());
    }

    private static boolean eligible(View view) {
        if (!view.isAttachedToWindow() || !view.isShown()
                || ReflectiveAccess.booleanDeclaredField(view, "mToAod", false)) return false;
        Object helper = ReflectiveAccess.fieldValue(view, "mAnimationHelper");
        Object animation = ReflectiveAccess.fieldValue(helper, "mClockAnima");
        return animation != null && animation.getClass().getName().equals(
                "com.android.keyguard.clock.animation.allinone.AllInOneClockAnimation")
                && !ReflectiveAccess.booleanDeclaredField(animation, "mToAod", false)
                && !ReflectiveAccess.booleanDeclaredField(animation, "isAodAnimation", false);
    }

    private static float number(Object owner, String name) {
        return ((Number) ReflectiveAccess.fieldValue(owner, name)).floatValue();
    }

    private static Object value(Object flow) throws ReflectiveOperationException {
        if (flow == null) return null;
        return flow.getClass().getMethod("getValue").invoke(flow);
    }

    /** Read actual rows rather than the stack's pessimistic reserved notification area. */
    private static float notificationTop(View clock) throws ReflectiveOperationException {
        int id = clock.getResources().getIdentifier("notification_stack_scroller", "id", "com.android.systemui");
        View found = id == 0 ? null : clock.getRootView().findViewById(id);
        if (!(found instanceof ViewGroup stack)) return Float.NaN;
        if (!stack.isShown()) return Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        Rect visible = new Rect();
        Matrix parentMatrix = new Matrix();
        stack.transformMatrixToGlobal(parentMatrix);
        for (int i = 0; i < stack.getChildCount(); i++) {
            View row = stack.getChildAt(i);
            String name = row.getClass().getName();
            if (!name.contains("ExpandableNotificationRow") && !name.contains("MediaHeader")) continue;
            if (!row.isShown() || row.getAlpha() <= 0.01f || !row.getGlobalVisibleRect(visible)) continue;
            Object rowState = row.getClass().getMethod("getViewState").invoke(row);
            if (rowState != null && ReflectiveAccess.booleanDeclaredField(rowState, "hidden", false)) continue;
            if (name.contains("ExpandableNotificationRow")) {
                Object injector = row.getClass().getMethod("getInjector").invoke(row);
                Object alpha = ReflectiveAccess.fieldValue(injector, "numStateAlpha");
                if (alpha instanceof Number && ((Number) alpha).floatValue() <= 0.01f) continue;
            }
            Object targetY = ReflectiveAccess.fieldValue(rowState, "mYTranslation");
            float delta = targetY instanceof Number ? ((Number) targetY).floatValue() - row.getTranslationY() : 0;
            float[] vector = {0, delta};
            parentMatrix.mapVectors(vector);
            top = Math.min(top, visible.top + vector[1]);
        }
        return top;
    }

    private static State stateFor(View view) {
        State state = STATES.get(view);
        if (state == null) {
            state = new State(view);
            STATES.put(view, state);
            view.addOnAttachStateChangeListener(state);
            if (view.isAttachedToWindow()) state.onViewAttachedToWindow(view);
        }
        return state;
    }

    /** Recalculate when short rows settle without a new reserved-stack-top event. */
    private static final class State implements ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        final WeakReference<View> clock;
        ViewTreeObserver observer;
        float y, lastTop = Float.NaN;
        boolean drag, replaying, applied, lastEnabled;
        Object type;

        State(View view) { clock = new WeakReference<>(view); }

        @Override public void onViewAttachedToWindow(View view) {
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
            lastTop = Float.NaN;
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
            lastTop = Float.NaN;
        }

        @Override public boolean onPreDraw() {
            View view = clock.get();
            if (view == null || type == null || replaying || !eligible(view)) return true;
            boolean enabled = ModuleSettings.progressiveLockscreenClockAvoidance && !failed;
            if (HyperMusicCoverClockHooks.ownsClock()) { lastEnabled = false; return true; }
            if (!enabled && !applied) return true;
            try {
                float top = enabled ? notificationTop(view) : Float.NaN;
                // No stack on an unsupported layout: restore once, then stop replaying.
                if (enabled && Float.isNaN(top)) {
                    if (!applied) return true;
                    enabled = false;
                }
                boolean changed = enabled != lastEnabled || Float.isNaN(lastTop)
                        || (top != lastTop && (Float.isInfinite(top) || Float.isInfinite(lastTop)
                            || Math.abs(top - lastTop) > 1f));
                if (!changed) return true;
                lastTop = top;
                lastEnabled = enabled;
                replaying = true;
                applied = false;
                notifStateChange.invoke(view, y, drag, type);
            } catch (ReflectiveOperationException | RuntimeException error) {
                failed = true;
                HookDiagnostics.failure("com.android.systemui", FEATURE, error);
            } finally { replaying = false; }
            return true;
        }
    }
}
