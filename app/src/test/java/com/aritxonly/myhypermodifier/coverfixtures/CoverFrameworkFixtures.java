package com.aritxonly.myhypermodifier.coverfixtures;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.List;

/** Minimal WM test double. Does not claim to emulate native surfaces, insets or ROM behavior. */
public final class CoverFrameworkFixtures {
    public static BiConsumer<Object, Configuration> afterResolve;

    public static Object activity(String name, int width, int height) {
        Task task = new Task();
        task.config.densityDpi = 320;
        bounds(task.config, new Rect(0, 0, width, height));
        ActivityRecord record = new ActivityRecord();
        record.task = task;
        record.info = new ActivityInfo();
        record.info.packageName = name;
        record.info.applicationInfo = new ApplicationInfo();
        record.info.applicationInfo.uid = 10123;
        record.requested.setTo(task.config);
        record.recomputeConfiguration();
        return record;
    }

    public static Object wm(Context context) { WindowManagerService wm = new WindowManagerService(); wm.mContext = context; return wm; }
    public static Object policy() { return new DisplayPolicy(); }
    public static Object window(Object activity) { WindowState window = new WindowState(); window.mActivityRecord = (ActivityRecord) activity; return window; }
    public static Configuration requested(Object record) { return ((ActivityRecord) record).requested; }
    public static Configuration parent(Object record) { return ((ActivityRecord) record).task.config; }
    public static void legacy(Object record) { ((ActivityRecord) record).compat = new Object(); }
    public static void mode(Object record, int mode) { ((ActivityRecord) record).task.mode = mode; }
    public static void display(Object record, int display) { ((ActivityRecord) record).display = display; }
    public static void landscape(Object record) { ((ActivityRecord) record).orientation = Configuration.ORIENTATION_LANDSCAPE; }
    public static List<Rect> history(Object record) { return ((ActivityRecord) record).history; }
    public static Object sibling(Object record) {
        ActivityRecord sibling = new ActivityRecord();
        sibling.task = ((ActivityRecord) record).task;
        sibling.info = ((ActivityRecord) record).info;
        sibling.requested.setTo(sibling.task.config);
        sibling.recomputeConfiguration();
        return sibling;
    }

    static void bounds(Configuration config, Rect bounds) {
        try {
            Field f = Configuration.class.getField("windowConfiguration");
            Object wc = f.get(config);
            wc.getClass().getMethod("setBounds", Rect.class).invoke(wc, bounds);
            wc.getClass().getMethod("setAppBounds", Rect.class).invoke(wc, bounds);
            wc.getClass().getMethod("setMaxBounds", Rect.class).invoke(wc, bounds);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

class ConfigurationContainer {
    final Configuration config = new Configuration();
    Configuration getConfiguration() { return config; }
}

class ActivityRecord extends ConfigurationContainer {
    ActivityInfo info;
    Task task;
    int display;
    int orientation = Configuration.ORIENTATION_PORTRAIT;
    Object compat;
    final Configuration requested = new Configuration();
    final List<Rect> history = new ArrayList<>();
    Configuration getResolvedOverrideConfiguration() { return config; }
    Object getParent() { return task; }
    Task getTask() { return task; }
    int getDisplayId() { return display; }
    Object getAppCompatDisplayInsets() { return compat; }
    int getRequestedConfigurationOrientation() { return orientation; }
    void resolveOverrideConfiguration(Configuration parent) { config.setTo(requested); }
    void recomputeConfiguration() {
        resolveOverrideConfiguration(task.config);
        if (CoverFrameworkFixtures.afterResolve != null) CoverFrameworkFixtures.afterResolve.accept(this, task.config);
        try {
            Object wc = Configuration.class.getField("windowConfiguration").get(config);
            history.add(new Rect((Rect) wc.getClass().getMethod("getBounds").invoke(wc)));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

class Task extends ConfigurationContainer {
    int mTaskId = 42, mode = 1;
    int getWindowingMode() { return mode; }
    int getActivityType() { return 1; }
    void computeConfigResourceOverrides(Configuration out, Configuration parent) {
        try {
            Object wc = Configuration.class.getField("windowConfiguration").get(out);
            Rect bounds = (Rect) wc.getClass().getMethod("getBounds").invoke(wc);
            out.screenWidthDp = bounds.width() * 160 / parent.densityDpi;
            out.screenHeightDp = bounds.height() * 160 / parent.densityDpi;
            out.orientation = Configuration.ORIENTATION_PORTRAIT;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}

class WindowState {
    ActivityRecord mActivityRecord;
    int getRequestedVisibleTypes() { return 3; }
    boolean isRequestedVisible(int types) { return (types & 3) != 0; }
}

class DisplayContent { int getDisplayId() { return 0; } }
class DisplayPolicy {
    DisplayContent mDisplayContent = new DisplayContent();
    void focusChangedLw(WindowState oldFocus, WindowState focus) { }
    void updateSystemBarAttributes() { }
}
class WindowManagerService {
    Context mContext;
    Object mGlobalLock = new Object();
    void requestTraversal() { }
    void systemReady() { }
}
