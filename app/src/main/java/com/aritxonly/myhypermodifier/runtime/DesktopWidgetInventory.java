package com.aritxonly.myhypermodifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.ResultReceiver;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** On-demand, read-only widget inventory through the already scoped SystemUI process. */
public final class DesktopWidgetInventory {
    public static final String ACTION = "com.aritxonly.myhypermodifier.DESKTOP_WIDGET_INVENTORY";
    private static final AtomicBoolean ATTACHED = new AtomicBoolean(), BUSY = new AtomicBoolean();
    private static final java.util.concurrent.Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private DesktopWidgetInventory() {}
    static void attach(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 36 || !ATTACHED.compareAndSet(false, true)) return;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                try {
                    // The module publishes sender identity; do not disclose inventory to other apps.
                    if (!"com.aritxonly.myhypermodifier".equals(getSentFromPackage())
                            || getSentFromUid() != context.getPackageManager().getPackageUid("com.aritxonly.myhypermodifier", 0)) return;
                    ResultReceiver reply = intent.getParcelableExtra("reply", ResultReceiver.class);
                    if (reply == null) return;
                    if (!BUSY.compareAndSet(false, true)) { reply.send(1, error("正在读取，请稍后刷新")); return; }
                    PendingResult pending = goAsync();
                    EXECUTOR.execute(() -> {
                        File file = null;
                        try {
                            if (intent.getBooleanExtra("compatibility", false)) {
                                reply.send(0, DesktopWidgetCompatibility.read(context));
                                return;
                            }
                            IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                                    .getDeclaredMethod("getService", String.class).invoke(null, "appwidget");
                            if (service == null) throw new IllegalStateException("小组件服务不可用");
                            file = File.createTempFile("mhm-widget-inventory-", ".tmp", context.getCacheDir());
                            try (FileOutputStream output = new FileOutputStream(file)) { service.dump(output.getFD(), new String[0]); }
                            if (file.length() > 1024 * 1024) throw new IllegalStateException("小组件列表过大");
                            String dump = Files.readString(file.toPath());
                            ArrayList<String> entries = DesktopWidgetInventoryParser.parse(dump, Process.myUid() / 100000);
                            Bundle result = new Bundle(); result.putStringArrayList("widgets", entries); reply.send(0, result);
                        } catch (Throwable failure) {
                            reply.send(1, error(intent.getBooleanExtra("compatibility", false)
                                    ? "桌面兼容状态不可用，请确认系统桌面与系统界面作用域，并在更新模块后重启两者。请勿据此判断 schema 不兼容。"
                                    : "无法读取桌面小组件，请确认系统界面作用域已启用并重启系统界面"));
                        } finally {
                            if (file != null) file.delete();
                            BUSY.set(false); pending.finish();
                        }
                    });
                } catch (Exception ignoredError) { /* Not a trusted or supported request. */ }
            }
        }, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
    }
    private static Bundle error(String message) { Bundle result = new Bundle(); result.putString("error", message); return result; }
}
