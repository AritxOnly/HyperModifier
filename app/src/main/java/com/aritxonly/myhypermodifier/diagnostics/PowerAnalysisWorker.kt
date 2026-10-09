package com.aritxonly.myhypermodifier

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** One endpoint job only: no periodic sampling during the screen-off test. */
class PowerAnalysisWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val start = inputData.getLong("start", 0)
        val state = PowerAnalysisStore.state(applicationContext)
        if (state.start != start || state.stopped) return Result.success()
        return try {
            PowerAnalysisCollector.finish(applicationContext, start)
            Result.success()
        } catch (error: Exception) {
            File(PowerAnalysisStore.directory(applicationContext, start), "errors.txt")
                .appendText("${System.currentTimeMillis()}: ${error.message}\n")
            Result.retry()
        }
    }
}

internal object PowerAnalysisCollector {
    // Serialize UI-triggered completion with the endpoint worker to avoid duplicate samples.
    @Synchronized
    fun begin(context: Context, start: Long) {
        val folder = PowerAnalysisStore.directory(context, start)
        File(folder, "device.json").writeText(JSONObject()
            .put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
            .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("moduleVersion", BuildConfig.VERSION_NAME).put("mode", "screen-off-endpoints").toString(2))
        inventory(context, folder)
        val probe = File(folder, "root-access.txt")
        val root = PowerAnalysisStore.requestsRoot(context) && shell("id", probe) && probe.readText().contains("uid=0")
        val success = if (root) dump(folder, "before") else false
        val sample = sample(context).put("phase", "before").put("statsAvailable", success)
        val baselineTime = sample.getLong("time")
        PowerAnalysisStore.anchor(context, start, baselineTime)
        PowerAnalysisStore.writeSample(context, start, sample, root,
            if (root && success) "基线就绪，请熄屏；测试期间不进行周期采样"
            else "基线就绪，请熄屏；整机统计不可用，仅记录电池与休眠时钟")
    }

    @Synchronized
    fun finish(context: Context, start: Long, manual: Boolean = false) {
        val state = PowerAnalysisStore.state(context)
        if (!PowerAnalysisPolicy.shouldFinish(state.start, start, state.stopped, state.end, System.currentTimeMillis(), manual)) return
        val folder = PowerAnalysisStore.directory(context, start)
        // Read the endpoint clocks before collecting dumps, excluding endpoint command overhead.
        val sample = sample(context).put("phase", "after").put("manualEnd", manual)
            .put("plannedEnd", state.end).put("completionDelayMs", (System.currentTimeMillis() - state.end).coerceAtLeast(0))
        val success = if (state.root) dump(folder, "after") else false
        sample.put("statsAvailable", success)
        if (PowerAnalysisStore.writeSample(context, start, sample, state.root,
                if (state.root && !success) "结束统计不完整，请查看原始记录" else "首尾采集完成")) {
            File(folder, "completion.json").writeText(sample.toString(2))
            PowerAnalysisStore.stop(context)
        }
    }

    private fun sample(context: Context): JSONObject {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        fun property(id: Int): Any = manager.getIntProperty(id).let {
            if (it == Int.MIN_VALUE || (id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER && it <= 0)) JSONObject.NULL else it
        }
        return JSONObject().put("time", System.currentTimeMillis())
            .put("elapsedRealtime", SystemClock.elapsedRealtime()).put("uptimeMillis", SystemClock.uptimeMillis())
            .put("level", battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
            .put("scale", battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1))
            .put("plugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1))
            .put("status", battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
            .put("temperatureTenthsC", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1))
            .put("voltageMv", battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1))
            .put("chargeUah", property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .put("currentUa", property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            .put("interactive", context.getSystemService(PowerManager::class.java).isInteractive)
    }

    private fun dump(folder: File, phase: String): Boolean {
        val started = System.currentTimeMillis()
        // --charged omits the multi-day history and retains cumulative UID statistics.
        val battery = shell("dumpsys batterystats --charged", File(folder, "batterystats-$phase.txt"))
        val power = shell("dumpsys power", File(folder, "power-$phase.txt"))
        val cpu = shell("dumpsys cpuinfo", File(folder, "cpuinfo-$phase.txt"))
        File(folder, "capture-$phase.json").writeText(JSONObject()
            .put("started", started).put("finished", System.currentTimeMillis())
            .put("battery", battery).put("power", power).put("cpu", cpu)
            .put("batteryCommand", "dumpsys batterystats --charged").toString(2))
        return battery && power && cpu
    }

    @Suppress("DEPRECATION")
    private fun inventory(context: Context, folder: File) {
        val packages = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val modules = JSONArray()
        val appNames = JSONObject()
        packages.forEach { app ->
            val label = context.packageManager.getApplicationLabel(app).toString()
            appNames.put(app.uid.toString(), (appNames.optString(app.uid.toString()) + " ${app.packageName} ($label)").trim())
            val legacy = app.metaData?.getBoolean("xposedmodule", false) == true
            val modern = !legacy && runCatching {
                ZipFile(app.sourceDir).use { it.getEntry("META-INF/xposed/java_init.list") != null }
            }.getOrDefault(false)
            if (legacy || modern) modules.put(JSONObject().put("package", app.packageName)
                .put("label", label).put("enabledInLsposed", JSONObject.NULL))
        }
        File(folder, "installed-lsp-modules.json").writeText(modules.toString(2))
        File(folder, "uid-packages.json").writeText(appNames.toString(2))
    }

    /** Fixed commands only. Bound execution and the persisted output size. */
    private fun shell(command: String, output: File): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).redirectOutput(output).start()
        try {
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                output.appendText("\n采集超时\n")
            }
            if (output.length() > 16 * 1024 * 1024) {
                java.io.RandomAccessFile(output, "rw").use { it.setLength(16 * 1024 * 1024) }
                output.appendText("\n内容超过上限，已截断\n")
                return false
            }
            finished && process.exitValue() == 0
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }.getOrElse {
        output.writeText("不可用：${it.javaClass.simpleName}: ${it.message}\n")
        false
    }
}
