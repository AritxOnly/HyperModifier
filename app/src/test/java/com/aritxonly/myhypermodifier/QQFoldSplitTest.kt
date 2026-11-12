package com.aritxonly.myhypermodifier

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class QQFoldSplitTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun view(value: Float) = View(context).apply {
        layoutParams = ConstraintLayout.LayoutParams(0, -1).apply { horizontalWeight = value }
    }
    private fun weight(view: View) = (view.layoutParams as ConstraintLayout.LayoutParams).horizontalWeight
    private fun setWeight(view: View, value: Float) {
        (view.layoutParams as ConstraintLayout.LayoutParams).horizontalWeight = value
    }
    private fun controller(vararg views: View) = QQFoldSplitWeights(
        ConstraintLayout.LayoutParams::class.java.getField("horizontalWeight"), views[0], views[1], views[2])

    @Test fun changesAllRightContainersAndRestoresNativeWeights() {
        val left = view(.5f); val chat = view(.5f); val function = view(.5f)
        val weights = controller(left, chat, function)
        assertTrue(weights.apply())
        assertEquals(.4f, weight(left)); assertEquals(.6f, weight(chat)); assertEquals(.6f, weight(function))
        assertEquals(0, left.layoutParams.width)
        assertTrue(weights.apply()) // Coalesced refresh must not capture our values as stock.
        weights.restore()
        listOf(left, chat, function).forEach { assertEquals(.5f, weight(it)) }
        assertTrue(weights.apply()) // Re-enable in the existing native split.
    }

    @Test fun refusesTabletAndOtherModulesWithoutPartialChanges() {
        val left = view(.3f); val chat = view(.7f); val function = view(.7f)
        assertFalse(controller(left, chat, function).apply())
        assertEquals(.3f, weight(left)); assertEquals(.7f, weight(chat))
        setWeight(left, .5f); setWeight(chat, .5f); setWeight(function, .65f)
        assertFalse(controller(left, chat, function).apply())
        assertEquals(.5f, weight(left)); assertEquals(.5f, weight(chat))
    }

    @Test fun disableDoesNotUndoNativeFullWidthOrAnotherModule() {
        val left = view(.5f); val chat = view(.5f); val function = view(.5f)
        val weights = controller(left, chat, function)
        assertTrue(weights.apply())
        setWeight(left, 1f); left.layoutParams.width = -1 // QQ folded/fullscreen reset.
        setWeight(chat, .7f) // Another module changed this after us.
        weights.restore()
        assertEquals(1f, weight(left)); assertEquals(-1, left.layoutParams.width)
        assertEquals(.7f, weight(chat)); assertEquals(.5f, weight(function))
    }

    @Test fun nativeResetBetweenAppliesKeepsOriginalRestoration() {
        val views = arrayOf(view(.5f), view(.5f), view(.5f))
        val weights = controller(*views)
        assertTrue(weights.apply())
        views.forEach { setWeight(it, .5f) }
        assertTrue(weights.apply())
        weights.restore()
        views.forEach { assertEquals(.5f, weight(it)) }
    }

    @Test fun nativeConstraintChainMeasuresFortySixtyAndRestoresEqualSplit() {
        val root = ConstraintLayout(context)
        val left = view(.5f).apply { id = 1 }
        val chat = view(.5f).apply { id = 2 }
        val function = view(.5f).apply { id = 3 }
        (left.layoutParams as ConstraintLayout.LayoutParams).apply {
            startToStart = 0; endToStart = 3; topToTop = 0; bottomToBottom = 0
        }
        listOf(chat, function).forEach { view ->
            (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                startToEnd = 1; endToEnd = 0; topToTop = 0
            }
        }
        listOf(left, chat, function).forEach(root::addView)
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1000, 600)
        }
        layout()
        assertEquals(500, left.width)
        val weights = controller(left, chat, function)
        assertTrue(weights.apply())
        layout()
        assertEquals(400, left.width); assertEquals(600, chat.width); assertEquals(600, function.width)
        weights.restore()
        layout()
        assertEquals(500, left.width); assertEquals(500, chat.width); assertEquals(500, function.width)
    }

    @Test fun independentOptInPersistsAndDefaultsRemainOff() {
        assertFalse(ModifierSettings().qqFoldSplitRatioEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().qqFoldSplitRatioEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().qqFoldSplitRatioEnabled)
        val chosen = ModifierSettings(qqFoldSplitRatioEnabled = true, qqFloatingNavigationEnabled = false)
        ModifierSettingsStore.save(context, chosen)
        assertTrue(ModifierSettingsStore.load(context).qqFoldSplitRatioEnabled)
        assertFalse(ModifierSettingsStore.load(context).qqFloatingNavigationEnabled)
        assertTrue(ModifierSettingsStore.toBundle(chosen).getBoolean("qq_fold_split_ratio_enabled"))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).qqFoldSplitRatioEnabled)
        assertFalse(ModifierSettingsStore.load(context).qqFloatingNavigationEnabled)
    }
}
