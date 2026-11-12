package com.aritxonly.myhypermodifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** QQ 9.3.75 only. Never overrides device type, fold posture, multi-window or orientation. */
final class QQFoldSplitHooks {
    private static final String FEATURE = "QQ 折叠屏 4:6 分栏";
    private static final Float LEFT = 0.4f, RIGHT = 0.6f;
    private static Float nativeLeft, nativeRight;
    private static Method stateGetter, deviceGetter, leftGetter, rightGetter;
    private static XposedModule hooker;
    private static boolean observed;
    private static boolean attempted, installed;
    private static volatile boolean ratiosActive;
    private static Field weight, aioCacheValid, aioLockField;
    private static Object aioCacheLock;
    private static QQFoldSplitWeights weights;
    private static WeakReference<Activity> owner = new WeakReference<>(null);

    private static boolean enabled() {
        return ModuleSettings.moduleHooksEnabled && ModuleSettings.qqFoldSplitRatioEnabled;
    }

    /** Called on the main queue only, and only for changes to QQ/master settings. */
    static void refresh(XposedModule module, ClassLoader loader, Activity activity) {
        refresh(module, loader, activity, true);
    }

    static void refresh(XposedModule module, ClassLoader loader, Activity activity, boolean created) {
        if (enabled() && !attempted && activity != null) install(module, loader, activity);
        if (!installed) return;
        try {
            if (!enabled()) {
                QQFoldPostureRefresh.detach();
                setActive(false);
                if (weights != null) weights.restore();
                weights = null;
                return;
            }
            if (activity != null && created) {
                QQFoldPostureRefresh.attach(activity);
                update(activity);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            ratiosActive = false;
            HookDiagnostics.failure(ModuleScopePackage.QQ, FEATURE, error);
        }
    }

    private static void install(XposedModule module, ClassLoader loader, Activity activity) {
        attempted = true;
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>(5);
        try {
            // Version/shape checks run only on first opt-in, never on a layout/getter callback.
            Application app = activity.getApplication();
            if (app == null) throw new IllegalStateException("QQ Application 尚未就绪");
            var info = app.getPackageManager().getPackageInfo(ModuleScopePackage.QQ, 0);
            if (!"9.3.75".equals(info.versionName) || info.getLongVersionCode() != 16580L) {
                throw new IllegalStateException("仅适配 QQ 9.3.75 (16580)");
            }
            if (LargeScreenDevicePolicy.detectedMode() != LargeScreenDevicePolicy.FOLDABLE) return;
            hooker = module;
            Class<?> pad = Class.forName("com.tencent.common.config.pad.PadUtil", false, loader);
            deviceGetter = pad.getDeclaredMethod("a", Context.class);
            Class<?> manager = Class.forName("com.tencent.mobileqq.pad.e", false, loader);
            stateGetter = method(manager, "a");
            weight = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", false, loader)
                    .getField("horizontalWeight");
            Class<?> aio = Class.forName("com.tencent.mobileqq.aio.utils.AIOPadCompatUtils", false, loader);
            aioCacheValid = field(aio, "g");
            aioLockField = field(aio, "h");
            if (aioCacheValid.getType() != boolean.class) {
                throw new NoSuchFieldException("QQ 聊天宽度缓存不匹配");
            }
            Method updateAioCache = method(aio, "i", boolean.class);
            Method enterFull = method(manager, "b", Activity.class);
            Method exitFull = method(manager, "c", Activity.class);
            Method configuration = method(manager, "d", android.content.res.Configuration.class,
                    Activity.class, boolean.class);
            Method orientation = method(Class.forName("com.tencent.mobileqq.pad.c", false, loader),
                    "c", Activity.class);
            Method getLeft = leftGetter = method(manager, "h"), getRight = rightGetter = method(manager, "g");
            if (getLeft.getReturnType() != float.class || getRight.getReturnType() != float.class) {
                throw new NoSuchMethodException("QQ 分栏方法签名不匹配");
            }
            // Public transitions survive native hot-fix dispatch that bypasses private e/f.
            for (Method transition : new Method[]{enterFull, exitFull, configuration}) {
                int activityIndex = transition == configuration ? 1 : 0;
                handles.add(module.hook(transition).setId("qq-fold-transition-" + transition.getName())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                            Object result = chain.proceed();
                            if (enabled() && chain.getArg(activityIndex) instanceof Activity) {
                                update((Activity) chain.getArg(activityIndex));
                            } else {
                                setActive(false);
                            }
                            return result;
                        }));
            }
            handles.add(module.hook(getLeft).setId("qq-fold-split-left")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        return ratiosActive && enabled() && nativeLeft != null && nativeLeft.equals(result) ? LEFT : result;
                    }));
            handles.add(module.hook(getRight).setId("qq-fold-split-right")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        return ratiosActive && enabled() && nativeRight != null && nativeRight.equals(result) ? RIGHT : result;
                    }));
            // The native cache recomputation calls the tiny right-ratio getter. Deoptimize
            // this single infrequent method so an AOT-inlined 0.5 read cannot bypass the hook.
            // Metadata only here: defer native static initialization until QQ actually lays out.
            module.deoptimize(updateAioCache);
            QQFoldPostureRefresh.prepare(orientation, configuration);
            installed = true;
            module.log(android.util.Log.INFO, "MyHyperModifier", "QQ fold 4:6 hooks installed");
            HookDiagnostics.available(ModuleScopePackage.QQ, FEATURE);
        } catch (Throwable error) {
            ratiosActive = false;
            for (var handle : handles) handle.unhook();
            HookDiagnostics.failure(ModuleScopePackage.QQ, FEATURE, error);
        }
    }

    static boolean isInstalled() { return installed; }

    static void onWindowEvent(Activity activity) throws ReflectiveOperationException {
        if (installed && enabled()) {
            QQFoldPostureRefresh.attach(activity);
            update(activity);
        }
    }

    static void onDestroy(Activity activity) throws IllegalAccessException {
        QQFoldPostureRefresh.detach(activity);
        if (owner.get() != activity) return;
        setActive(false);
        weights = null;
        owner.clear();
    }

    private static void update(Activity activity) throws ReflectiveOperationException {
        Object state = stateGetter.invoke(null); // Includes QQ's hot-fix redirect, unlike field a.
        Object device = deviceGetter.invoke(null, activity);
        if (!(state instanceof Enum<?>) || !"STATE_FLAT".equals(((Enum<?>) state).name())
                || !(device instanceof Enum<?>) || !"FOLD".equals(((Enum<?>) device).name())) {
            setActive(false);
            return;
        }
        apply(activity);
    }

    // These IDs belong to the version-checked QQ APK, not to this module's R table.
    @SuppressLint("ResourceType")
    private static void apply(Activity activity) throws ReflectiveOperationException {
        if (activity.isDestroyed() || activity.isFinishing()
                || !"com.tencent.mobileqq.activity.SplashActivity".equals(activity.getClass().getName())) return;
        if (owner.get() != activity) {
            weights = null;
            owner = new WeakReference<>(activity);
        }
        if (weights == null) {
            View left = activity.findViewById(0x7f0a27a2);
            View chat = activity.findViewById(0x7f0a27a1);
            View function = activity.findViewById(0x7f0a279f);
            if (left == null || chat == null || function == null
                    || !weight.getDeclaringClass().isInstance(left.getLayoutParams())
                    || !weight.getDeclaringClass().isInstance(chat.getLayoutParams())
                    || !weight.getDeclaringClass().isInstance(function.getLayoutParams())) return;
            // Capture native getter returns only after QQ created the actual equal-split views.
            if (!ratiosActive) {
                nativeLeft = (Float) leftGetter.invoke(null);
                nativeRight = (Float) rightGetter.invoke(null);
            }
            weights = new QQFoldSplitWeights(weight, left, chat, function);
        }
        setActive(weights.apply());
        if (ratiosActive && !observed) {
            observed = true;
            hooker.log(android.util.Log.INFO, "MyHyperModifier", "QQ 4:6 layout applied; native getters="
                    + nativeLeft + "/" + nativeRight);
        }
        if (!ratiosActive) {
            weights.restore();
            weights = null;
        }
    }

    private static void setActive(boolean active) throws IllegalAccessException {
        if (ratiosActive == active) return;
        ratiosActive = active;
        // QQ checks this under the same lock and lazily recomputes using its current native
        // mode/top Activity. Never replay its UI or force a computation during Activity creation.
        if (aioCacheLock == null) aioCacheLock = aioLockField.get(null);
        if (aioCacheLock == null) return;
        synchronized (aioCacheLock) {
            aioCacheValid.setBoolean(null, false);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }
}
