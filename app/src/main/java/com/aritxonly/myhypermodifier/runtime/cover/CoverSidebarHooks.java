package com.aritxonly.myhypermodifier;

import android.app.ActivityManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** One plain SystemUI window; status and recent-task work is driven by system events. */
final class CoverSidebarHooks {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static Sidebar sidebar;

    static void install(XposedModule module, ClassLoader loader, Application app) throws Throwable {
        if (!ModuleSettings.coverAdaptationEnabled || !INSTALLED.compareAndSet(false, true)) return;
        try { installInternal(module, loader, app); }
        catch (Throwable error) { INSTALLED.set(false); throw error; }
    }

    private static void installInternal(XposedModule module, ClassLoader loader, Application app) throws Throwable {
        Sidebar next = new Sidebar(app, loader);
        // Follow native status-bar icon appearance, including transparent status bars.
        Class<?> queue = Class.forName("com.android.systemui.statusbar.CommandQueue", false, loader);
        Class<?> region = Class.forName("com.android.internal.view.AppearanceRegion", false, loader);
        Method appearance = region.getMethod("getAppearance");
        Method regionsChanged = CoverWindowAccess.method(queue, "onSystemBarAttributesChanged",
                int.class, int.class, Array.newInstance(region, 0).getClass(), boolean.class,
                int.class, int.class, String.class,
                Array.newInstance(Class.forName("com.android.internal.statusbar.LetterboxDetails", false, loader), 0).getClass());
        Class<?> tasks = Class.forName("com.android.systemui.shared.system.TaskStackChangeListeners$Impl", false, loader);
        Method descriptionChanged = CoverWindowAccess.method(tasks, "onTaskDescriptionChanged", ActivityManager.RunningTaskInfo.class);
        Method imeChanged = CoverWindowAccess.method(queue, "setImeWindowStatus", int.class, int.class, int.class, boolean.class);
        var handle = module.hook(regionsChanged).setId("cover-sidebar-appearance")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if ((Integer) chain.getArg(0) != 0) return result;
                    Object regions = chain.getArg(2);
                    if (regions != null && Array.getLength(regions) > 0) {
                        boolean dark = ((Integer) appearance.invoke(Array.get(regions, 0)) & 8) != 0;
                        next.requestTint(dark);
                    }
                    return result;
                });
        io.github.libxposed.api.XposedInterface.HookHandle taskHandle = null;
        io.github.libxposed.api.XposedInterface.HookHandle imeHandle = null;
        try {
            imeHandle = module.hook(imeChanged).setId("cover-sidebar-ime")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if ((Integer) chain.getArg(0) != 0) return result;
                        boolean shown = ((Integer) chain.getArg(1) & 2) != 0; // IME_VISIBLE
                        if (next.reportedIme != shown) {
                            next.reportedIme = shown;
                            next.main.post(() -> next.imeChanged(shown));
                        }
                        return result;
                    });
            taskHandle = module.hook(descriptionChanged).setId("cover-sidebar-task-description")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (!next.active) return result;
                        ActivityManager.RunningTaskInfo info = (ActivityManager.RunningTaskInfo) chain.getArg(0);
                        if (info != null && info.taskId == next.taskId && info.taskDescription != null) {
                            int id = info.taskId, color = info.taskDescription.getStatusBarColor();
                            next.main.post(() -> next.descriptionChanged(id, color));
                        }
                        return result;
                    });
            next.start();
            ModuleSettings.onChanged(next::settingsChanged);
            sidebar = next;
            HookDiagnostics.available("com.android.systemui", "外屏适配侧栏（实验）");
        } catch (Throwable error) {
            handle.unhook();
            if (taskHandle != null) taskHandle.unhook();
            if (imeHandle != null) imeHandle.unhook();
            INSTALLED.set(false); next.exit(); throw error;
        }
    }

    private static final class Sidebar {
        // A privileged native system-bar companion window, not an application overlay.
        static final int WINDOW_TYPE = 2017; // TYPE_STATUS_BAR_SUB_PANEL
        final Application app;
        final Handler main = new Handler(Looper.getMainLooper());
        final Binder lease = new Binder();
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        final LruCache<String, Drawable> icons = new LruCache<>(16);
        final Object taskService;
        final Method recentTasks, taskList, currentUser, fitInsets;
        final Field taskUser;
        final ConnectivityManager connectivity;
        final AtomicBoolean refreshQueued = new AtomicBoolean();
        final AtomicBoolean tintQueued = new AtomicBoolean();
        volatile boolean active;
        volatile boolean pendingDark;
        volatile boolean appearanceKnown;
        volatile boolean reportedIme;
        volatile boolean allowed = true;
        volatile boolean shown, dark;
        boolean left, statusRegistered, networkRegistered;
        volatile int taskId = -1;
        int width, height, taskBackground, generation, colorGeneration;
        String lastMessage = "";
        Intent lastState;
        boolean imeVisible;
        WindowManager windows;
        Context windowContext;
        LinearLayout root, recent;
        TextView battery, network;
        ConnectivityManager.NetworkCallback networkCallback;
        final BroadcastReceiver status = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()) && battery != null) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    boolean charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
                    battery.setText(level >= 0 && scale > 0 ? (charging ? "充电 " : "") + (level * 100 / scale) + "%" : "电池");
                }
            }
        };

        Sidebar(Application app, ClassLoader loader) throws ReflectiveOperationException {
            this.app = app;
            worker.allowCoreThreadTimeOut(true);
            Class<?> atm = Class.forName("android.app.ActivityTaskManager", false, loader);
            taskService = atm.getMethod("getService").invoke(null);
            recentTasks = CoverWindowAccess.method(taskService.getClass(), "getRecentTasks", int.class, int.class, int.class);
            taskList = Class.forName("android.content.pm.ParceledListSlice", false, loader).getMethod("getList");
            currentUser = ActivityManager.class.getDeclaredMethod("getCurrentUser");
            currentUser.setAccessible(true);
            fitInsets = WindowManager.LayoutParams.class.getMethod("setFitInsetsTypes", int.class);
            taskUser = CoverWindowAccess.field(ActivityManager.RecentTaskInfo.class, "userId");
            connectivity = app.getSystemService(ConnectivityManager.class);
        }

        void start() {
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent state) { state(state); }
            }, new IntentFilter(CoverAdaptationContract.STATE), "android.permission.MANAGE_ACTIVITY_TASKS", main, Context.RECEIVER_EXPORTED);
            IntentFilter lifecycle = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            lifecycle.addAction("android.intent.action.USER_SWITCHED");
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) { exit(); icons.evictAll(); }
            }, lifecycle, null, main, Context.RECEIVER_NOT_EXPORTED);
            Bundle extra = new Bundle(); extra.putBinder("lease", lease);
            app.sendBroadcast(new Intent(CoverAdaptationContract.SYSTEM_COMMAND).setPackage("android")
                    .putExtra("command", CoverAdaptationContract.ATTACH).putExtras(extra));
        }

        void settingsChanged() {
            boolean next = ModuleSettings.coverAdaptationEnabled && ModuleSettings.moduleHooksEnabled
                    && !ModuleSettings.systemUiCompatibilityMode;
            if (allowed == next) return;
            allowed = next;
            if (!next) main.post(this::exit);
        }

        void state(Intent state) {
            lastState = state;
            if (!ModuleSettings.moduleHooksEnabled || ModuleSettings.systemUiCompatibilityMode
                    || !ModuleSettings.coverAdaptationEnabled) { exit(); return; }
            String message = state.getStringExtra("message");
            if (message != null && !message.isEmpty() && !message.equals(lastMessage)) {
                Toast.makeText(app, message, Toast.LENGTH_LONG).show();
            }
            lastMessage = message;
            active = state.getBooleanExtra("active", false);
            int nextTask = state.getIntExtra("task", -1);
            boolean nextLeft = state.getBooleanExtra("left", false);
            int nextWidth = state.getIntExtra("width", 0), nextHeight = state.getIntExtra("height", 0);
            boolean changed = nextTask != taskId;
            if (shown && (left != nextLeft || width != nextWidth || height != nextHeight)) remove();
            taskId = nextTask; left = nextLeft; width = nextWidth; height = nextHeight;
            if (changed) { taskBackground = 0; colorGeneration++; tint(dark); }
            if (!active || imeVisible || !state.getBooleanExtra("visible", false)) { remove(); return; }
            try {
                if (!shown) { show(); changed = true; }
                if (changed) refreshRecents();
            } catch (Throwable error) {
                HookDiagnostics.failure("com.android.systemui", "外屏适配侧栏", error);
                exit();
            }
        }

        int dp(int value) { return Math.round(value * app.getResources().getDisplayMetrics().density); }

        void show() throws ReflectiveOperationException {
            int railWidth = width - CoverAdaptationPolicy.appWidth(height);
            if (railWidth < dp(64)) throw new IllegalStateException("Sidebar too narrow");
            windowContext = app.createWindowContext(WINDOW_TYPE, null);
            windows = windowContext.getSystemService(WindowManager.class);
            root = new LinearLayout(windowContext);
            root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(8), dp(12), dp(8), dp(12));
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                var safe = insets.getInsets(WindowInsets.Type.displayCutout() | WindowInsets.Type.navigationBars());
                view.setPadding(dp(8), Math.max(dp(12), safe.top), dp(8), Math.max(dp(12), safe.bottom));
                return insets;
            });
            TextClock clock = new TextClock(windowContext);
            clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("HH:mm"); clock.setTextSize(16);
            clock.setGravity(Gravity.CENTER); root.addView(clock, row(dp(48)));
            network = text("网络", 12); root.addView(network, row(dp(32)));
            battery = text("电池", 12); root.addView(battery, row(dp(32)));
            recent = new LinearLayout(windowContext); recent.setOrientation(LinearLayout.VERTICAL);
            recent.setGravity(Gravity.CENTER);
            root.addView(recent, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView swap = text("换边", 14); swap.setContentDescription("切换侧栏左右位置");
            swap.setOnClickListener(v -> CoverAdaptationContract.sendSystem(app, CoverAdaptationContract.SWAP));
            root.addView(swap, row(dp(48)));
            TextView exit = text("退出", 14); exit.setContentDescription("退出外屏适配并恢复应用布局");
            exit.setOnClickListener(v -> exit()); root.addView(exit, row(dp(48)));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(railWidth, height, WINDOW_TYPE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.x = left ? 0 : CoverAdaptationPolicy.appWidth(height);
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            fitInsets.invoke(params, 0);
            params.setTitle("HyperModifierCoverSidebar");
            tint(appearanceKnown ? pendingDark
                    : (app.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES);
            windows.addView(root, params); shown = true;
            statusRegistered = true;
            app.registerReceiver(status, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), null, main, Context.RECEIVER_NOT_EXPORTED);
            if (connectivity != null) {
                int stamp = generation;
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                        main.post(() -> { if (stamp == generation) updateNetwork(c); });
                    }
                    @Override public void onLost(Network n) {
                        main.post(() -> { if (stamp == generation) updateNetwork(null); });
                    }
                };
                connectivity.registerDefaultNetworkCallback(networkCallback, main); networkRegistered = true;
            }
        }

        TextView text(String label, int size) {
            TextView view = new TextView(windowContext); view.setText(label); view.setTextSize(size);
            view.setGravity(Gravity.CENTER); return view;
        }

        LinearLayout.LayoutParams row(int height) { return new LinearLayout.LayoutParams(-1, height); }

        void updateNetwork(NetworkCapabilities c) {
            if (network == null) return;
            network.setText(c == null ? "离线" : c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                    : c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "移动网络" : "已连接");
        }

        void tint(boolean useDarkIcons) {
            dark = useDarkIcons;
            if (root == null) return;
            int background = Color.alpha(taskBackground) == 255 ? taskBackground : dark ? 0xfff3f3f3 : 0xff171717;
            root.setBackgroundColor(background);
            int foreground = dark ? Color.BLACK : Color.WHITE;
            for (int i = 0; i < root.getChildCount(); i++) {
                View view = root.getChildAt(i);
                if (view instanceof TextView) ((TextView) view).setTextColor(foreground);
            }
        }

        void requestTint(boolean value) {
            pendingDark = value;
            appearanceKnown = true;
            if (!active || !shown || value == dark || !tintQueued.compareAndSet(false, true)) return;
            main.post(() -> { tintQueued.set(false); tint(pendingDark); });
        }

        void descriptionChanged(int id, int color) {
            if (!active || id != taskId) return;
            colorGeneration++;
            taskBackground = color;
            tint(dark);
        }

        void imeChanged(boolean shown) {
            if (imeVisible == shown) return;
            imeVisible = shown;
            // The ROM may draw a full-display IME. Release the sidebar's input rectangle while
            // typing so it cannot cover Enter/Backspace or swallow keyboard touches.
            if (shown) remove();
            else if (lastState != null) state(lastState);
        }

        void refreshRecents() {
            if (!refreshQueued.compareAndSet(false, true)) return;
            int stamp = generation, selected = taskId, colorStamp = colorGeneration;
            worker.execute(() -> {
                List<Recent> result = new ArrayList<>();
                int background = 0;
                try {
                    int user = (Integer) currentUser.invoke(null);
                    Object slice = recentTasks.invoke(taskService, 16, 0, user);
                    List<?> tasks = (List<?>) taskList.invoke(slice);
                    HashSet<String> seen = new HashSet<>();
                    for (Object entry : tasks) {
                        ActivityManager.RecentTaskInfo task = (ActivityManager.RecentTaskInfo) entry;
                        if (taskUser.getInt(task) != user) continue;
                        if (task.taskId == selected && task.taskDescription != null) {
                            background = task.taskDescription.getStatusBarColor();
                        }
                        ComponentName component = task.topActivity != null ? task.topActivity : task.baseActivity;
                        if (component == null || task.taskId == selected || task.persistentId < 0
                                || !CoverAdaptationPolicy.application(component.getPackageName())
                                || !seen.add(component.getPackageName()) || result.size() >= 4) continue;
                        String pkg = component.getPackageName();
                        Drawable icon = icons.get(pkg);
                        if (icon == null) { icon = app.getPackageManager().getApplicationIcon(pkg); icons.put(pkg, icon); }
                        CharSequence label = app.getPackageManager().getApplicationLabel(app.getPackageManager().getApplicationInfo(pkg, 0));
                        result.add(new Recent(task.persistentId, label, icon));
                    }
                } catch (Exception error) {
                    android.util.Log.w("MyHyperModifier", "Cover recent tasks unavailable", error);
                }
                int color = background;
                main.post(() -> {
                    refreshQueued.set(false);
                    if (!shown || stamp != generation || selected != taskId) {
                        if (shown) refreshRecents();
                        return;
                    }
                    if (colorStamp == colorGeneration) taskBackground = color;
                    tint(dark); recent.removeAllViews();
                    for (Recent item : result) {
                        ImageButton button = new ImageButton(windowContext);
                        button.setImageDrawable(item.icon); button.setBackgroundColor(Color.TRANSPARENT);
                        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        button.setPadding(dp(8), dp(8), dp(8), dp(8));
                        button.setContentDescription("切换到 " + item.label);
                        button.setOnClickListener(v -> app.sendBroadcast(new Intent(CoverAdaptationContract.SYSTEM_COMMAND)
                                .setPackage("android").putExtra("command", CoverAdaptationContract.SWITCH).putExtra("task", item.id)));
                        recent.addView(button, new LinearLayout.LayoutParams(dp(48), dp(56)));
                    }
                });
            });
        }

        void remove() {
            generation++;
            if (networkRegistered) {
                networkRegistered = false;
                try { connectivity.unregisterNetworkCallback(networkCallback); }
                catch (RuntimeException ignored) { }
            }
            if (statusRegistered) {
                statusRegistered = false;
                try { app.unregisterReceiver(status); }
                catch (IllegalArgumentException ignored) { }
            }
            // addView registers the window before View attachment. Remove it even when the first
            // traversal has not run yet (rapid shade/fold/exit), or the rail becomes orphaned.
            if (root != null && windows != null && shown) {
                try { windows.removeViewImmediate(root); }
                catch (RuntimeException error) { android.util.Log.w("MyHyperModifier", "Cover sidebar detach failed", error); }
            }
            shown = false; root = null; recent = null; battery = null; network = null;
            windowContext = null; windows = null; taskBackground = 0;
        }

        void exit() {
            active = false; remove();
            CoverAdaptationContract.sendSystem(app, CoverAdaptationContract.EXIT);
        }

        record Recent(int id, CharSequence label, Drawable icon) {}
    }

    private CoverSidebarHooks() {}
}
