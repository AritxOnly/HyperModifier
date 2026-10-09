package com.aritxonly.myhypermodifier

internal object HookDiagnosticPolicy {
    const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    fun expired(time: Long, now: Long): Boolean = time <= 0 || now - time >= RETENTION_MS

    /** Events arrive newest first; an old failure must not override a later successful install. */
    fun latestFeatures(events: List<HookDiagnostic>): List<HookDiagnostic> =
        events.filter { it.feature != "模块注入" }
            .distinctBy { it.packageName to it.feature }
}
