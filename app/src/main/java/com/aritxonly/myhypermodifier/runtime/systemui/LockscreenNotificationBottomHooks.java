package com.aritxonly.myhypermodifier;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** HMC's native stacking bottom, including targets created before deferred hook installation. */
final class LockscreenNotificationBottomHooks {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean QUEUED = new AtomicBoolean(), NATIVE_OBSERVED = new AtomicBoolean(), APPLIED_OBSERVED = new AtomicBoolean();
    private static final WeakHashMap<Object, Binding> BINDINGS = new WeakHashMap<>();
    private static Method update, childrenUpdate;
    private static Field layout, stackingBottom, modelLazy, animations, expanded, topAnimation, needsAnimation;
    private static MethodHandle lazyGet, showing, flowValue;
    private static volatile long lastSignature;
    private static boolean failed, installed;
    private LockscreenNotificationBottomHooks() {}

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutControllerInjectorImpl", false, loader);
        layout = type.getDeclaredField("layout");
        stackingBottom = type.getDeclaredField("stackingBottom");
        modelLazy = type.getDeclaredField("notifContainerViewModel");
        update = type.getDeclaredMethod("updateStackingBottomForMiui", float.class);
        Class<?> stack = layout.getType();
        childrenUpdate = stack.getDeclaredMethod("requestChildrenUpdate");
        childrenUpdate.setAccessible(true);
        animations = stack.getDeclaredField("mAnimationsEnabled");
        expanded = stack.getDeclaredField("mIsExpanded");
        topAnimation = stack.getDeclaredField("mTopPaddingNeedsAnimation");
        needsAnimation = stack.getDeclaredField("mNeedsAnimation");
        for (Field field : new Field[] {layout, stackingBottom, modelLazy, animations, expanded, topAnimation, needsAnimation}) field.setAccessible(true);
        Class<?> lazy = Class.forName("dagger.Lazy", false, loader);
        Class<?> model = Class.forName("com.miui.systemui.notification.view.viewmodel.NotificationContainerViewModel", false, loader);
        Method state = model.getMethod("isShowingKeyguardNotification");
        lazyGet = getter(lazy.getMethod("get"));
        showing = getter(state);
        flowValue = getter(state.getReturnType().getMethod("getValue"));
        module.hook(update).setId("lockscreen-notification-bottom")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object injector = chain.getThisObject();
                    Binding binding = binding(injector);
                    float previous = stackingBottom.getFloat(injector);
                    boolean wasOverridden = binding.state.overridden;
                    Object result = chain.proceed();
                    binding.state.nativeResult((float) chain.getArg(0), stackingBottom.getFloat(injector));
                    if (NATIVE_OBSERVED.compareAndSet(false, true)) HookDiagnostics.observed("com.android.systemui", "通知底界原生回调", "已命中 updateStackingBottomForMiui");
                    reconcile(binding, previous, wasOverridden);
                    return result;
                });
        installed = true;
        lastSignature = signature();
    }

    private static Binding binding(Object injector) throws IllegalAccessException {
        Binding binding = BINDINGS.get(injector);
        if (binding == null) {
            View stack = (View) layout.get(injector);
            binding = new Binding(injector, stack);
            binding.state.adopt(stackingBottom.getFloat(injector));
            BINDINGS.put(injector, binding);
        }
        return binding;
    }
    static void adoptExisting(Object injector) throws ReflectiveOperationException {
        if (!installed || injector == null) return;
        binding(injector);
        HookDiagnostics.observed("com.android.systemui", "通知底界目标", "已登记现有通知控制器；不依赖新通知或重建界面");
        if (enabled()) schedule();
    }
    private static void reconcile(Binding binding, float previous, boolean wasOverridden) {
        Object injector = binding.injector.get();
        View stack = binding.stack.get();
        if (injector == null || stack == null) return;
        try {
            float current = stackingBottom.getFloat(injector);
            float baseline = binding.state.baseline(current);
            float result = baseline;
            boolean lockscreen = !failed && enabled() && stack.isAttachedToWindow() && showingKeyguardNotifications(injector);
            if (lockscreen) {
                View root = stack.getRootView();
                float shortcutTop = ModuleSettings.lockscreenNotificationBottomAnchor == LockscreenBottomPolicy.SHORTCUTS
                        ? LockscreenShortcutGlassHooks.shortcutTopIn(root) : Float.NaN;
                result = LockscreenBottomPolicy.bottom(baseline, root.getHeight(), layoutTopIn(stack, root),
                        stack.getHeight(), shortcutTop,
                        LockscreenBottomPolicy.gap(ModuleSettings.lockscreenNotificationBottomGap) * stack.getResources().getDisplayMetrics().density,
                        ModuleSettings.lockscreenNotificationBottomAnchor, LockscreenShortcutGlassHooks.enabled());
                if (APPLIED_OBSERVED.compareAndSet(false, true)) HookDiagnostics.observed("com.android.systemui", "通知底界应用",
                        "native=" + baseline + ", applied=" + result + ", rootH=" + root.getHeight()
                                + ", stackH=" + stack.getHeight() + ", shortcutTop=" + shortcutTop);
            }
            if (Float.compare(current, result) != 0) stackingBottom.setFloat(injector, result);
            binding.state.applied(baseline, result);
            if ((wasOverridden || binding.state.overridden) && Math.abs(previous - result) >= 1f && stack.isAttachedToWindow()) restack(stack);
        } catch (Throwable error) {
            if (!failed) {
                failed = true;
                HookDiagnostics.failure("com.android.systemui", "通知底界应用", error);
                schedule();
            }
        }
    }
    private static MethodHandle getter(Method method) throws IllegalAccessException {
        return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(Object.class, Object.class));
    }
    private static boolean showingKeyguardNotifications(Object injector) throws Throwable {
        Object lazy = modelLazy.get(injector);
        Object model = (Object) lazyGet.invokeExact(lazy);
        Object flow = (Object) showing.invokeExact(model);
        return Boolean.TRUE.equals((Object) flowValue.invokeExact(flow));
    }
    private static float layoutTopIn(View view, View root) {
        float y = 0;
        while (view != root) {
            y += view.getTop();
            if (!(view.getParent() instanceof View)) return Float.NaN;
            view = (View) view.getParent();
        }
        return y;
    }
    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode && ModuleSettings.lockscreenNotificationBottomEnabled;
    }
    private static long signature() {
        if (!enabled()) return 0;
        return 1L << 40 | (long) LockscreenBottomPolicy.anchor(ModuleSettings.lockscreenNotificationBottomAnchor) << 32
                | (Float.floatToIntBits(LockscreenBottomPolicy.gap(ModuleSettings.lockscreenNotificationBottomGap)) & 0xffffffffL);
    }
    static void refresh() {
        long now = signature();
        if (now == lastSignature) return;
        lastSignature = now;
        if (enabled()) failed = false; // Explicit user change permits a new attempt, never a timer.
        schedule();
    }
    static void geometryChanged() {
        if (enabled() && ModuleSettings.lockscreenNotificationBottomAnchor == LockscreenBottomPolicy.SHORTCUTS) schedule();
    }
    private static void schedule() {
        if (!installed || !QUEUED.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            QUEUED.set(false);
            if (BINDINGS.isEmpty() && enabled()) LockscreenTargetBootstrap.request();
            for (Binding binding : new ArrayList<>(BINDINGS.values())) {
                Object injector = binding.injector.get();
                View stack = binding.stack.get();
                if (injector == null || stack == null) continue;
                try {
                    if (Float.isFinite(binding.state.input)) {
                        update.invoke(injector, binding.state.input);
                    } else {
                        // Late adoption has the native output, not its raw input. Reconcile that
                        // output directly, so navigation inset adjustment can never be applied twice.
                        reconcile(binding, stackingBottom.getFloat(injector), binding.state.overridden);
                    }
                } catch (ReflectiveOperationException error) { HookDiagnostics.failure("com.android.systemui", "通知底界刷新", error); }
            }
        });
    }
    private static void restack(View stack) throws ReflectiveOperationException {
        if (animations.getBoolean(stack) && expanded.getBoolean(stack)) {
            topAnimation.setBoolean(stack, true);
            needsAnimation.setBoolean(stack, true);
        }
        childrenUpdate.invoke(stack);
    }
    private static final class Binding implements View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
        final WeakReference<Object> injector;
        final WeakReference<View> stack;
        final NotificationBottomState state = new NotificationBottomState();
        Binding(Object injector, View stack) {
            this.injector = new WeakReference<>(injector);
            this.stack = new WeakReference<>(stack);
            stack.addOnLayoutChangeListener(this);
            stack.addOnAttachStateChangeListener(this);
        }
        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            if (enabled() && (l != ol || t != ot || r != or || b != ob)) schedule();
        }
        @Override public void onViewAttachedToWindow(View v) { if (enabled()) schedule(); }
        @Override public void onViewDetachedFromWindow(View v) { if (state.overridden) schedule(); }
    }
}
