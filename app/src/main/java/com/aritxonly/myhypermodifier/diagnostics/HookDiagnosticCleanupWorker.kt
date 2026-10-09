package com.aritxonly.myhypermodifier

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class HookDiagnosticCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = if (HookDiagnosticStore.prune(applicationContext))
        Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "hook-diagnostic-cleanup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HookDiagnosticCleanupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
