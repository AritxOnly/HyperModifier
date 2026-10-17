package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.View;
import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Supplied 40.7.0: use the player's own texture wrapper within feed initialization only. */
final class DouyinPlayerTextureHooks {
    private static final ThreadLocal<Integer> FEED = ThreadLocal.withInitial(() -> 0);
    private static boolean reported;
    private static final Set<TextureView> TEXTURES = Collections.newSetFromMap(new WeakHashMap<>());
    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        // TextureView.mUpdateListener calls updateLayer() for every decoder frame.
        // updateLayerAndInvalidate() covers opacity/visibility changes, not the steady video path.
        module.hook(TextureView.class.getDeclaredMethod("updateLayer"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    TextureView texture = (TextureView) chain.getThisObject();
                    if (TEXTURES.contains(texture)) NativeGpuBackdrop.onNativeTextureFrame(texture);
                    return result;
                });
        Class<?> feed = Class.forName("com.ss.android.ugc.aweme.feed.ui.FeedVideoPlayerView", false, loader);
        Field parent = feed.getDeclaredField("a");
        parent.setAccessible(true);
        Class<?> factory = Class.forName("X.0GO1", false, loader);
        Method create = factoryMethod(factory);
        Method output = outputMethod(factory);
        module.hook(feed.getDeclaredMethod("LJJIJIIJIL"))
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.douyinFloatingNavigationEnabled)
                        return chain.proceed();
                    ViewGroup group = (ViewGroup) parent.get(chain.getThisObject());
                    if (group == null || !homepage(group.getContext())) return chain.proceed();
                    int depth = FEED.get();
                    FEED.set(depth + 1);
                    try { return chain.proceed(); }
                    finally { if (depth == 0) FEED.remove(); else FEED.set(depth); }
                });
        module.hook(create).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
            if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.douyinFloatingNavigationEnabled || FEED.get() == 0)
                return chain.proceed();
            // Verified false branch wraps KeepSurfaceTextureView; preserve native ownership.
            Object result = chain.proceed(new Object[] {chain.getArg(0), false});
            Object view = result == null ? null : output.invoke(result);
            if (view instanceof TextureView) {
                TextureView texture = (TextureView) view;
                texture.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View view) { TEXTURES.add((TextureView) view); }
                    public void onViewDetachedFromWindow(View view) { TEXTURES.remove((TextureView) view); }
                });
                if (texture.isAttachedToWindow()) TEXTURES.add(texture);
                if (!reported) {
                    reported = true;
                    android.util.Log.w("MyHyperModifier", "Douyin native feed texture selected (40.7.0)");
                }
            }
            return result;
        });
    }
    private static Method factoryMethod(Class<?> type) throws NoSuchMethodException {
        return type.getDeclaredMethod("LJIILIIL", ViewGroup.class, boolean.class);
    }
    private static Method outputMethod(Class<?> type) throws NoSuchMethodException { return type.getMethod("getView"); }
    private static boolean homepage(Context context) {
        for (int i = 0; context != null && i < 12; i++) {
            String name = context.getClass().getName();
            if (name.equals("com.ss.android.ugc.aweme.main.MainActivity") || name.equals("com.ss.android.ugc.aweme.splash.SplashActivity")) return true;
            if (!(context instanceof ContextWrapper)) return false;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return false;
            context = next;
        }
        return false;
    }
}
