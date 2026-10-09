package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.TimeUnit;

/** Sends only LSPosed's fixed official launch request, as HyperModifier's own UID. */
public final class ManagerLaunchActivity extends Activity {
    private static final String OPEN_MANAGER_COMMAND =
            "/system/bin/am broadcast --user 0 -a android.telephony.action.SECRET_CODE"
                    + " -d android_secret_code://5776733";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { finish(); return; }
        Toast.makeText(this, "正在打开 LSPosed；如出现 Root 授权，请授权 HyperModifier。", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean submitted = false;
            Process process = null;
            try {
                // Standard su interface shared by Magisk, KernelSU and APatch.
                // The command is constant: Intent extras cannot change what runs.
                process = new ProcessBuilder("su", "-c", OPEN_MANAGER_COMMAND)
                        .redirectErrorStream(true).redirectOutput(new java.io.File("/dev/null")).start();
                boolean completed = process.waitFor(45, TimeUnit.SECONDS);
                submitted = completed && process.exitValue() == 0;
            } catch (Exception unavailable) {
                android.util.Log.w("MyHyperModifier", "LSPosed Root launch request failed", unavailable);
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
            boolean completed = submitted;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!completed) Toast.makeText(this,
                        "无法提交启动请求，请在 Root 管理器中授权 HyperModifier，并确认 su 可用。",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        }, "mhm-open-manager").start();
    }
}
