package com.aritxonly.myhypermodifier

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** Live LSPosed connection and scope snapshot exposed to the settings UI. */
internal object ModuleFrameworkState {
    const val MIN_SUPPORTED_API = 102

    data class Snapshot(
        val connected: Boolean = false,
        val apiVersion: Int = 0,
        val frameworkName: String = "",
        val frameworkVersion: String = "",
        val frameworkVersionCode: Long = 0L,
        val scope: Set<String> = emptySet(),
    ) {
        fun isActive(packageName: String): Boolean =
            connected && apiVersion >= MIN_SUPPORTED_API && packageName in scope
    }

    private val mutableSnapshot = mutableStateOf(Snapshot())
    val snapshot: State<Snapshot> = mutableSnapshot

    fun onServiceBound(
        apiVersion: Int,
        frameworkName: String,
        frameworkVersion: String,
        frameworkVersionCode: Long,
        scope: List<String>,
    ) {
        mutableSnapshot.value = Snapshot(
            connected = true,
            apiVersion = apiVersion,
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            frameworkVersionCode = frameworkVersionCode,
            scope = scope.toSet(),
        )
    }

    fun onServiceDied() {
        mutableSnapshot.value = Snapshot()
    }
}
