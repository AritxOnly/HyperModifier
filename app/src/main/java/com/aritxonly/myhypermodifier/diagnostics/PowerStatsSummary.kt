package com.aritxonly.myhypermodifier

/** Human-readable --charged summary; unknown vendor formats stay unavailable. */
internal data class PowerStatsSummary(val batteryRealtimeMs: Long?, val screenOnMs: Long?, val statsStart: String?) {
    companion object {
        fun parse(text: String): PowerStatsSummary {
            fun line(prefix: String) = text.lineSequence().firstOrNull { it.trimStart().startsWith(prefix) }
                ?.trim()?.removePrefix(prefix)?.trim()
            return PowerStatsSummary(
                line("Time on battery:")?.substringBefore("realtime")?.substringBefore("(")?.let(::duration),
                line("Screen on:")?.substringBefore("(")?.let(::duration),
                line("Start clock time:"),
            )
        }

        fun duration(value: String): Long? {
            val tokens = Regex("(\\d+)(ms|d|h|m|s)").findAll(value).toList()
            if (tokens.isEmpty()) return null
            return tokens.sumOf {
                it.groupValues[1].toLong() * when (it.groupValues[2]) {
                    "d" -> 86_400_000L; "h" -> 3_600_000L; "m" -> 60_000L; "s" -> 1_000L; else -> 1L
                }
            }
        }
    }

    fun screenOnDelta(after: PowerStatsSummary): Long? {
        // Refuse to attribute across a statistics reset or unknown reset identity.
        if (statsStart == null || statsStart != after.statsStart || screenOnMs == null || after.screenOnMs == null ||
            batteryRealtimeMs == null || after.batteryRealtimeMs == null || after.batteryRealtimeMs < batteryRealtimeMs) return null
        return (after.screenOnMs - screenOnMs).takeIf { it >= 0 }
    }
}
