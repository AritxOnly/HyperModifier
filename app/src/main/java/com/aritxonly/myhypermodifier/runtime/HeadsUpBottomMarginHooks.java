package com.aritxonly.myhypermodifier;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Changes template bottom spacing only while a row qualifies for the mini-window heads-up bar. */
final class HeadsUpBottomMarginHooks {
    private static final String TAG = "MyHyperModifier";
    private static final String ROW_INJECTOR =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector";
    private static final String NOTIFICATION_CONTENT_VIEW =
            "com.android.systemui.statusbar.notification.row.NotificationContentView";
    private static final String NOTIFICATION_ROW =
            "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean REFRESH_REGISTERED = new AtomicBoolean();
    private static final Map<ViewGroup, Boolean> TRACKED_ROWS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> ORIGINAL_MARGINS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, MinHeightState> ORIGINAL_MIN_HEIGHTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<ViewGroup> ADJUSTED_ROWS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static Field contractedChildField;
    private static Field minContractedHeightField;

    private HeadsUpBottomMarginHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> injector = Class.forName(ROW_INJECTOR, false, classLoader);
            Field miniBarVisible = injector.getField("miniBarVisible");
            Method getMiniBar = injector.getDeclaredMethod("getMiniBar");
            Method updateMiniWindowBar = injector.getDeclaredMethod("updateMiniWindowBar");
            module.hook(updateMiniWindowBar)
                    .setId("heads-up-content-bottom-margin")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            ModuleSettings.ensureLoaded();
                            Object bar = getMiniBar.invoke(chain.getThisObject());
                            ViewParent parent = bar instanceof View ? ((View) bar).getParent() : null;
                            if (parent instanceof ViewGroup) {
                                ViewGroup row = (ViewGroup) parent;
                                boolean visible = miniBarVisible.getBoolean(chain.getThisObject());
                                TRACKED_ROWS.put(row, visible);
                                applyRow(row, visible);
                            }
                        } catch (Throwable ignored) {
                            // A changed SystemUI row layout must retain its original spacing.
                        }
                        return result;
                    });
            installLayoutRefreshHooks(module, classLoader);
            if (REFRESH_REGISTERED.compareAndSet(false, true)) {
                ModuleSettings.onLoaded(HeadsUpBottomMarginHooks::refreshTrackedRows);
            }
        } catch (Throwable throwable) {
            INSTALLED.set(false);
            module.log(Log.WARN, TAG, "Heads-up bottom margin hook unavailable", throwable);
        }
    }

    private static void installLayoutRefreshHooks(XposedModule module, ClassLoader classLoader) {
        try {
            Class<?> contentClass = Class.forName(NOTIFICATION_CONTENT_VIEW, false, classLoader);
            contractedChildField = contentClass.getField("mContractedChild");
            minContractedHeightField = contentClass.getField("mMinContractedHeight");
            Method onMeasure = contentClass.getDeclaredMethod(
                    "onMeasure", int.class, int.class);
            module.hook(onMeasure)
                    .setId("heads-up-bottom-margin-before-measure")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object receiver = chain.getThisObject();
                            if (receiver instanceof ViewGroup) {
                                ViewGroup content = (ViewGroup) receiver;
                                ViewParent parent = content.getParent();
                                if (parent instanceof ViewGroup) {
                                    ViewGroup row = (ViewGroup) parent;
                                    Boolean visible = TRACKED_ROWS.get(row);
                                    if (visible != null && (visible || ADJUSTED_ROWS.contains(row))) {
                                        applyContentView(content,
                                                visible && ModuleSettings.headsUpBottomMarginEnabled);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            // Layout measurement must proceed on unsupported notification rows.
                        }
                        return chain.proceed();
                    });
            Class<?> rowClass = Class.forName(NOTIFICATION_ROW, false, classLoader);
            Method onAppearFinished = rowClass.getDeclaredMethod(
                    "onAppearAnimationFinished", boolean.class, boolean.class);
            module.hook(onAppearFinished)
                    .setId("heads-up-bottom-margin-after-appearance")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object receiver = chain.getThisObject();
                            if (receiver instanceof ViewGroup) {
                                ViewGroup row = (ViewGroup) receiver;
                                Boolean visible = TRACKED_ROWS.get(row);
                                if (visible != null) applyRow(row, visible);
                            }
                        } catch (Throwable ignored) {
                            // Keep the original heads-up appearance completion path intact.
                        }
                        return result;
                    });
        } catch (Throwable throwable) {
            module.log(Log.WARN, TAG, "Heads-up layout refresh hook unavailable", throwable);
        }
    }

    private static void refreshTrackedRows() {
        ArrayList<ViewGroup> rows;
        synchronized (TRACKED_ROWS) {
            rows = new ArrayList<>(TRACKED_ROWS.keySet());
        }
        for (ViewGroup row : rows) {
            if (row == null) continue;
            row.post(() -> {
                Boolean visible = TRACKED_ROWS.get(row);
                if (visible != null) applyRow(row, visible);
            });
        }
    }

    private static void applyRow(ViewGroup row, boolean miniBarVisible) {
        boolean customize = miniBarVisible && ModuleSettings.headsUpBottomMarginEnabled;
        if (!customize && !ADJUSTED_ROWS.contains(row)) return;
        for (int index = 0; index < row.getChildCount(); index++) {
            View child = row.getChildAt(index);
            if (NOTIFICATION_CONTENT_VIEW.equals(child.getClass().getName())) {
                applyContentView((ViewGroup) child, customize);
            }
        }
        if (customize) ADJUSTED_ROWS.add(row);
        else ADJUSTED_ROWS.remove(row);
    }

    private static void applyContentView(ViewGroup content, boolean customize) {
        Resources resources = content.getResources();
        int contentId = resources.getIdentifier(
                "row_icon_and_main", "id", SYSTEM_UI_PACKAGE);
        int actionTargetId = resources.getIdentifier(
                "notification_action_list_margin_target", "id", SYSTEM_UI_PACKAGE);
        if (contentId == 0) return;
        int marginPx = Math.round(ModuleSettings.headsUpBottomMarginDp
                * resources.getDisplayMetrics().density);
        adjustMinimumHeight(content, contentId, actionTargetId, customize, marginPx);
        adjustDescendants(content, contentId, actionTargetId, customize, marginPx);
    }

    private static void adjustMinimumHeight(ViewGroup content, int contentId, int actionTargetId,
            boolean customize, int marginPx) {
        if (contractedChildField == null || minContractedHeightField == null) return;
        try {
            MinHeightState state = ORIGINAL_MIN_HEIGHTS.get(content);
            if (customize) {
                Object contractedChild = contractedChildField.get(content);
                View target = contractedChild instanceof View
                        ? findMarginTarget((View) contractedChild, contentId, actionTargetId)
                        : null;
                if (target == null) {
                    restoreMinimumHeight(content, state);
                    return;
                }
                ViewGroup.LayoutParams params = target.getLayoutParams();
                if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                    restoreMinimumHeight(content, state);
                    return;
                }
                Integer savedMargin = ORIGINAL_MARGINS.get(target);
                int originalMargin = savedMargin != null ? savedMargin
                        : ((ViewGroup.MarginLayoutParams) params).bottomMargin;
                if (originalMargin <= 0) {
                    restoreMinimumHeight(content, state);
                    return;
                }
                int current = minContractedHeightField.getInt(content);
                if (state == null) {
                    state = new MinHeightState(current);
                    ORIGINAL_MIN_HEIGHTS.put(content, state);
                } else if (current != state.applied) {
                    state.original = current;
                }
                int desired = Math.max(0, state.original + marginPx - originalMargin);
                if (current != desired) minContractedHeightField.setInt(content, desired);
                state.applied = desired;
            } else if (state != null) {
                restoreMinimumHeight(content, state);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unknown content variants keep their original minimum height.
        }
    }

    private static void restoreMinimumHeight(ViewGroup content, MinHeightState state)
            throws IllegalAccessException {
        if (state == null) return;
        minContractedHeightField.setInt(content, state.original);
        ORIGINAL_MIN_HEIGHTS.remove(content);
    }

    private static View findMarginTarget(View view, int contentId, int actionTargetId) {
        if (view.getId() == contentId && hasTemplateBottomMargin(view)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (view.getId() == actionTargetId && group.getChildCount() > 0) {
            View first = group.getChildAt(0);
            if (first.getId() != contentId && hasTemplateBottomMargin(first)) return first;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            View target = findMarginTarget(group.getChildAt(index), contentId, actionTargetId);
            if (target != null) return target;
        }
        return null;
    }

    private static boolean hasTemplateBottomMargin(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams
                && (((ViewGroup.MarginLayoutParams) params).bottomMargin > 0
                || ORIGINAL_MARGINS.containsKey(view));
    }

    private static void adjustDescendants(View view, int contentId, int actionTargetId,
            boolean customize, int marginPx) {
        if (view.getId() == contentId) {
            adjustMargin(view, customize, marginPx);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        if (view.getId() == actionTargetId && group.getChildCount() > 0) {
            View first = group.getChildAt(0);
            if (first.getId() != contentId) adjustMargin(first, customize, marginPx);
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            adjustDescendants(group.getChildAt(index), contentId, actionTargetId,
                    customize, marginPx);
        }
    }

    private static void adjustMargin(View target, boolean customize, int marginPx) {
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        Integer original = ORIGINAL_MARGINS.get(target);
        if (customize) {
            if (original == null) {
                if (margins.bottomMargin <= 0) return;
                ORIGINAL_MARGINS.put(target, margins.bottomMargin);
            }
            if (margins.bottomMargin != marginPx) {
                margins.bottomMargin = marginPx;
                target.setLayoutParams(margins);
            }
        } else if (original != null) {
            if (margins.bottomMargin != original) {
                margins.bottomMargin = original;
                target.setLayoutParams(margins);
            }
            ORIGINAL_MARGINS.remove(target);
        }
    }

    private static final class MinHeightState {
        int original;
        int applied;

        MinHeightState(int original) {
            this.original = original;
            this.applied = original;
        }
    }
}
