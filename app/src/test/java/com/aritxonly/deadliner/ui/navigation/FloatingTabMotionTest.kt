package com.aritxonly.deadliner.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingTabMotionTest {
    @Test
    fun releaseAndCancellationResetPressDuringDrag() {
        val motion = FloatingTabMotionState(0)
        motion.press()
        motion.startDrag()
        motion.dragTo(0.6f, 3, 400f)
        assertTrue(motion.isPressed)
        assertTrue(motion.isDragging)
        motion.finishGesture(1)
        assertEquals(false, motion.isPressed)
        assertEquals(false, motion.isDragging)
        assertEquals(0f, motion.velocityPxPerSecond, 0f)

        motion.press()
        motion.startDrag()
        motion.dragTo(0.3f, 3, -200f)
        motion.cancelGesture()
        assertEquals(false, motion.isPressed)
        assertEquals(false, motion.isDragging)
        assertEquals(0f, motion.velocityPxPerSecond, 0f)
    }

    @Test
    fun targetRoundsContinuousIndicatorPosition() {
        assertEquals(
            2,
            resolveFloatingTabTarget(
                position = 1.56f,
                tabCount = 4,
                enabledIndices = listOf(0, 1, 2, 3),
            ),
        )
        assertEquals(
            1,
            resolveFloatingTabTarget(
                position = 1.44f,
                tabCount = 4,
                enabledIndices = listOf(0, 1, 2, 3),
            ),
        )
    }

    @Test
    fun disabledRoundedTargetUsesNearestEnabledTab() {
        assertEquals(
            3,
            resolveFloatingTabTarget(
                position = 2.1f,
                tabCount = 4,
                enabledIndices = listOf(0, 1, 3),
            ),
        )
    }

    @Test
    fun velocityStretchIsBoundedAndDirectional() {
        assertEquals(0f, floatingTabStretch(0f, 100f), 0.0001f)
        assertEquals(
            FloatingTabMotionDefaults.MaximumStretch,
            floatingTabStretch(10_000f, 100f),
            0.0001f,
        )
        val stretch = floatingTabStretch(400f, 100f)
        assertTrue(floatingTabTransformOriginX(400f, stretch) > 0.5f)
        assertTrue(floatingTabTransformOriginX(-400f, stretch) < 0.5f)
    }

    @Test
    fun committedDragDoesNotReplayWhenExternalSelectionReturns() {
        val sync = FloatingTabSelectionSync()

        sync.markCommitted(2)

        assertEquals(false, sync.shouldAnimateExternalSelection(2))
        assertEquals(true, sync.shouldAnimateExternalSelection(1))
    }

    @Test
    fun panelDragFractionIsNormalizedAndBounded() {
        assertEquals(0.25f, floatingTabPanelDragFraction(100f, 400f), 0.0001f)
        assertEquals(-0.5f, floatingTabPanelDragFraction(-200f, 400f), 0.0001f)
        assertEquals(1f, floatingTabPanelDragFraction(800f, 400f), 0.0001f)
        assertEquals(0f, floatingTabPanelDragFraction(100f, 0f), 0.0001f)
    }

    @Test
    fun rapidSelectionKeepsMomentumOnlyWhilePreviousTransitionIsInFlight() {
        assertTrue(
            floatingTabSelectionIsInterrupted(
                visualPosition = 1.4f,
                previousTarget = 3,
                nextTarget = 0,
                velocityTabsPerSecond = 7f,
            ),
        )
        assertTrue(
            floatingTabSelectionIsInterrupted(
                visualPosition = 2.1f,
                previousTarget = 2,
                nextTarget = 3,
                velocityTabsPerSecond = 4f,
            ),
        )
        assertEquals(
            false,
            floatingTabSelectionIsInterrupted(
                visualPosition = 2f,
                previousTarget = 2,
                nextTarget = 1,
                velocityTabsPerSecond = 0f,
            ),
        )
    }

}
