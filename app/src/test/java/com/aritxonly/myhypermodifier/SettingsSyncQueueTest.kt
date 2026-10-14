package com.aritxonly.myhypermodifier

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SettingsSyncQueueTest {
    @Test(timeout = 5_000) fun queuedIntermediateSnapshotsAreDiscardedAndLatestRunsOffCallerThread() {
        val executor = Executors.newSingleThreadExecutor()
        val blocked = CountDownLatch(1)
        try {
            executor.execute { blocked.await() }
            val queue = SettingsSyncQueue(executor)
            val caller = Thread.currentThread()
            val writes = mutableListOf<Int>()
            repeat(100) { index -> queue.submit {
                assertNotSame(caller, Thread.currentThread())
                writes.add(index)
                true
            } }
            blocked.countDown()
            assertTrue(queue.flush())
            assertEquals(listOf(99), writes)
        } finally {
            blocked.countDown()
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000) fun changeDuringCommitRetainsTheTrailingSnapshotBeforeRestartBarrier() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val queue = SettingsSyncQueue(executor)
            val writes = mutableListOf<Int>()
            queue.submit {
                started.countDown()
                release.await()
                writes.add(1)
                true
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            queue.submit { writes.add(2); true }
            queue.submit { writes.add(3); true }
            release.countDown()
            assertTrue(queue.flush())
            assertEquals(listOf(1, 3), writes)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test(timeout = 5_000) fun failedCommitBlocksRestartUntilASuccessfulRetry() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val queue = SettingsSyncQueue(executor)
            queue.submit { false }
            assertFalse(queue.flush())
            queue.submit { true }
            assertTrue(queue.flush())
        } finally {
            executor.shutdownNow()
        }
    }
}
