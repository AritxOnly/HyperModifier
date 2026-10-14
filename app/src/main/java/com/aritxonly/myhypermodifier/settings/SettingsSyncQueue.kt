package com.aritxonly.myhypermodifier

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Serializes writes and drops superseded queued snapshots, while preserving the trailing write. */
internal class SettingsSyncQueue(
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MyHyperModifier-preferences-sync").apply { isDaemon = true }
    },
) {
    private val generation = AtomicLong()
    private val schedulingLock = Any()
    @Volatile private var succeeded = true

    fun submit(write: () -> Boolean) {
        synchronized(schedulingLock) {
            val requested = generation.incrementAndGet()
            executor.execute {
                if (requested == generation.get()) succeeded = write()
            }
        }
    }

    /** A barrier after all previously requested writes. Never call from this queue's worker. */
    fun flush(timeoutMs: Long = 5_000): Boolean {
        val barrier = synchronized(schedulingLock) { executor.submit<Boolean> { succeeded } }
        return barrier.get(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
