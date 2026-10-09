package com.aritxonly.myhypermodifier

internal object PowerAnalysisPolicy {
    const val MAX_SAMPLE_GAP_MS = 45 * 60 * 1000L

    fun sleepMs(elapsedDelta: Long, uptimeDelta: Long): Long? =
        if (elapsedDelta <= 0 || uptimeDelta < 0 || uptimeDelta > elapsedDelta) null else elapsedDelta - uptimeDelta

    fun shouldFinish(start: Long, requestedStart: Long, stopped: Boolean, end: Long, now: Long, manual: Boolean): Boolean =
        start > 0 && start == requestedStart && !stopped && (manual || now >= end)

    /** Only integrate adjacent discharging samples; gaps and reboots break continuity. */
    fun dischargedMah(a: PowerSample, b: PowerSample): Double? {
        val elapsed = b.time - a.time
        if (elapsed <= 0 || elapsed > MAX_SAMPLE_GAP_MS || b.uptime <= a.uptime ||
            kotlin.math.abs((b.uptime - a.uptime) - elapsed) > 60_000 ||
            a.plugged != 0 || b.plugged != 0 || a.chargeUah == null || b.chargeUah == null) return null
        val delta = a.chargeUah - b.chargeUah
        return if (delta >= 0) delta / 1000.0 else null
    }
}

internal data class PowerSample(
    val time: Long,
    val uptime: Long,
    val plugged: Int,
    val chargeUah: Int?,
    val interactive: Boolean,
)
