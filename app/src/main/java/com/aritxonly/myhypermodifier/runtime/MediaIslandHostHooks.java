package com.aritxonly.myhypermodifier;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Aligns media geometry and reserves bottom space inside expanded Super Island content. */
final class MediaIslandHostHooks {
    static final String CONTENT_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView";
    static final String EXPANDED_CLASS =
            "miui.systemui.dynamicisland.view.DynamicIslandExpandedView";
    private static final String PLAYER_CLASS =
            "com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout";
    private static final String TAG = "MyHyperModifier";
    private static final Set<Method> INSTALLED = ConcurrentHashMap.newKeySet();
    private static final Map<View, Integer> ORIGINAL_BOTTOM_PADDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, PullBarState> ORIGINAL_PULL_BARS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class PullBarState {
        final Drawable background;
        final int width;
        final int height;
        final float alpha;

        PullBarState(View bar) {
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            background = bar.getBackground();
            width = params == null ? 0 : params.width;
            height = params == null ? 0 : params.height;
            alpha = bar.getAlpha();
        }
    }

    static void install(XposedModule module, ClassLoader loader) {
        try {
            installLoaded(module, Class.forName(CONTENT_CLASS, false, loader));
        } catch (Throwable error) {
            Log.w(TAG, "Super Island host geometry hook unavailable", error);
        }
        try {
            installExpandedLoaded(module, Class.forName(EXPANDED_CLASS, false, loader));
        } catch (Throwable error) {
            Log.w(TAG, "Super Island content padding hook unavailable", error);
        }
    }

