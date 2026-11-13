package com.aritxonly.myhypermodifier;

import android.graphics.Canvas;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

import static com.aritxonly.myhypermodifier.ModuleSettings.*;

/**
 * 横屏应用状态栏 / 导航栏的半透明灰色遮罩。
 *
 * <p>HyperOS 的系统栏背景不是普通 View 背景，而是 {@code BarTransitions} 的
 * {@code BarBackgroundDrawable} 按 {@code mMode} 绘制：半透明态（1 = TRANSLUCENT、
 * 2 = SEMI_TRANSPARENT）用它从主题读到的 {@code #66000000}
 * （{@code status_bar_background_semi_transparent}）铺一层 40% 黑。
 * 横屏看视频 / 玩游戏时屏幕上沿、下沿那两条灰带就是它。
 *
 * <p>因为状态栏和导航栏共用同一个 drawable 实现、又合并成同一个开关，
 * 这里不区分归属：半透明态一律跳过绘制。只跳半透明态，
 * 不透明态（控制中心、锁屏）与纯透明态保持原生。
 *
 * <p>注意：不要依赖「构造时登记归属」的做法。SystemUI 的 bar 实例可能在
 * 本 Hook 安装前就创建好了（延迟安装路径），那样后建的能生效、先建的不能，
 * 表现就是「状态栏好了、小白条还在」。
 */
final class BarScrimHooks {

    static final String KEY = "bar_scrim_removed";

    private static final int MODE_TRANSLUCENT = 1;
    private static final int MODE_SEMI_TRANSPARENT = 2;

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean APPLIED_LOGGED = new AtomicBoolean();

    private BarScrimHooks() {
    }

    static void install(XposedModule module, ClassLoader loader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> drawable = Class.forName(
                    "com.android.systemui.shared.statusbar.phone.BarTransitions$BarBackgroundDrawable",
                    false, loader);
            Field mode = drawable.getDeclaredField("mMode");
            mode.setAccessible(true);
            Method draw = drawable.getDeclaredMethod("draw", Canvas.class);

            module.hook(draw)
                    .setId("bar-scrim-draw")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        ensureLoaded();
                        if (!barScrimRemoved) return chain.proceed();
                        int current = mode.getInt(chain.getThisObject());
                        if (current != MODE_TRANSLUCENT && current != MODE_SEMI_TRANSPARENT) {
                            return chain.proceed();
                        }
                        if (APPLIED_LOGGED.compareAndSet(false, true)) {
                            Log.i("MyHyperModifier", "Bar scrim removed (mode=" + current + ")");
                        }
                        return null;
                    });

            module.deoptimize(draw);
            HookDiagnostics.available("com.android.systemui", "横屏状态栏及小白条遮罩");
        } catch (Throwable error) {
            INSTALLED.set(false);
            logOnce(error);
            HookDiagnostics.failure("com.android.systemui", "横屏状态栏及小白条遮罩", error);
        }
    }

    private static void logOnce(Throwable error) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            Log.w("MyHyperModifier", "Bar scrim hook unavailable", error);
        }
    }
}
