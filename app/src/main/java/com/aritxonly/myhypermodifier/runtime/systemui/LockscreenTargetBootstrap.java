package com.aritxonly.myhypermodifier;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot adoption of already-created SystemUI targets after deferred Hook installation. */
final class LockscreenTargetBootstrap {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean QUEUED = new AtomicBoolean();
    private static Access access;
    private LockscreenTargetBootstrap() {}

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        access = new Access(
                Class.forName("miui.stub.MiuiStub", false, loader),
                Class.forName("com.android.systemui.statusbar.phone.CentralSurfacesImpl", false, loader),
                Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController", false, loader),
                Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutControllerInjectorImpl", false, loader),
                Class.forName("com.android.keyguard.panel.KeyguardPanelViewController", false, loader),
                Class.forName("com.android.keyguard.shortcut.MiuiShortcutController", false, loader));
        request();
    }
    static void request() {
        if (access == null || !QUEUED.compareAndSet(false, true)) return;
        MAIN.post(() -> {
            QUEUED.set(false);
            if (!ModuleSettings.moduleHooksEnabled || ModuleSettings.systemUiCompatibilityMode
                    || (!LockscreenShortcutGlassHooks.enabled() && !ModuleSettings.lockscreenNotificationBottomEnabled)) return;
            try {
                Snapshot snapshot = access.discover(LockscreenShortcutGlassHooks.enabled());
                if (snapshot == null) {
                    HookDiagnostics.unavailable("com.android.systemui", "锁屏目标接管", "现有通知控制器尚未建立，等待原生生命周期回调或相关设置变化");
                    return;
                }
                // These are independent: a missing shortcut root must not hide the existing stack.
                LockscreenNotificationBottomHooks.adoptExisting(snapshot.injector);
                if (snapshot.shortcutRoot != null) {
                    LockscreenShortcutGlassHooks.adoptExisting(snapshot.shortcutController, snapshot.shortcutRoot);
                }
                HookDiagnostics.observed("com.android.systemui", "锁屏目标接管", "已接管首帧前建立的通知控制器；快捷容器=" + (snapshot.shortcutRoot != null));
            } catch (ReflectiveOperationException | RuntimeException error) {
                HookDiagnostics.failure("com.android.systemui", "锁屏目标接管", error);
            }
        });
    }
    static final class Snapshot {
        final Object injector, shortcutController;
        final View shortcutRoot;
        Snapshot(Object injector, Object shortcutController, View shortcutRoot) {
            this.injector = injector;
            this.shortcutController = shortcutController;
            this.shortcutRoot = shortcutRoot;
        }
    }
    // Only metadata is cached. Resolve current objects anew on the bounded adoption attempts.
    static final class Access {
        final Field singleton, provider, central, stack, injector, panel, shortcuts, root;
        final Method lazyGet;
        Access(Class<?> stub, Class<?> surfaces, Class<?> stackController, Class<?> stackInjector,
                Class<?> panelController, Class<?> shortcutController) throws ReflectiveOperationException {
            singleton = field(stub, "INSTANCE");
            provider = field(stub, "mSysUIProvider");
            central = field(provider.getType(), "mCentralSurfaces");
            lazyGet = central.getType().getMethod("get");
            stack = field(surfaces, "mStackScrollerController");
            injector = field(stackController, "mNsslControllerInjector");
            panel = field(stackInjector, "keyguardPanelViewController");
            shortcuts = field(panelController, "miuiShortcutControllerLazy");
            root = field(shortcutController, "shortcutContainerView");
        }
        Snapshot discover() throws ReflectiveOperationException { return discover(true); }
        Snapshot discover(boolean includeShortcuts) throws ReflectiveOperationException {
            Object stub = singleton.get(null);
            if (stub == null) return null;
            Object sysui = provider.get(stub);
            if (sysui == null) return null;
            Object lazy = central.get(sysui);
            Object surfaces = lazy == null ? null : lazyGet.invoke(lazy);
            if (surfaces == null) return null;
            Object controller = stack.get(surfaces);
            if (controller == null) return null;
            Object target = injector.get(controller);
            if (target == null) return null;
            if (!includeShortcuts) return new Snapshot(target, null, null);
            Object panelLazy = panel.get(target);
            Object keyguard = panelLazy == null ? null : lazyGet.invoke(panelLazy);
            Object shortcutLazy = keyguard == null ? null : shortcuts.get(keyguard);
            Object shortcut = shortcutLazy == null ? null : lazyGet.invoke(shortcutLazy);
            View view = shortcut == null ? null : (View) root.get(shortcut);
            return new Snapshot(target, shortcut, view);
        }
        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            Field value = owner.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        }
    }
}
