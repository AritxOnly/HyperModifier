package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.ToLongFunction;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Native Header contracts verified against reference/Settings.apk. */
final class SettingsHomeHooks {
    private static final String PACKAGE = "com.aritxonly.myhypermodifier";
    private static final long MODULE_ID = SettingsHomeHeaderPolicy.MODULE_ID;
    private static final long MANAGER_ID = SettingsHomeHeaderPolicy.MANAGER_ID;

    static void install(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> home = Class.forName("com.android.settings.MiuiSettings", false, loader);
        Class<?> header = Class.forName("com.android.settingslib.miuisettings.preference.PreferenceActivity$Header", false, loader);
        Field headerId = header.getField("id");
        ToLongFunction<Object> readId = item -> {
            try { return headerId.getLong(item); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        };
        module.hook(home.getDeclaredMethod("updateHeaderList", List.class))
                .setId("settings-home-headers").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    @SuppressWarnings("unchecked")
                    List<Object> headers = (List<Object>) chain.getArg(0);
                    if (headers == null) return chain.proceed();
                    Context context = (Context) chain.getThisObject();
                    try {
                        SettingsHomeHeaderPolicy.removeInjected(headers, readId);
                    } catch (Throwable failure) {
                        module.log(Log.WARN, "MyHyperModifier", "Could not prepare Settings entries", failure);
                    }
                    // Native filtering must mutate the exact list shared with the adapter.
                    Object result = chain.proceed();
                    try {
                        insertFiltered(context, headers, header, readId, loader);
                    } catch (Throwable failure) {
                        module.log(Log.WARN, "MyHyperModifier", "Settings entry unavailable", failure);
                    }
                    return result;
                });
        module.hook(home.getDeclaredMethod("onHeaderClick", header, int.class))
                .setId("settings-home-open-module").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    long value = readId.applyAsLong(chain.getArg(0));
                    if (value != MODULE_ID && value != MANAGER_ID && !SettingsHomeHeaderPolicy.isModuleEntryId(value)) return chain.proceed();
                    Activity activity = (Activity) chain.getThisObject();
                    if (value == MANAGER_ID) LsposedManagerLauncher.open(activity, module.getFrameworkName());
                    else try {
                        Intent target = value == MODULE_ID ? moduleIntent() : (Intent) header.getField("intent").get(chain.getArg(0));
                        if (target == null) return chain.proceed();
                        activity.startActivity(target);
                    }
                    catch (Exception failure) { Toast.makeText(activity, "无法打开模块设置，请确认模块已安装且入口可用。", Toast.LENGTH_SHORT).show(); }
                    return null;
                });
        installIcons(module, loader, header, readId);
    }

    private static void installIcons(XposedModule module, ClassLoader loader, Class<?> header,
                                     ToLongFunction<Object> readId) {
        try {
            Class<?> adapter = Class.forName("com.android.settings.MiuiSettings$HeaderAdapter", false, loader);
            Class<?> holder = Class.forName("com.android.settings.MiuiSettings$HeaderViewHolder", false, loader);
            Field iconField = holder.getDeclaredField("icon");
            iconField.setAccessible(true);
            module.hook(adapter.getDeclaredMethod("setIcon", holder, header))
                    .setId("settings-home-entry-icons").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        long id = readId.applyAsLong(chain.getArg(1));
                        if (id != MODULE_ID && id != MANAGER_ID && !SettingsHomeHeaderPolicy.isModuleEntryId(id)) return chain.proceed();
                        // Keep native icon visibility and row binding before replacing the artwork.
                        Object result = chain.proceed();
                        try {
                            ImageView icon = (ImageView) iconField.get(chain.getArg(0));
                            Drawable drawable;
                            if (SettingsHomeHeaderPolicy.isModuleEntryId(id)) {
                                android.os.Bundle extras = (android.os.Bundle) header.getField("extras").get(chain.getArg(1));
                                drawable = icon.getContext().getPackageManager().getApplicationIcon(extras.getString("mhm_target_package"));
                            } else {
                                Context own = icon.getContext().createPackageContext(PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
                                String name = id == MANAGER_ID ? "ic_settings_lsposed" : "ic_settings_hypermodifier";
                                int resource = own.getResources().getIdentifier(name, "drawable", PACKAGE);
                                drawable = own.getDrawable(resource);
                            }
                            // The native row reserves 34dp for a centered 28dp drawable. Changing
                            // the ImageView to 28dp also moves its following title 6dp to the left.
                            // Bound only the artwork; preserve host LayoutParams, padding and scale.
                            int dimension = icon.getResources().getIdentifier(
                                    "header_icon_size", "dimen", "com.android.settings");
                            int size = dimension != 0 ? icon.getResources().getDimensionPixelSize(dimension)
                                    : Math.round(28f * icon.getResources().getDisplayMetrics().density);
                            drawable = boundedIcon(drawable, size);
                            icon.setImageTintList(null);
                            icon.clearColorFilter();
                            icon.setImageDrawable(drawable);
                        } catch (Exception unavailable) {
                            module.log(Log.WARN, "MyHyperModifier", "Settings entry icon unavailable", unavailable);
                        }
                        return result;
                    });
        } catch (Exception unavailable) {
            module.log(Log.WARN, "MyHyperModifier", "Settings icon binding unavailable", unavailable);
        }
    }

    /** Prevent app icon intrinsic sizes from changing wrap_content native rows. */
    private static Drawable boundedIcon(Drawable drawable, int size) {
        return new android.graphics.drawable.DrawableWrapper(drawable) {
            @Override public int getIntrinsicWidth() { return size; }
            @Override public int getIntrinsicHeight() { return size; }
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                android.graphics.Path clip = new android.graphics.Path();
                float radius = Math.min(bounds.width(), bounds.height()) * 0.28f;
                clip.addRoundRect(new android.graphics.RectF(bounds), radius, radius,
                        android.graphics.Path.Direction.CW);
                int save = canvas.save();
                canvas.clipPath(clip);
                super.draw(canvas);
                canvas.restoreToCount(save);
            }
        };
    }

    private static void insertFiltered(Context context, List<Object> headers, Class<?> type,
                                       ToLongFunction<Object> readId, ClassLoader loader) throws Exception {
        if (!ModuleSettings.moduleHooksEnabled) return;
        java.util.Map<String, List<Object>> positions = new java.util.LinkedHashMap<>();
        for (String position : new String[]{"device", "top", "bottom", "middle"}) positions.put(position, new java.util.ArrayList<>());
        String builtInPosition = SettingsHomeHeaderPolicy.normalizePosition(ModuleSettings.settingsHomeEntryPosition);
        if (ModuleSettings.settingsModulesEntryEnabled) positions.get(SettingsHomeHeaderPolicy.normalizePosition(ModuleSettings.settingsManagerEntryPosition)).add(header(context, type, MANAGER_ID, "LSPosed", true));
        if (ModuleSettings.settingsHomeEntryEnabled) positions.get(builtInPosition).add(header(context, type, MODULE_ID, "HyperModifier", true));
        java.util.Set<Long> usedIds = new java.util.HashSet<>();
        for (HomeModuleEntryConfig.Entry entry : HomeModuleEntryConfig.decode(ModuleSettings.settingsModuleEntries)) {
            if (PACKAGE.equals(entry.packageName) || "org.lsposed.manager".equals(entry.packageName)) continue;
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(new android.content.ComponentName(entry.packageName, entry.activity))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!entry.category.isBlank()) intent.addCategory(entry.category);
                android.content.pm.ActivityInfo activity = context.getPackageManager().getActivityInfo(intent.getComponent(), 0);
                if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled
                        || context.getPackageManager().resolveActivity(intent, 0) == null) continue;
                if (activity.permission != null && context.getPackageManager().checkPermission(activity.permission, context.getPackageName())
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) continue;
                long entryId = SettingsHomeHeaderPolicy.moduleEntryId(entry.packageName);
                if (!usedIds.add(entryId)) continue;
                String label = activity.applicationInfo.loadLabel(context.getPackageManager()).toString();
                Object value = header(context, type, entryId, label.isBlank() ? entry.packageName : label, true);
                type.getField("intent").set(value, intent);
                android.os.Bundle extras = (android.os.Bundle) type.getField("extras").get(value);
                extras.putString("mhm_target_package", entry.packageName);
                positions.get(entry.position).add(value);
            } catch (Exception unavailable) { /* Uninstalled, disabled or inaccessible modules stay hidden. */ }
        }
        if (positions.values().stream().allMatch(List::isEmpty)) return;
        Class<?> utils = Class.forName("com.android.settings.MiuiUtils", false, loader);
        boolean legacy = Boolean.TRUE.equals(utils.getDeclaredMethod("isHyperOs1").invoke(null));
        Field group = type.getField("groupId"), fragment = type.getField("fragment"), intent = type.getField("intent");
        java.util.function.ToIntFunction<Object> readGroup = item -> {
            try { return group.getInt(item); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        };
        java.util.function.ObjIntConsumer<Object> writeGroup = (item, value) -> {
            try { group.setInt(item, value); } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        };
        java.util.function.Predicate<Object> category = item -> {
            try { return fragment.get(item) == null && intent.get(item) == null; }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        };
        for (java.util.Map.Entry<String, List<Object>> section : positions.entrySet()) {
            if (section.getValue().isEmpty()) continue;
            String position = section.getKey();
            long lower = "top".equals(position) ? resourceId(context, "wifi_settings")
                    : "bottom".equals(position) ? SettingsHomeHeaderPolicy.betweenAnchor(headers,
                    resourceId(context, "feedback_services_settings"), resourceId(context, "other_advanced_settings"), readId)
                    : resourceId(context, "personalize_title");
            Object start = legacy ? header(context, type, SettingsHomeHeaderPolicy.moduleEntryId("separator." + position + ".start"), "", false) : null;
            Object end = legacy ? header(context, type, SettingsHomeHeaderPolicy.moduleEntryId("separator." + position + ".end"), "", false) : null;
            SettingsHomeHeaderPolicy.placeEntries(headers, section.getValue(), position, resourceId(context, "my_device"), lower,
                    "middle".equals(position) ? resourceId(context, "xiao_mi_hyperos_ai") : 0,
                    start, end, readId, readGroup, writeGroup, category);
        }
    }

    private static int resourceId(Context context, String name) {
        return context.getResources().getIdentifier(name, "id", "com.android.settings");
    }

    private static Object header(Context context, Class<?> type, long id, String title, boolean entry) throws Exception {
        Object value = type.getDeclaredConstructor().newInstance();
        type.getField("id").setLong(value, id);
        type.getField("title").set(value, title);
        type.getField("titleRes").setInt(value, 0);
        type.getField("summary").set(value, "");
        type.getField("extras").set(value, new android.os.Bundle());
        try { type.getField("key").set(value, "mhm-entry-" + id); }
        catch (NoSuchFieldException ignored) { }
        // Negative group IDs are excluded from native section card/divider decoration.
        type.getField("groupId").setInt(value, SettingsHomeHeaderPolicy.groupFor(entry));
        if (entry) {
            // Keep a valid host resource as fallback; our setIcon hook loads the module drawable.
            int icon = context.getResources().getIdentifier("ic_settings_24dp", "drawable", "com.android.settings");
            type.getField("iconRes").setInt(value, icon == 0 ? android.R.drawable.ic_menu_preferences : icon);
            type.getField("intent").set(value, moduleIntent());
        }
        return value;
    }

    private static Intent moduleIntent() {
        return new Intent(Intent.ACTION_MAIN).setClassName(PACKAGE, PACKAGE + ".SettingsActivity");
    }
}
