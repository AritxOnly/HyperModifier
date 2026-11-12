package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.List;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Verified against Authenticator 7.2/7002015. Never touches account or OTP data. */
final class AuthenticatorGridHooks {
    private static final String KEY = "authenticator_grid_enabled";
    private static final String MASTER = "module_hooks_enabled";
    private static final int MAIN_ID = 0x7f0a026d, SEARCH_ID = 0x7f0a0113;
    private static final int NAME_ID = 0x7f0a026b, CODE_ID = 0x7f0a026a;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Values must be weak too: a LayoutManager can point back to its RecyclerView.
    private static final WeakHashMap<View, WeakReference<State>> LISTS = new WeakHashMap<>();
    private static final Runnable REFRESH = AuthenticatorGridHooks::refresh;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private static boolean installed, versionChecked, supported;
    private static volatile boolean enabled;
    private static Field managerField, spanField, peerFragment, decorations;
    private static Class<?> dividerClass;
    private static Method setManager, firstVisible, scrollTo, addDecoration, removeDecoration;
    private static Constructor<?> gridConstructor;
    private static XposedModule module;

    static synchronized void install(XposedModule target, ClassLoader loader, SharedPreferences prefs)
            throws ReflectiveOperationException {
        if (installed) return;
        module = target;
        Class<?> recycler = Class.forName("android.support.v7.widget.RecyclerView", false, loader);
        Class<?> manager = Class.forName("kz", false, loader);
        Class<?> linear = Class.forName("android.support.v7.widget.LinearLayoutManager", false, loader);
        Class<?> grid = Class.forName("android.support.v7.widget.GridLayoutManager", false, loader);
        Class<?> peer = Class.forName("bwa", false, loader);
        Class<?> fragment = Class.forName("but", false, loader);
        managerField = recycler.getField("m");
        decorations = recycler.getField("o");
        dividerClass = Class.forName("jk", false, loader);
        Class<?> decoration = Class.forName("lv", false, loader);
        addDecoration = recycler.getMethod("av", decoration);
        removeDecoration = recycler.getMethod("au", decoration);
        spanField = grid.getDeclaredField("b");
        spanField.setAccessible(true);
        peerFragment = peer.getDeclaredField("c");
        peerFragment.setAccessible(true);
        setManager = recycler.getMethod("Y", manager);
        firstVisible = linear.getMethod("K");
        scrollTo = linear.getMethod("aa", int.class);
        gridConstructor = grid.getConstructor(Context.class, AttributeSet.class, int.class, int.class);
        fragmentRoot = fragment.getMethod("J");
        Method created = fragment.getDeclaredMethod("ad", View.class, Bundle.class);
        Method attached = Class.forName("com.google.android.apps.authenticator2.main.OtpView", false, loader)
                .getDeclaredMethod("onAttachedToWindow");
        Method drag = Class.forName("bvq", false, loader).getDeclaredMethod("f");
        Field dragPeer = drag.getDeclaringClass().getField("b");
        ArrayList<XposedInterface.HookHandle> handles = new ArrayList<>(3);
        try {
            handles.add(target.hook(created).setId("authenticator-grid-created")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        View root = (View) chain.getArg(0);
                        if (checkVersion(root.getContext())) {
                            bind(root.findViewById(MAIN_ID));
                            bind(root.findViewById(SEARCH_ID));
                        }
                        return result;
                    }));
            handles.add(target.hook(attached).setId("authenticator-grid-card")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (!supported || !enabled) return result;
                        View row = (View) chain.getThisObject();
                        if (row.getParent() instanceof View list) {
                            State state = state(list);
                            if (state != null && state.columns > 1) state.style(row);
                        }
                        return result;
                    }));
            handles.add(target.hook(drag).setId("authenticator-grid-drag")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        int directions = (int) chain.proceed();
                        if (!supported || !enabled || directions == 0) return directions;
                        Object host = peerFragment.get(dragPeer.get(chain.getThisObject()));
                        // Fragment.J() returns the current root view in this build.
                        View root = (View) fragmentRoot.invoke(host);
                        State state = root == null ? null : state(root.findViewById(MAIN_ID));
                        return AuthenticatorGridPolicy.dragDirections(directions, state != null && state.columns > 1);
                    }));
            preferences = prefs;
            preferenceListener = (values, key) -> {
                if (key != null && !KEY.equals(key) && !MASTER.equals(key)) return;
                MAIN.removeCallbacks(REFRESH);
                MAIN.post(REFRESH);
            };
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
            installed = true;
            MAIN.post(REFRESH);
        } catch (Throwable error) {
            for (var handle : handles) handle.unhook();
            throw error;
        }
    }
    private static Method fragmentRoot;

    private static boolean checkVersion(Context context) {
        if (!versionChecked) {
            versionChecked = true;
            try {
                supported = context.getPackageManager().getPackageInfo(ModuleScopePackage.AUTHENTICATOR, 0)
                        .getLongVersionCode() == 7002015;
            } catch (Exception error) { report(error); }
            if (!supported) module.log(Log.WARN, "MyHyperModifier", "Authenticator grid requires 7.2/7002015");
        }
        return supported;
    }

    private static void refresh() {
        boolean next = preferences.getBoolean(MASTER, true) && preferences.getBoolean(KEY, false);
        if (next == enabled) return;
        enabled = next;
        for (var ref : new ArrayList<>(LISTS.values())) {
            State state = ref.get();
            if (state != null) state.run();
        }
    }

    private static State state(View list) {
        WeakReference<State> ref = LISTS.get(list);
        return ref == null ? null : ref.get();
    }

    private static void bind(View list) {
        if (list == null || state(list) != null) return;
        State state = new State((ViewGroup) list);
        LISTS.put(list, new WeakReference<>(state));
        list.addOnLayoutChangeListener(state); // View owns state; no static strong path back to View.
        state.run();
    }

    private static void report(Throwable error) {
        if (module != null) module.log(Log.WARN, "MyHyperModifier", "Authenticator grid adaptation unavailable", error);
        else Log.w("MyHyperModifier", "Authenticator grid adaptation unavailable", error);
    }

    private static final class State implements View.OnLayoutChangeListener, Runnable {
        final WeakReference<ViewGroup> list;
        final WeakHashMap<View, RowStyle> rows = new WeakHashMap<>();
        Object original, grid, divider;
        int columns = 1, dividerIndex;
        boolean blocked;
        State(ViewGroup view) { list = new WeakReference<>(view); }
        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            // No allocations/reflection on the steady-state layout path.
            int desired = columns((ViewGroup) v);
            if (!blocked && desired != columns) {
                MAIN.removeCallbacks(this);
                MAIN.post(this);
            }
        }
        private int columns(ViewGroup view) {
            return AuthenticatorGridPolicy.columns(enabled,
                    view.getWidth() - view.getPaddingLeft() - view.getPaddingRight(),
                    view.getResources().getDisplayMetrics().density);
        }
        @Override public void run() {
            ViewGroup view = list.get();
            if (view == null || blocked) return;
            int desired = columns(view);
            if (desired == columns) return;
            Object attempted = null;
            try {
                Object current = managerField.get(view);
                // Respect another module/application replacing our manager.
                if (grid != null && current != grid) { restoreDivider(view); restoreRows(); blocked = true; return; }
                if (grid == null) original = current;
                if (original == null || !firstVisible.getDeclaringClass().isInstance(current)) return;
                int anchor = (int) firstVisible.invoke(current);
                Object next = original;
                if (desired > 1) {
                    next = gridConstructor.newInstance(view.getContext(), null, 0, 0);
                    // R8 retained only the XML constructor. Initialize fresh manager before attaching;
                    // there are no populated span caches to invalidate at this point.
                    spanField.setInt(next, desired);
                }
                if (desired > 1 && divider == null) {
                    List<?> items = (List<?>) decorations.get(view);
                    for (int i = 0; i < items.size(); i++) {
                        Object item = items.get(i);
                        if (item.getClass() == dividerClass) { divider = item; dividerIndex = i; break; }
                    }
                    if (divider != null) removeDecoration.invoke(view, divider);
                }
                attempted = next;
                setManager.invoke(view, next);
                grid = desired > 1 ? next : null;
                columns = desired;
                if (anchor >= 0) scrollTo.invoke(next, anchor);
                if (desired == 1) { restoreDivider(view); restoreRows(); original = null; }
                else for (int i = 0; i < view.getChildCount(); i++) style(view.getChildAt(i));
            } catch (ReflectiveOperationException | RuntimeException error) {
                // Best-effort rollback if a vendor/app change fails after manager attachment.
                try {
                    Object current = managerField.get(view);
                    if (original != null && (current == attempted || current == grid))
                        setManager.invoke(view, original);
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
                try { restoreDivider(view); } catch (ReflectiveOperationException ignored) {}
                restoreRows();
                grid = null; original = null; columns = 1; blocked = true;
                report(error);
            }
        }
        @SuppressWarnings("unchecked")
        void restoreDivider(ViewGroup view) throws ReflectiveOperationException {
            if (divider != null) {
                List<Object> items = (List<Object>) decorations.get(view);
                if (!items.contains(divider)) {
                    addDecoration.invoke(view, divider); // Native invalidation and willNotDraw bookkeeping.
                    items.remove(divider);
                    items.add(Math.min(dividerIndex, items.size()), divider);
                }
                divider = null;
            }
        }
        void style(View row) {
            if (rows.containsKey(row)) return;
            TextView name = row.findViewById(NAME_ID), code = row.findViewById(CODE_ID);
            if (name == null || code == null) return;
            RowStyle style = new RowStyle(name, code);
            rows.put(row, style);
            style.apply(name, code);
        }
        void restoreRows() {
            for (var entry : rows.entrySet()) {
                View row = entry.getKey();
                TextView name = row.findViewById(NAME_ID), code = row.findViewById(CODE_ID);
                if (name != null && code != null) entry.getValue().restore(name, code);
            }
            rows.clear();
        }
    }

    static final class RowStyle {
        final RelativeLayout.LayoutParams nameParams, codeParams;
        final float size;
        RelativeLayout.LayoutParams appliedName, appliedCode;
        float appliedSize;
        RowStyle(TextView name, TextView code) {
            nameParams = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) name.getLayoutParams());
            codeParams = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) code.getLayoutParams());
            size = code.getTextSize();
        }
        void apply(TextView name, TextView code) {
            float density = code.getResources().getDisplayMetrics().density;
            RelativeLayout.LayoutParams n = new RelativeLayout.LayoutParams(nameParams);
            RelativeLayout.LayoutParams c = new RelativeLayout.LayoutParams(codeParams);
            n.width = c.width = ViewGroup.LayoutParams.MATCH_PARENT;
            n.setMarginStart(Math.round(12 * density));
            n.setMarginEnd(Math.round(12 * density));
            c.setMarginStart(Math.round(12 * density));
            c.setMarginEnd(Math.round(52 * density)); // Preserve countdown/HOTP button and hit area.
            appliedName = n; appliedCode = c;
            appliedSize = Math.min(size, 28 * density);
            name.setLayoutParams(n);
            code.setLayoutParams(c);
            code.setTextSize(TypedValue.COMPLEX_UNIT_PX, appliedSize);
        }
        void restore(TextView name, TextView code) {
            if (name.getLayoutParams() == appliedName)
                name.setLayoutParams(new RelativeLayout.LayoutParams(nameParams));
            if (code.getLayoutParams() == appliedCode)
                code.setLayoutParams(new RelativeLayout.LayoutParams(codeParams));
            if (code.getTextSize() == appliedSize) code.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }
}
