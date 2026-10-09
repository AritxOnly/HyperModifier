package com.aritxonly.myhypermodifier

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal object PowerAnalysisStore {
    const val PREFS = "power_analysis"
    private const val PERIODIC = "power-analysis-samples"
    private const val FINISH = "power-analysis-finish"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): PowerAnalysisState {
        val p = prefs(context)
        return PowerAnalysisState(p.getLong("start", 0), p.getLong("end", 0),
            p.getBoolean("stopped", true), p.getBoolean("root", false),
            p.getString("status", "尚未开始").orEmpty(), p.getInt("count", 0))
    }

    fun directory(context: Context, start: Long): File = File(context.filesDir, "power-analysis/$start")

    fun start(context: Context, requestRoot: Boolean, minutes: Int = 15) {
        require(minutes in listOf(15, 30))
        check(state(context).stopped) { "监测已经开启" }
        val start = System.currentTimeMillis()
        directory(context, start).mkdirs()
        check(prefs(context).edit().clear().putLong("start", start)
            .putLong("end", start + TimeUnit.MINUTES.toMillis(minutes.toLong())).putBoolean("stopped", false)
            .putBoolean("requestRoot", requestRoot).putString("status", "正在准备基线，请暂时保持亮屏").commit())
        try {
            PowerAnalysisCollector.begin(context, start)
            val baseline = state(context)
            val delay = (baseline.end - System.currentTimeMillis()).coerceAtLeast(0)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC) // Remove legacy periodic jobs.
            WorkManager.getInstance(context).enqueueUniqueWork(FINISH, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PowerAnalysisWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("start" to start)).build())
        } catch (error: Exception) {
            stop(context)
            throw error
        }
        // Keep the latest three sessions; starting again does not erase the previous report.
        File(context.filesDir, "power-analysis").listFiles()?.filter { it.isDirectory }
            ?.sortedByDescending { it.name.toLongOrNull() ?: 0 }?.drop(3)?.forEach { it.deleteRecursively() }
    }

    @Synchronized
    fun stop(context: Context) {
        prefs(context).edit().putBoolean("stopped", true)
            .commit()
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(FINISH)
    }

    @Synchronized
    fun writeSample(context: Context, start: Long, sample: JSONObject, root: Boolean, status: String): Boolean {
        val state = state(context)
        if (state.start != start || state.stopped) return false
        File(directory(context, start), "samples.jsonl").appendText(sample.toString() + "\n")
        prefs(context).edit().putBoolean("root", root).putString("status", status)
            .putInt("count", state.count + 1).commit()
        return true
    }

    @Synchronized
    fun anchor(context: Context, start: Long, baselineTime: Long) {
        val current = state(context)
        if (current.start != start || current.stopped) return
        val duration = current.end - current.start
        prefs(context).edit().putLong("end", baselineTime + duration)
            .putLong("baselineTime", baselineTime).commit()
    }

    fun requestsRoot(context: Context) = prefs(context).getBoolean("requestRoot", false)
}

internal data class PowerAnalysisState(
    val start: Long, val end: Long, val stopped: Boolean, val root: Boolean,
    val status: String, val count: Int,
) {
    val active: Boolean get() = start > 0 && !stopped
    val due: Boolean get() = active && System.currentTimeMillis() >= end
}
