package com.aritxonly.myhypermodifier;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import org.xmlpull.v1.XmlPullParser;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Provider-side background adaptation; never change text, clicks or arbitrary content images. */
final class AndroidWidgetGlassHooks {
    private static final String FEATURE = "安卓小组件玻璃背景";
    private static String targetPackage;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private static int logged;
    private static volatile WidgetGlassStyles.Rules selection;
    private static final java.util.Map<Class<?>, java.util.Map<String, Field>> FIELDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> PASSTHROUGH = ThreadLocal.withInitial(() -> false);

    private AndroidWidgetGlassHooks() {}

    static void install(XposedModule module, String packageName) throws Throwable {
        targetPackage = packageName;
        if (!INSTALLED.compareAndSet(false, true)) return;
        int installed = 0;
        for (Method method : AppWidgetManager.class.getDeclaredMethods()) {
            if (!method.getName().equals("updateAppWidget") && !method.getName().equals("partiallyUpdateAppWidget")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 2 || types[1] != RemoteViews.class
                    || (types[0] != int[].class && types[0] != ComponentName.class)) continue;
            module.hook(method).setId("android-widget-glass-" + method.toGenericString())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (PASSTHROUGH.get()) return chain.proceed();
                        ModuleSettings.ensureLoaded();
                        if (!ModuleSettings.moduleHooksEnabled || !ModuleSettings.androidWidgetGlassEnabled)
                            return chain.proceed();
                        WidgetGlassStyles.Rules rules = selectionRules();
                        Object target = chain.getArg(0);
                        AppWidgetManager manager = (AppWidgetManager) chain.getThisObject();
                        // ComponentName updates must split IDs too: instances may choose different materials.
                        int[] ids = target instanceof ComponentName ? manager.getAppWidgetIds((ComponentName) target) : (int[]) target;
                        if (ids == null || ids.length == 0) return chain.proceed();
                        // Default-all without OFF overrides needs no per-instance metadata IPC.
                        boolean allSelected = rules.glass.size() == 1 && rules.glass.contains("*")
                                && !rules.styles.containsValue(WidgetGlassStyles.OFF);
                        int[] chosen = allSelected ? ids : Arrays.stream(ids).filter(id -> {
                            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
                            return info != null && selected(info.provider, id, rules);
                        }).toArray();
                        if (chosen.length == 0) return chain.proceed();
                        int[] untouched = allSelected ? new int[0] : Arrays.stream(ids).filter(id -> Arrays.stream(chosen).noneMatch(chosenId -> chosenId == id)).toArray();
                        Object selectedTarget = chosen;
                        RemoteViews original = (RemoteViews) chain.getArg(1);
                        if (original == null) return chain.proceed();
                        RemoteViews copy;
                        try {
                            copy = original.clone();
                            int leaves = hideBackground(copy, Collections.newSetFromMap(new IdentityHashMap<>()), true);
                            if (logged++ < 6) Log.i("MHMDesktopWidget", "BACKGROUND package=" + targetPackage + " layers=" + leaves);
                        } catch (Exception error) {
                            Log.w("MHMDesktopWidget", "BACKGROUND unsupported RemoteViews; keeping provider output", error);
                            return chain.proceed();
                        }
                        // A mixed provider batch keeps the original output for unselected IDs.
                        if (untouched.length > 0) {
                            PASSTHROUGH.set(true);
                            try {
                                if (method.getName().equals("partiallyUpdateAppWidget")) manager.partiallyUpdateAppWidget(untouched, original);
                                else manager.updateAppWidget(untouched, original);
                            } finally { PASSTHROUGH.remove(); }
                        }
                        if (target instanceof ComponentName) {
                            PASSTHROUGH.set(true);
                            try {
                                if (method.getName().equals("partiallyUpdateAppWidget")) manager.partiallyUpdateAppWidget(chosen, copy);
                                else manager.updateAppWidget(chosen, copy);
                            } finally { PASSTHROUGH.remove(); }
                            return null;
                        }
                        return chain.proceed(new Object[]{selectedTarget, copy});
                    });
            installed++;
        }
        if (installed == 0) throw new NoSuchMethodException("AppWidgetManager RemoteViews update methods");
        SharedPreferences preferences = module.getRemotePreferences("modifier_settings");
        listener = (prefs, key) -> {
            if ("deadliner_widget_glass_enabled".equals(key) || AndroidWidgetGlassPolicy.KEY.equals(key) || WidgetGlassStyles.KEY.equals(key) || "module_hooks_enabled".equals(key)) {
                MAIN.removeCallbacks(REFRESH);
                MAIN.postDelayed(REFRESH, 500);
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        HookDiagnostics.available(packageName, FEATURE);
    }

    private static WidgetGlassStyles.Rules selectionRules() {
        String styles = ModuleSettings.androidWidgetGlassStyles;
        String glass = ModuleSettings.androidWidgetGlassTargets;
        String clock = ModuleSettings.androidWidgetClockGlassTargets;
        WidgetGlassStyles.Rules current = selection;
        if (current == null || !current.matchesSources(styles, glass, clock)) {
            current = new WidgetGlassStyles.Rules(styles, glass, clock);
            selection = current;
        }
        return current;
    }

    private static boolean selected(ComponentName provider, int id, WidgetGlassStyles.Rules rules) {
        return WidgetGlassStyles.resolve(rules.styles, rules.glass,
                ModuleSettings.androidWidgetClockGlassEnabled, rules.clock,
                provider.getPackageName(), provider.getClassName(), id) != WidgetGlassStyles.OFF;
    }

    private static Application application() throws ReflectiveOperationException {
        Class<?> thread = Class.forName("android.app.ActivityThread");
        return (Application) thread.getDeclaredMethod("currentApplication").invoke(null);
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        return cachedField(object.getClass(), name).get(object);
    }
    private static Field cachedField(Class<?> type, String name) throws ReflectiveOperationException {
        java.util.Map<String, Field> fields = FIELDS.computeIfAbsent(type, ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        Field value = fields.get(name);
        if (value != null) return value;
        value = type.getDeclaredField(name);
        value.setAccessible(true);
        fields.put(name, value);
        return value;
    }
    // Determine the view type before appending an image-only action. Missing IDs
    // and opaque bitmap content stay untouched rather than breaking host inflation.
    private static int clearLayoutBackground(RemoteViews views, boolean outer) throws Exception {
        Application app = application(); if (app == null) return 0;
        Context context = app.createPackageContext(views.getPackage(), Context.CONTEXT_IGNORE_SECURITY);
        int overrideId = (Integer) field(views, "mViewId");
        int count = 0;
        try (XmlResourceParser xml = context.getResources().getLayout(views.getLayoutId())) {
            boolean root = true;
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() != XmlPullParser.START_TAG) continue;
                int id = xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "id", -1);
                if (root && overrideId != -1) id = overrideId;
                if (id == android.R.id.background) {
                    views.setInt(id, "setBackgroundColor", Color.TRANSPARENT);
                    String type = xml.getName();
                    // An outer root ImageView may contain the entire widget as one bitmap.
                    if ((!root || !outer) && (type.equals("ImageView") || type.equals("android.widget.ImageView")))
                        views.setInt(id, "setImageAlpha", 0);
                    count++;
                } else if (outer && root && id != -1) {
                    views.setInt(id, "setBackgroundColor", Color.TRANSPARENT);
                    count++;
                }
                root = false;
            }
        }
        return count;
    }

    private static int hideBackground(RemoteViews views, Set<RemoteViews> seen, boolean outer) throws Exception {
        if (!seen.add(views)) return 0;
        int leaves = 0;
        boolean variant = false;
        // RemoteViews with portrait/landscape or responsive sizes forbid adding actions
        // to their wrapper. Append to each leaf after the provider's existing alpha action.
        for (String name : new String[]{"mLandscape", "mPortrait", "mSizedRemoteViews"}) {
            Field field = cachedField(RemoteViews.class, name);
            Object value = field.get(views);
            if (value instanceof RemoteViews) {
                variant = true;
                leaves += hideBackground((RemoteViews) value, seen, outer);
            } else if (value instanceof Iterable<?>) {
                for (Object child : (Iterable<?>) value) if (child instanceof RemoteViews) {
                    variant = true;
                    leaves += hideBackground((RemoteViews) child, seen, outer);
                }
            }
        }
        if (!variant) {
            Object actions = field(views, "mActions");
            if (actions instanceof Iterable<?>) {
                // Glance inserts the appWidgetBackground image as a nested RemoteViews
                // with a dynamically overridden root ID. Inspect those leaves too.
                for (Object action : (Iterable<?>) actions) {
                    if (action.getClass().getSimpleName().equals("ViewGroupActionAdd")) {
                        Object nested = field(action, "mNestedViews");
                        if (nested instanceof RemoteViews) leaves += hideBackground((RemoteViews) nested, seen, false);
                    }
                }
            }
            leaves += clearLayoutBackground(views, outer);
        }
        return leaves;
    }

    private static final Runnable REFRESH = () -> {
        try {
            Application app = application();
            if (app == null) return;
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            // Refresh every provider of this process on removals too, restoring its original background.
            for (AppWidgetProviderInfo info : manager.getInstalledProviders()) {
                if (!targetPackage.equals(info.provider.getPackageName())) continue;
                int[] ids = manager.getAppWidgetIds(info.provider);
                if (ids.length > 0) app.sendBroadcast(new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(info.provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids));
            }
            Log.i("MHMDesktopWidget", "BACKGROUND requested provider refresh after config change");
        } catch (Throwable error) {
            Log.w("MHMDesktopWidget", "BACKGROUND refresh unavailable", error);
        }
    };
}
