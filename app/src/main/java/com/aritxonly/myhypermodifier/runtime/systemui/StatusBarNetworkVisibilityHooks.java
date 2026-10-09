package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.StatusBarNetworkVisibilityPolicy.Kind;

/** Resource-name based hooks confined to status-bar mobile and Wi-Fi view descendants. */
final class StatusBarNetworkVisibilityHooks {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String MOBILE_VIEW = SYSTEM_UI
            + ".statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    private static final String WIFI_VIEW = SYSTEM_UI
            + ".statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Map<View, Target> TARGETS = new WeakHashMap<>();
    private static final Map<ViewGroup, boolean[]> STOCK_CLIPPING = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ConnectivityManager connectivity;
    private static ConnectivityManager.NetworkCallback networkCallback;
    private static boolean networkObservationFailed;
    private static volatile boolean wifi;

    private StatusBarNetworkVisibilityHooks() {}

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            module.hook(View.class.getDeclaredMethod("setVisibility", int.class))
                    .setId("status-bar-network-visibility")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Target target;
                        synchronized (TARGETS) { target = TARGETS.get(chain.getThisObject()); }
                        if (target == null || APPLYING.get()) return chain.proceed();
                        target.policy.recordStockVisibility((Integer) chain.getArg(0));
                        return chain.proceed(new Object[]{target.visibility()});
                    });
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "状态栏信号隐藏", error);
            return;
        }
        installTranslationHook(module, "setTranslationX", true);
        installTranslationHook(module, "setTranslationY", false);
        // These classes may inherit FrameLayout.onMeasure. Hook each resolved method once and
        // filter receivers before touching resources, so other FrameLayouts stay untouched.
        Set<Method> hooked = new HashSet<>();
        installView(module, loader, MOBILE_VIEW, hooked);
        installView(module, loader, WIFI_VIEW, hooked);
    }

    private static void installTranslationHook(XposedModule module, String method, boolean horizontal) {
        try {
            module.hook(View.class.getDeclaredMethod(method, float.class))
                    .setId("status-bar-network-" + method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Target target;
                        synchronized (TARGETS) { target = TARGETS.get(chain.getThisObject()); }
                        if (target == null || !target.isActivity() || APPLYING.get()) {
                            return chain.proceed();
                        }
                        float stock = (Float) chain.getArg(0);
                        if (horizontal) target.stockX = stock;
                        else target.stockY = stock;
                        View view = (View) chain.getThisObject();
                        float offset = hasActivityAncestor(view, target.kind) ? 0f
                                : target.offset(horizontal) * view.getResources().getDisplayMetrics().density;
                        return chain.proceed(new Object[]{stock + offset});
                    });
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, "上下行箭头位置", error);
        }
    }

    // ROMs may have either a combined indicator or individual arrows inside a container.
    // Translating both the container and its children would apply the offset twice.
    private static boolean hasActivityAncestor(View view, Kind kind) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View ancestor) {
            Target target;
            synchronized (TARGETS) { target = TARGETS.get(ancestor); }
            if (target != null && target.kind == kind) return true;
            parent = ancestor.getParent();
        }
        return false;
    }

    private static void installView(XposedModule module, ClassLoader loader, String name,
                                    Set<Method> hooked) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            Class<?> owner = type;
            Method measure = null;
            while (owner != null) {
                try {
                    measure = owner.getDeclaredMethod("onMeasure", int.class, int.class);
                    break;
                } catch (NoSuchMethodException ignored) { owner = owner.getSuperclass(); }
            }
            if (measure == null) throw new NoSuchMethodException(name + ".onMeasure");
            if (hooked.add(measure)) {
                module.hook(measure)
                        .setId("status-bar-network-measure-" + hooked.size())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object receiver = chain.getThisObject();
                            if (receiver instanceof View root) {
                                String className = root.getClass().getName();
                                if (MOBILE_VIEW.equals(className) || WIFI_VIEW.equals(className)) {
                                    try { prepare(root, MOBILE_VIEW.equals(className)); }
                                    catch (RuntimeException error) {
                                        HookDiagnostics.failure(SYSTEM_UI, "状态栏信号隐藏", error);
                                    }
                                }
                            }
                            return chain.proceed();
                        });
            }
            HookDiagnostics.available(SYSTEM_UI, name.equals(MOBILE_VIEW)
                    ? "移动网络类型及上下行箭头隐藏" : "Wi-Fi 制式及上下行箭头隐藏");
        } catch (Throwable error) {
            HookDiagnostics.failure(SYSTEM_UI, name.equals(MOBILE_VIEW)
                    ? "移动网络类型及上下行箭头隐藏" : "Wi-Fi 制式及上下行箭头隐藏", error);
        }
    }

    private static void prepare(View root, boolean mobile) {
        ModuleSettings.ensureLoaded();
        if (ModuleSettings.statusBarHideMobileTypeOnWifi) observeNetwork(root.getContext());
        if (mobile) {
            remember(root, "mobile_type", Kind.MOBILE_TYPE);
            remember(root, "mobile_left_mobile_inout", Kind.MOBILE_ACTIVITY);
            remember(root, "mobile_in", Kind.MOBILE_ACTIVITY);
            remember(root, "mobile_out", Kind.MOBILE_ACTIVITY);
        } else {
            remember(root, "wifi_standard", Kind.WIFI_STANDARD);
            remember(root, "inout_container", Kind.WIFI_ACTIVITY);
            remember(root, "wifi_activity", Kind.WIFI_ACTIVITY);
            remember(root, "wifi_in", Kind.WIFI_ACTIVITY);
            remember(root, "wifi_out", Kind.WIFI_ACTIVITY);
        }
    }

    private static void remember(View root, String name, Kind kind) {
        int id = root.getResources().getIdentifier(name, "id", SYSTEM_UI);
        View child = id == 0 ? null : root.findViewById(id);
        if (child == null) return;
        Target target;
        synchronized (TARGETS) {
            target = TARGETS.get(child);
            if (target == null) {
                target = new Target(kind, child);
                TARGETS.put(child, target);
            }
        }
        apply(child, target);
    }

    private static void apply(View view, Target target) {
        int visibility = target.visibility();
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            if (view.getVisibility() != visibility) view.setVisibility(visibility);
            if (target.isActivity()) {
                applyClipping(view, target);
                float density = view.getResources().getDisplayMetrics().density;
                boolean child = hasActivityAncestor(view, target.kind);
                float x = target.stockX + (child ? 0f : target.offset(true) * density);
                float y = target.stockY + (child ? 0f : target.offset(false) * density);
                if (view.getTranslationX() != x) view.setTranslationX(x);
                if (view.getTranslationY() != y) view.setTranslationY(y);
            }
        }
        finally { APPLYING.set(previous); }
    }

    private static void applyClipping(View view, Target target) {
        boolean shifted = target.offset(true) != 0f || target.offset(false) != 0f;
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup group) {
            boolean[] stock = STOCK_CLIPPING.get(group);
            if (stock == null && shifted) {
                stock = new boolean[]{group.getClipChildren(), group.getClipToPadding()};
                STOCK_CLIPPING.put(group, stock);
            }
            if (stock != null) {
                boolean children = !shifted && stock[0];
                boolean padding = !shifted && stock[1];
                if (group.getClipChildren() != children) group.setClipChildren(children);
                if (group.getClipToPadding() != padding) group.setClipToPadding(padding);
            }
            String name = group.getClass().getName();
            if (MOBILE_VIEW.equals(name) || WIFI_VIEW.equals(name)) break;
            parent = group.getParent();
        }
    }

    private static void observeNetwork(Context context) {
        if (networkCallback != null || networkObservationFailed) return;
        connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) return;
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { refresh(); }
            @Override public void onLost(Network network) { refresh(); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                refresh();
            }
        };
        try {
            connectivity.registerDefaultNetworkCallback(callback);
            networkCallback = callback;
            updateWifi();
        } catch (RuntimeException error) {
            networkObservationFailed = true;
            connectivity = null;
            wifi = false;
            HookDiagnostics.failure(SYSTEM_UI, "Wi-Fi 网络监听", error);
        }
    }

    private static void updateWifi() {
        if (connectivity == null) return;
        try {
            Network active = connectivity.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null
                    : connectivity.getNetworkCapabilities(active);
            wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (RuntimeException ignored) { wifi = false; }
    }

    /** Called after settings loads as well as default-network transitions, always on the UI queue. */
    static void refresh() {
        MAIN.post(() -> {
            updateWifi();
            ArrayList<Map.Entry<View, Target>> targets;
            synchronized (TARGETS) { targets = new ArrayList<>(TARGETS.entrySet()); }
            for (Map.Entry<View, Target> entry : targets) {
                View view = entry.getKey();
                if (view == null) continue;
                if (ModuleSettings.statusBarHideMobileTypeOnWifi) observeNetwork(view.getContext());
                apply(view, entry.getValue());
            }
        });
    }

    private static final class Target {
        final Kind kind;
        final StatusBarNetworkVisibilityPolicy policy;
        float stockX;
        float stockY;

        Target(Kind kind, View view) {
            this.kind = kind;
            policy = new StatusBarNetworkVisibilityPolicy(view.getVisibility());
            stockX = view.getTranslationX();
            stockY = view.getTranslationY();
        }

        boolean isActivity() {
            return kind == Kind.MOBILE_ACTIVITY || kind == Kind.WIFI_ACTIVITY;
        }

        float offset(boolean horizontal) {
            float value = kind == Kind.MOBILE_ACTIVITY
                    ? (horizontal ? ModuleSettings.statusBarMobileActivityOffsetX
                                  : ModuleSettings.statusBarMobileActivityOffsetY)
                    : (horizontal ? ModuleSettings.statusBarWifiActivityOffsetX
                                  : ModuleSettings.statusBarWifiActivityOffsetY);
            return Float.isFinite(value) ? Math.max(-1.5f, Math.min(1.5f, value)) : 0f;
        }

        int visibility() {
            return policy.resolve(kind, wifi, ModuleSettings.statusBarHideMobileTypeOnWifi,
                    ModuleSettings.statusBarHideMobileActivity, ModuleSettings.statusBarHideWifiActivity,
                    ModuleSettings.statusBarHideWifiStandard);
        }
    }
}
