package com.aritxonly.myhypermodifier;

import android.appwidget.*;
import android.content.*;
import dalvik.system.PathClassLoader;
import java.lang.reflect.*;

/** Standalone app_process regression: installed provider dimensions -> stock picker -> ItemInfo.
 * Compile alongside LocalWidgetCatalogSizes.java and LocalWidgetCatalogPolicy.java.
 * No widget allocation, binding, workspace writes, or application restarts.
 */
public final class LocalWidgetCatalogSizesDeviceTest {
    public static void main(String[] args) throws Throwable {
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
        Context shell = system.createPackageContext("com.android.shell", 0);
        Context assistant = system.createPackageContext("com.miui.personalassistant", 0);
        LocalWidgetCatalogSizes sizes = new LocalWidgetCatalogSizes(assistant.getResources(), "com.miui.personalassistant");
        ClassLoader loader = new PathClassLoader(args[0], ClassLoader.getSystemClassLoader());
        Class<?> item = Class.forName("com.miui.personalassistant.widget.entity.ItemInfo", false, loader);
        Method apply = Class.forName("com.miui.personalassistant.picker.util.g", false, loader).getMethod("a", item, int.class);
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe"); Object unsafe = null;
        for (Field f : unsafeType.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == unsafeType) {
            f.setAccessible(true); unsafe = f.get(null); break;
        }
        int count = 0;
        for (String pkg : new String[]{"com.achievo.vipshop", "com.aritxonly.deadliner"}) {
            for (AppWidgetProviderInfo provider : AppWidgetManager.getInstance(shell)
                    .getInstalledProvidersForPackage(pkg, android.os.Process.myUserHandle())) {
                int style = sizes.select(provider.targetCellWidth, provider.targetCellHeight, provider.minWidth, provider.minHeight);
                String name = provider.provider.getClassName();
                int expected = pkg.equals("com.achievo.vipshop")
                        ? (name.endsWith("WidgetMultiProvider") || name.endsWith("WidgetDeskHelerProvider") || name.endsWith("WidgetStyle4Provider") ? 2 : 1)
                        : (name.endsWith("CapsuleTaskGlanceWidgetReceiver") ? 6 : 1);
                if (style != expected) throw new AssertionError(name + ": unexpected style " + style + " expected " + expected);
                Object target = unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, item);
                apply.invoke(null, target, style);
                int x = LocalWidgetCatalogSizes.spanX(style), y = LocalWidgetCatalogSizes.spanY(style);
                if (item.getField("spanX").getInt(target) != x || item.getField("spanY").getInt(target) != y
                        || item.getField("configSpanX").getInt(target) != x || item.getField("configSpanY").getInt(target) != y)
                    throw new AssertionError("native item span mismatch: " + name);
                System.out.println("PASS: " + name + " minPx=" + provider.minWidth + "x" + provider.minHeight
                        + " target=" + provider.targetCellWidth + "x" + provider.targetCellHeight + " -> " + x + "x" + y);
                count++;
            }
        }
        if (count != 9) throw new AssertionError("expected 7 VIP + 2 Deadliner providers, found " + count);
        System.out.println("PASS: all 9 installed provider sizes agree with actual native ItemInfo/configSpan conversion");
        System.exit(0);
    }
}
