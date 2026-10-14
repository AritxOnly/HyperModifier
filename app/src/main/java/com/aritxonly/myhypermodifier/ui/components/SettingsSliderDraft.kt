package com.aritxonly.myhypermodifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** Keep pointer updates local; persistence only receives a settled or explicitly finished value. */
internal class SettingsSliderDraft(val sourceValue: Float) {
    var value by mutableFloatStateOf(sourceValue)
        private set
    private var committedValue = sourceValue

    fun update(value: Float) { this.value = value }

    fun commit(onCommit: (Float) -> Unit) {
        if (value == committedValue) return
        val settledValue = value
        onCommit(settledValue)
        committedValue = settledValue
    }

    fun commitIfCurrent(externalValue: Float, onCommit: (Float) -> Unit) {
        if (externalValue == sourceValue) commit(onCommit)
    }
}

@Composable
internal fun rememberSettingsSliderDraft(value: Float, onCommit: (Float) -> Unit): SettingsSliderDraft {
    val draft = remember(value) { SettingsSliderDraft(value) }
    val latestCommit by rememberUpdatedState(onCommit)
    val latestExternalValue by rememberUpdatedState(value)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(draft, draft.value) {
        // Miuix accessibility setProgress does not emit onValueChangeFinished.
        delay(150)
        draft.commitIfCurrent(latestExternalValue, latestCommit)
    }
    DisposableEffect(draft, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) draft.commitIfCurrent(latestExternalValue, latestCommit)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // A preset/import can replace this draft before its timer fires. Never replay
            // the obsolete value over that external update when its effect is disposed.
            draft.commitIfCurrent(latestExternalValue, latestCommit)
        }
    }
    return draft
}
