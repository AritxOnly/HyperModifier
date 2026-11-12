package com.aritxonly.myhypermodifier;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Reuses native text, charging policy, measuring and layout. No drawing/animation/global hooks. */
final class ControlCenterBatteryPercentHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Main-thread lifecycle callbacks. Values contain no reference to a View or Context.
    private static final WeakHashMap<View, Binding> VIEWS = new WeakHashMap<>();
    private static final ControlCenterBatteryPanelState PANEL = new ControlCenterBatteryPanelState();
    private static Field style, toAod, percentContainer, percentFolme, iconView;
    private static Object expandingState, expandedState;
    private static MethodHandle updateText, showPercent, hidePercent, contentState;
    private static int lastConfig;

    private ControlCenterBatteryPercentHooks() {}

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && !ModuleSettings.systemUiCompatibilityMode
                && ModuleSettings.controlCenterShowBatteryPercent;
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Class<?> type = Class.forName(SYSTEM_UI + ".statusbar.views.MiuiBatteryMeterView", false, loader);
        style = type.getField("mBatteryStyle");
        toAod = type.getField("mToAod");
        percentContainer = type.getField("mBatteryPercentContainer");
        percentFolme = type.getField("mBatteryPercentContainerFolme");
        iconView = type.getField("mBatteryIconView");
        MethodType visibilityType = MethodType.methodType(void.class, Object.class, View.class, String.class);
        showPercent = MethodHandles.lookup().unreflect(type.getDeclaredMethod("showView",
                percentFolme.getType(), View.class, String.class)).asType(visibilityType);
        hidePercent = MethodHandles.lookup().unreflect(type.getDeclaredMethod("hideView",
                percentFolme.getType(), View.class, String.class)).asType(visibilityType);
        Method update = type.getDeclaredMethod("updateChargeAndText");
        updateText = MethodHandles.lookup().unreflect(update)
                .asType(MethodType.methodType(void.class, Object.class));
        Method attach = type.getDeclaredMethod("onAttachedToWindow");
        Method detach = type.getDeclaredMethod("onDetachedFromWindow");
        Method layout = type.getDeclaredMethod("onLayout", boolean.class,
                int.class, int.class, int.class, int.class);
        Class<?> phase = Class.forName(SYSTEM_UI
                + ".plugins.miui.controlcenter.ControlCenterContent$ExpandState", false, loader);
        expandingState = phase.getField("EXPANDING").get(null);
        expandedState = phase.getField("EXPANDED").get(null);
        Class<?> listener = Class.forName("com.miui.systemui.controlcenter.container."
                + "ControlCenterContainerController$onExpandChangeListener$1", false, loader);
        Method expand = listener.getDeclaredMethod("onExpandStateChanged", phase);
        Class<?> content = Class.forName(SYSTEM_UI + ".plugins.miui.controlcenter.ControlCenterContent", false, loader);
        Class<?> container = Class.forName("com.miui.systemui.controlcenter.container."
                + "ControlCenterContainerController", false, loader);
        Method contentAttached = container.getDeclaredMethod("onContentAttached", content);
        Method contentDetached = container.getDeclaredMethod("onContentDetached", content);
        contentState = MethodHandles.lookup().unreflect(content.getDeclaredMethod("getExpandState"))
                .asType(MethodType.methodType(Object.class, Object.class));
        Method changeStyle = type.getDeclaredMethod("onBatteryStyleChanged", int.class);
        module.hook(changeStyle).setId("cc-battery-percent-style")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View view = (View) chain.getThisObject();
                    Binding binding = VIEWS.get(view);
                    if (binding == null || (!enabled() && !binding.policy.hasOverride())) {
                        return chain.proceed();
                    }
                    // Restore only our effective-style override before stock compares old/new styles.
                    style.setInt(view, binding.policy.beforeStockStyle(style.getInt(view)));
                    boolean wasWorking = binding.working;
                    binding.working = true;
                    Object result;
                    try { result = chain.proceed(); }
                    finally { binding.working = wasWorking; }
                    reconcile(view, binding);
                    return result;
                });
        module.hook(update).setId("cc-battery-percent-text")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    Binding binding = VIEWS.get(chain.getThisObject());
                    if (binding != null && !binding.working) {
                        reconcile((View) chain.getThisObject(), binding);
                    }
                    return result;
                });
        // Observe the full plugin state before stock reduces it to a transient animation boolean.
        module.hook(expand).setId("cc-battery-percent-panel-state")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    boolean changed = updatePanel(chain.getArg(0));
                    Object result = chain.proceed();
                    if (changed) reconcileAll();
                    return result;
                });
        module.hook(contentAttached).setId("cc-battery-percent-content-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    boolean changed = updatePanel(contentState.invokeExact((Object) chain.getArg(0)));
                    Object result = chain.proceed();
                    if (changed) reconcileAll();
                    return result;
                });
        module.hook(contentDetached).setId("cc-battery-percent-content-detach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    boolean changed = PANEL.update(ControlCenterBatteryPanelState.COLLAPSED);
                    Object result = chain.proceed();
                    if (changed) reconcileAll();
                    return result;
                });
        module.hook(attach).setId("cc-battery-percent-attach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    ModuleSettings.ensureLoaded();
                    lastConfig = configKey();
                    reconcile(view, remember(view));
                    return result;
                });
        module.hook(layout).setId("cc-battery-percent-layout")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    View view = (View) chain.getThisObject();
                    Binding binding = VIEWS.get(view);
                    if (binding != null && binding.policy.hasOverride()) {
                        try {
                            centerExternalPercent(view, binding);
                        } catch (Throwable error) {
                            HookDiagnostics.failure(SYSTEM_UI, "控制中心外显电量", error);
                        }
                    }
                    return result;
                });
        module.hook(detach).setId("cc-battery-percent-detach")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    View view = (View) chain.getThisObject();
                    Binding binding = VIEWS.remove(view);
                    if (binding != null) {
                        int current = style.getInt(view);
                        int restored = binding.policy.restore(current);
                        if (current != restored) {
                            style.setInt(view, restored);
                            updateText.invokeExact((Object) view);
                            settlePercent(view, binding, restored == 3);
                        }
                        restoreExternalPercentPosition(view, binding);
                    }
                    return chain.proceed();
                });
        HookDiagnostics.available(SYSTEM_UI, "控制中心电量");
    }

    private static Binding remember(View view) {
        Binding binding = VIEWS.get(view);
        if (binding == null) {
            binding = new Binding();
            VIEWS.put(view, binding);
        }
        return binding;
    }

    private static void reconcile(View view, Binding binding) {
        if (view == null || binding.working || binding.failed || !view.isAttachedToWindow()) return;
        if (!enabled() && !binding.policy.hasOverride()) return;
        try {
            int current = style.getInt(view);
            boolean owned = binding.policy.hasOverride();
            boolean drawsInside = ControlCenterBatteryPercentPolicy.drawsInside(
                    ModuleSettings.statusBarSolidBatteryFrame,
                    ModuleSettings.controlCenterBatteryPercentPlacement);
            int desired = binding.policy.resolve(current, enabled() && !toAod.getBoolean(view),
                    PANEL.showing(), !drawsInside);
            boolean dirty = binding.policy.takeDirty();
            if (current == desired && !dirty) return;
            binding.working = true;
            try {
                style.setInt(view, desired);
                // Re-enter only on an actual transition. The nested text hook has a cheap exit.
                updateText.invokeExact((Object) view);
                if (owned || binding.policy.hasOverride()) {
                    settlePercent(view, binding, desired == 3);
                }
            } finally { binding.working = false; }
        } catch (Throwable error) {
            binding.failed = true;
            try { style.setInt(view, binding.policy.restore(style.getInt(view))); }
            catch (IllegalAccessException ignored) { /* Contract checked at installation. */ }
            HookDiagnostics.failure(SYSTEM_UI, "控制中心外显电量", error);
        }
    }

    private static boolean updatePanel(Object state) {
        int phase = state == expandedState ? ControlCenterBatteryPanelState.EXPANDED
                : state == expandingState ? ControlCenterBatteryPanelState.EXPANDING
                : ControlCenterBatteryPanelState.COLLAPSED;
        return PANEL.update(phase);
    }

    private static void reconcileAll() {
        for (var entry : VIEWS.entrySet()) reconcile(entry.getKey(), entry.getValue());
        StatusBarBatteryFrameHooks.refreshControlCenterPercent();
    }

    /** Native helpers cancel in-flight percentage alpha/blur transitions before setting visibility. */
    private static void settlePercent(View view, Binding binding, boolean visible) throws Throwable {
        View target = (View) percentContainer.get(view);
        if (target == null) return;
        Object folme = percentFolme.get(view);
        if (folme != null) {
            if (visible) showPercent.invokeExact(folme, target, "battery_percent_container");
            else hidePercent.invokeExact(folme, target, "battery_percent_container");
        } else {
            target.setAlpha(1f);
            target.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (visible) centerExternalPercent(view, binding);
        else restoreExternalPercentPosition(view, binding);
    }

    private static void centerExternalPercent(View view, Binding binding) throws IllegalAccessException {
        View percent = (View) percentContainer.get(view);
        View icon = (View) iconView.get(view);
        View parent = percent == null ? null : (View) percent.getParent();
        if (percent == null || icon == null || parent == null ||
                percent.getVisibility() != View.VISIBLE ||
                percent.getHeight() <= 0 || icon.getHeight() <= 0) return;
        int[] parentLocation = new int[2];
        int[] iconLocation = new int[2];
        parent.getLocationOnScreen(parentLocation);
        icon.getLocationOnScreen(iconLocation);
        float desiredCenter = iconLocation[1] + icon.getHeight() / 2f - parentLocation[1];
        float layoutCenter = percent.getTop() + percent.getHeight() / 2f;
        if (!binding.ownsPercentTranslation) {
            binding.originalPercentTranslationY = percent.getTranslationY();
            binding.ownsPercentTranslation = true;
        }
        percent.setTranslationY(ControlCenterBatteryPercentPolicy.centeredTranslationY(
                binding.originalPercentTranslationY, layoutCenter, desiredCenter));
    }

    private static void restoreExternalPercentPosition(View view, Binding binding)
            throws IllegalAccessException {
        if (!binding.ownsPercentTranslation) return;
        View percent = (View) percentContainer.get(view);
        if (percent != null) percent.setTranslationY(binding.originalPercentTranslationY);
        binding.ownsPercentTranslation = false;
    }

    static boolean shouldShowPercentInside() {
        return enabled()
                && ControlCenterBatteryPercentPolicy.drawsInside(
                        ModuleSettings.statusBarSolidBatteryFrame,
                        ModuleSettings.controlCenterBatteryPercentPlacement)
                && PANEL.showing();
    }

    private static int configKey() {
        if (!enabled()) return 0;
        return ControlCenterBatteryPercentPolicy.drawsInside(
                ModuleSettings.statusBarSolidBatteryFrame,
                ModuleSettings.controlCenterBatteryPercentPlacement) ? 2 : 1;
    }

    static void refresh() {
        if (!PENDING.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            PENDING.set(false);
            int config = configKey();
            if (config == lastConfig) return;
            lastConfig = config;
            reconcileAll();
        });
    }

    private static final class Binding {
        final ControlCenterBatteryPercentPolicy policy = new ControlCenterBatteryPercentPolicy();
        boolean working;
        boolean failed;
        boolean ownsPercentTranslation;
        float originalPercentTranslationY;
    }
}
