package com.aritxonly.myhypermodifier

internal object HookDiagnosticPolicy {
    const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    fun expired(time: Long, now: Long): Boolean = time <= 0 || now - time >= RETENTION_MS

    /** Presentation only: retain originals in the diagnostic store for investigation. */
    fun errorGroups(events: List<HookDiagnostic>): Pair<List<HookDiagnostic>, List<HookDiagnostic>> {
        val ordered = events.sortedByDescending { it.time }
        val latest = ordered.distinctBy { it.packageName to it.feature }
            .associateBy { it.packageName to it.feature }
        return ordered.filter { it.warning }
            .distinctBy { Triple(it.packageName, it.feature, it.detail) }
            .partition { latest[it.packageName to it.feature]?.warning != false }
    }

    /** Events arrive newest first; an old failure must not override a later successful install. */
    fun latestFeatures(events: List<HookDiagnostic>): List<HookDiagnostic> =
        events.filter { it.feature != "模块注入" }
            .distinctBy { it.packageName to it.feature }
}
