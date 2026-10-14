package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class SettingsSliderDraftTest {
    @Test fun pointerUpdatesRemainLocalUntilCommitAndOnlyLatestValueIsSaved() {
        val draft = SettingsSliderDraft(10f)
        val saved = mutableListOf<Float>()
        repeat(100) { draft.update(it.toFloat()) }
        assertTrue(saved.isEmpty())
        assertEquals(99f, draft.value, 0f)
        draft.commit { saved.add(it) }
        assertEquals(listOf(99f), saved)
    }

    @Test fun finishTimerAndLifecycleFlushDoNotSaveTheSameValueAgain() {
        val draft = SettingsSliderDraft(10f)
        val saved = mutableListOf<Float>()
        draft.update(20f)
        repeat(3) { draft.commit { saved.add(it) } }
        assertEquals(listOf(20f), saved)
        draft.update(30f)
        draft.commit { saved.add(it) }
        assertEquals(listOf(20f, 30f), saved)
    }

    @Test fun returningToSavedValueDoesNotWrite() {
        val draft = SettingsSliderDraft(10f)
        draft.update(20f)
        draft.update(10f)
        draft.commit { fail("Unchanged values must not trigger persistence") }
    }

    @Test fun externalPresetWinsOverAnObsoleteDraft() {
        val draft = SettingsSliderDraft(10f)
        draft.update(20f)
        draft.commitIfCurrent(40f) { fail("Disposed draft must not overwrite an imported preset") }
        val replacement = SettingsSliderDraft(40f)
        assertEquals(40f, replacement.value, 0f)
        replacement.commit { fail("External values are already saved") }
    }

    @Test fun accessibilityOrLeavingThePageFlushesPendingValue() {
        val draft = SettingsSliderDraft(10f)
        draft.update(25f)
        var saved = 10f
        draft.commitIfCurrent(10f) { saved = it }
        assertEquals(25f, saved, 0f)
    }

    @Test fun failedSaveCanBeRetried() {
        val draft = SettingsSliderDraft(10f)
        draft.update(20f)
        try {
            draft.commit { throw IllegalStateException("Save failed") }
            fail("Expected save failure")
        } catch (_: IllegalStateException) { }
        var saved = 10f
        draft.commit { saved = it }
        assertEquals(20f, saved, 0f)
    }
}