    static void installLoaded(XposedModule module, Class<?> type) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if ("setMiniBar".equals(method.getName())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == View.class
                    && INSTALLED.add(method)) {
                try {
                    module.hook(method)
                            .setId("super-island-pull-bar-init")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                ModuleSettings.ensureLoaded();
                                updatePullBar((View) chain.getArg(0));
                                return result;
                            });
                } catch (Throwable error) {
                    INSTALLED.remove(method);
                    throw error;
                }
            }
            if ("updateMiniBar".equals(method.getName())
                    && method.getParameterCount() == 1
                    && INSTALLED.add(method)) {
                try {
                    Method getter = type.getDeclaredMethod("getMiniBar");
                    getter.setAccessible(true);
                    module.hook(method)
                            .setId("super-island-pull-bar-visibility")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                ModuleSettings.ensureLoaded();
                                updatePullBar((View) getter.invoke(chain.getThisObject()));
                                return result;
                            });
                } catch (Throwable error) {
                    INSTALLED.remove(method);
                    throw error;
                }
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!"updateExpandedSize".equals(method.getName()) || parameters.length != 3
                    || parameters[0] != int.class || parameters[1] != int.class
                    || !"com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"
                    .equals(parameters[2].getName())) continue;
            // These fields are resolved once, outside the callback. Do not silently continue
            // with a broken height cap on a different plugin revision.
            Field maximum = type.getDeclaredField("expandedViewMaxHeight");
            Field actual = type.getDeclaredField("expandedViewHeight");
            maximum.setAccessible(true);
            actual.setAccessible(true);
            if (!INSTALLED.add(method)) continue;
            try {
                module.hook(method)
                        .setId("super-island-plugin-expanded-size")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object data = chain.getArg(2);
                            ModuleSettings.ensureLoaded();
                            View content = contentView(data);
                            if (content == null) return chain.proceed();
                            View player = findPlayer(content, 0);
                            int mediaHeight = player == null ? 0
                                    : MediaConstraintCustomizer.requestedMediaIslandHeight(
                                            player.getContext());
                            int margin = contentMarginPx(content);
                            Object host = chain.getThisObject();
                            if (mediaHeight <= 0 && margin == 0) {
                                Object result = chain.proceed();
                                applyBottomPadding(expandedView(host), 0);
                                return result;
                            }
                            int stockMaximum = maximum.getInt(host);
                            int previous = actual.getInt(host);
                            int requested = mediaHeight > 0 ? mediaHeight : (Integer) chain.getArg(1);
                            int minimum = minimumExpandedHeight(content);
                            int baseHeight = SuperIslandContentMargin.contentHeight(
                                    requested, minimum, stockMaximum,
                                    mediaHeight > 0 || isPromoted(data));
                            int hostHeight = SuperIslandContentMargin.hostHeight(baseHeight, margin);
                            // The stock cap is shared by media and other events. Widen it only
                            // for this call so the reserved space is not clipped away.
                            maximum.setInt(host, Math.max(stockMaximum, hostHeight));
                            Object result;
                            try {
                                result = chain.proceed(new Object[]{chain.getArg(0), hostHeight, data});
                            } finally {
                                maximum.setInt(host, stockMaximum);
                            }
                            int applied = actual.getInt(host);
                            if (margin > 0) {
                                ViewGroup.LayoutParams params = content.getLayoutParams();
                                if (params != null && params.height != baseHeight) {
                                    params.height = baseHeight;
                                    content.setLayoutParams(params);
                                }
                            }
                            applyBottomPadding(expandedView(host), margin);
                            if (player != null && mediaHeight > 0 && previous != applied) {
                                Log.i(TAG, "Media island host height: " + previous + " -> "
                                        + applied + ", requested=" + hostHeight
                                        + ", player=" + player.getHeight());
                            }
                            return result;
                        });
                Log.i(TAG, "Installed Super Island host geometry hook");
            } catch (Throwable error) {
                INSTALLED.remove(method);
                throw error;
            }
        }
    }

    private static void updatePullBar(View bar) {
        if (bar == null) return;
        PullBarState original = ORIGINAL_PULL_BARS.get(bar);
        if (ModuleSettings.superIslandHidePullBar) {
            if (original == null) {
                original = new PullBarState(bar);
                ORIGINAL_PULL_BARS.put(bar, original);
                Log.i(TAG, "Hiding Super Island pull bar");
            }
            // HyperOS makes this View visible again during island state changes. Removing its
            // background and geometry also keeps it invisible during those animations.
            bar.setVisibility(View.GONE);
            bar.setBackground(null);
            bar.setAlpha(0f);
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            if (params != null && (params.width != 0 || params.height != 0)) {
                params.width = 0;
                params.height = 0;
                bar.setLayoutParams(params);
            }
        } else if (original != null) {
            ORIGINAL_PULL_BARS.remove(bar);
            bar.setBackground(original.background);
            bar.setAlpha(original.alpha);
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            if (params != null) {
                params.width = original.width;
                params.height = original.height;
                bar.setLayoutParams(params);
            }
        }
    }

    static void installExpandedLoaded(XposedModule module, Class<?> type) throws Exception {
        for (Method method : type.getDeclaredMethods()) {
            if (!method.getName().startsWith("setContentView$")
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != View.class
                    || !INSTALLED.add(method)) continue;
            try {
                module.hook(method)
                        .setId("super-island-content-bottom-padding")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            View expanded = (View) chain.getThisObject();
                            ModuleSettings.ensureLoaded();
                            applyBottomPadding(expanded, contentMarginPx(expanded));
                            return result;
                        });
            } catch (Throwable error) {
                INSTALLED.remove(method);
                throw error;
            }
        }
    }

    private static View contentView(Object data) {
        if (data == null) return null;
        try {
            Object view = data.getClass().getMethod("getView").invoke(data);
            return view instanceof View ? (View) view : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isPromoted(Object data) {
        try {
            Object extras = data.getClass().getMethod("getExtras").invoke(data);
            return extras instanceof Bundle && ((Bundle) extras).getBoolean("miui.focus.isPromoted");
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static int minimumExpandedHeight(View view) {
        int id = view.getResources().getIdentifier(
                "expanded_min_height", "dimen", "miui.systemui.plugin");
        return id == 0 ? 0 : view.getResources().getDimensionPixelSize(id);
    }

    private static int contentMarginPx(View view) {
        if (!ModuleSettings.superIslandContentBottomMarginEnabled) return 0;
        return Math.round(ModuleSettings.superIslandContentBottomMarginDp
                * view.getResources().getDisplayMetrics().density);
    }

    private static View expandedView(Object host) {
        try {
            Object value = host.getClass().getMethod("getExpandedView").invoke(host);
            return value instanceof View ? (View) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void applyBottomPadding(View expanded, int margin) {
        if (expanded == null) return;
        try {
            Object value = expanded.getClass().getMethod("getMContainer").invoke(expanded);
            if (!(value instanceof View)) return;
            View container = (View) value;
            Integer original = ORIGINAL_BOTTOM_PADDING.get(container);
            if (margin > 0) {
                if (original == null) {
                    original = container.getPaddingBottom();
                    ORIGINAL_BOTTOM_PADDING.put(container, original);
                }
                int desired = original + margin;
                if (container.getPaddingBottom() != desired) {
                    container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                            container.getPaddingRight(), desired);
                }
            } else if (original != null) {
                ORIGINAL_BOTTOM_PADDING.remove(container);
                if (container.getPaddingBottom() != original) {
                    container.setPadding(container.getPaddingLeft(), container.getPaddingTop(),
                            container.getPaddingRight(), original);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "Super Island content padding unavailable", error);
        }
    }

    private static View findPlayer(View view, int depth) {
        if (depth > 12) return null;
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass()) {
            if (PLAYER_CLASS.equals(type.getName())) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View player = findPlayer(group.getChildAt(index), depth + 1);
                if (player != null) return player;
            }
        }
        return null;
    }
}
