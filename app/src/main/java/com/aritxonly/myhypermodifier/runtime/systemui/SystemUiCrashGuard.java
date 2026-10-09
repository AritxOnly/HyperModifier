package com.aritxonly.myhypermodifier;

import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.xmlpull.v1.XmlPullParser;

/** Counts unstable starts before any SystemUI Hook is registered. */
final class SystemUiCrashGuard {
    private static final String TAG = "MyHyperModifier";
    private static final int FILE_MAGIC = 0x4d484d34;
    private static final int LEGACY_FILE_MAGIC = 0x4d484d33;
    private static final long RESTART_WINDOW_MS = 20_000L;
    private static final long STABLE_AFTER_MS = 20_000L;
    private static final long BOOT_EPOCH_TOLERANCE_MS = 5_000L;
    private static final int MAX_UNSTABLE_STARTS = 3;
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MyHyperModifier-startup-guard");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean started;
    private static boolean automaticCompatibility;
    private static String compatibilityDetail = "SystemUI 自动保护已启用";

    private SystemUiCrashGuard() {}

    static synchronized boolean start(ApplicationInfo appInfo, int retryGeneration) {
        if (started) return automaticCompatibility;
        started = true;
        try {
            String dataDir = appInfo == null ? null : appInfo.deviceProtectedDataDir;
            if (dataDir == null || dataDir.isEmpty()) {
                dataDir = appInfo == null ? null : appInfo.dataDir;
            }
            if (dataDir == null || dataDir.isEmpty()) throw new IOException("No package data directory");
            AtomicFile file = new AtomicFile(new File(dataDir, "myhypermodifier_systemui_guard"));
            State previous;
            try {
                previous = read(file);
            } catch (FileNotFoundException missing) {
                previous = readLegacy(new File(dataDir,
                        "shared_prefs/myhypermodifier_systemui_recovery.xml"));
            } catch (IOException damaged) {
                Log.w(TAG, "Could not read SystemUI startup state; rebuilding it", damaged);
                previous = new State(0, -1L, 0, false, 0L);
            }
            boolean retry = previous.retryGeneration != retryGeneration;
            long now = SystemClock.elapsedRealtime();
            long bootEpoch = System.currentTimeMillis() - now;
            boolean sameBoot = isSameBoot(previous.bootEpochMillis, bootEpoch);
            int attempts = retry || !sameBoot ? 1 : nextAttemptCount(
                    now, previous.windowStartElapsed, previous.unstableStarts);
            automaticCompatibility = shouldUseCompatibility(
                    previous.automaticCompatibility, retry, attempts);
            if (automaticCompatibility) {
                compatibilityDetail = previous.automaticCompatibility && !retry
                        ? "此前触发的自动保护仍在生效；可在兼容与恢复中手动重试"
                        : "本次开机 20 秒内 SystemUI 启动三次，已启用自动保护";
            }
            write(file, new State(retryGeneration,
                    attempts == 1 ? now : previous.windowStartElapsed,
                    attempts, automaticCompatibility, bootEpoch));
            try {
                WORKER.schedule(() -> {
                    try {
                        synchronized (SystemUiCrashGuard.class) {
                            State state = read(file);
                            write(file, new State(state.retryGeneration, -1L, 0,
                                    state.automaticCompatibility, state.bootEpochMillis));
                        }
                    } catch (Throwable error) {
                        Log.w(TAG, "Could not mark SystemUI startup stable", error);
                    }
                }, STABLE_AFTER_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable error) {
                Log.w(TAG, "Could not schedule SystemUI stable-start check", error);
            }
            if (automaticCompatibility) {
                Log.w(TAG, "SystemUI compatibility mode active after repeated unstable starts");
            }
        } catch (Throwable error) {
            automaticCompatibility = true;
            compatibilityDetail = "无法保存 SystemUI 启动保护状态，已暂时跳过 Hook；详情见系统日志";
            Log.w(TAG, "SystemUI startup guard unavailable; using compatibility mode", error);
        }
        return automaticCompatibility;
    }

    static synchronized boolean isAutomaticCompatibilityActive() {
        return automaticCompatibility;
    }

    static synchronized String compatibilityDetail() {
        return compatibilityDetail;
    }

    private static State read(AtomicFile file) throws IOException {
        try (DataInputStream input = new DataInputStream(file.openRead())) {
            int magic = input.readInt();
            if (magic != FILE_MAGIC && magic != LEGACY_FILE_MAGIC) {
                throw new IOException("Unknown guard file version");
            }
            int retryGeneration = input.readInt();
            long windowStartElapsed = input.readLong();
            int unstableStarts = input.readInt();
            boolean automaticCompatibility = input.readBoolean();
            long bootEpochMillis = magic == FILE_MAGIC ? input.readLong() : 0L;
            return new State(retryGeneration, windowStartElapsed, unstableStarts,
                    automaticCompatibility, bootEpochMillis);
        }
    }

    private static State readLegacy(File file) throws IOException {
        int retryGeneration = 0;
        long windowStart = -1L;
        int attempts = 0;
        boolean compatibility = false;
        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                String key = parser.getAttributeValue(null, "name");
                String value = parser.getAttributeValue(null, "value");
                if (key == null || value == null) continue;
                try {
                    switch (key) {
                        case "retry_generation": retryGeneration = Integer.parseInt(value); break;
                        case "window_start_elapsed": windowStart = Long.parseLong(value); break;
                        case "unstable_starts": attempts = Integer.parseInt(value); break;
                        case "automatic_compatibility": compatibility = Boolean.parseBoolean(value); break;
                        default: break;
                    }
                } catch (NumberFormatException ignored) { /* Ignore one damaged preference. */ }
            }
        } catch (FileNotFoundException missing) {
            return new State(0, -1L, 0, false, 0L);
        } catch (Exception error) {
            throw new IOException("Could not read previous SystemUI guard", error);
        }
        return new State(retryGeneration, windowStart, attempts, compatibility, 0L);
    }

    private static void write(AtomicFile file, State state) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            DataOutputStream data = new DataOutputStream(output);
            data.writeInt(FILE_MAGIC);
            data.writeInt(state.retryGeneration);
            data.writeLong(state.windowStartElapsed);
            data.writeInt(state.unstableStarts);
            data.writeBoolean(state.automaticCompatibility);
            data.writeLong(state.bootEpochMillis);
            data.flush();
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
    }

    static int nextAttemptCount(long now, long windowStart, int attempts) {
        if (windowStart < 0 || now < windowStart || now - windowStart > RESTART_WINDOW_MS) return 1;
        return Math.min(MAX_UNSTABLE_STARTS, Math.max(0, attempts) + 1);
    }

    static boolean shouldUseCompatibility(boolean latched, boolean retry, int attempts) {
        return (!retry && latched) || attempts >= MAX_UNSTABLE_STARTS;
    }

    static boolean isSameBoot(long previousEpochMillis, long currentEpochMillis) {
        return previousEpochMillis != 0L
                && Math.abs(currentEpochMillis - previousEpochMillis) <= BOOT_EPOCH_TOLERANCE_MS;
    }

    private static final class State {
        final int retryGeneration;
        final long windowStartElapsed;
        final int unstableStarts;
        final boolean automaticCompatibility;
        final long bootEpochMillis;

        State(int retryGeneration, long windowStartElapsed, int unstableStarts,
              boolean automaticCompatibility, long bootEpochMillis) {
            this.retryGeneration = retryGeneration;
            this.windowStartElapsed = windowStartElapsed;
            this.unstableStarts = unstableStarts;
            this.automaticCompatibility = automaticCompatibility;
            this.bootEpochMillis = bootEpochMillis;
        }
    }
}
