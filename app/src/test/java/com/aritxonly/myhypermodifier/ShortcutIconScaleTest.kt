package com.aritxonly.myhypermodifier

import android.graphics.Matrix
import android.graphics.Rect
import org.robolectric.annotation.GraphicsMode
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ShortcutIconScaleTest {
    private class Button(context: android.content.Context) : View(context) {
        var writes = 0
        var reads = 0
        override fun getAnimationMatrix(): Matrix? { reads++; return super.getAnimationMatrix() }
        override fun setAnimationMatrix(matrix: Matrix?) { writes++; super.setAnimationMatrix(matrix) }
    }
    private fun button() = Button(RuntimeEnvironment.getApplication()).apply { layout(10, 20, 50, 60) }
    @Test fun baselineAndLargerSmallerOuterSizesScaleAboutIconCenter() {
        assertEquals(54f, ModifierSettings().lockscreenShortcutGlassSize)
        val view = button()
        val scale = ShortcutIconScale(view)
        scale.update(LockscreenBottomPolicy.iconScale(54f))
        assertEquals(0, view.writes)
        scale.update(LockscreenBottomPolicy.iconScale(81f))
        val points = floatArrayOf(0f, 0f, 20f, 20f, 40f, 40f)
        view.animationMatrix!!.mapPoints(points)
        assertArrayEquals(floatArrayOf(-10f, -10f, 20f, 20f, 50f, 50f), points, .001f)
        scale.update(LockscreenBottomPolicy.iconScale(36f))
        val edge = floatArrayOf(40f, 20f)
        view.animationMatrix!!.mapPoints(edge)
        assertEquals(20f + 20f * (36f / 54f), edge[0], .001f)
        scale.update(1f)
        assertNull(view.animationMatrix)
    }
    @Test fun idleFramesDoNotRewriteAndNativeTransformPropertiesAreUntouched() {
        val view = button()
        view.scaleX = .8f; view.scaleY = .9f; view.translationX = 12f
        val nativeMatrix = Matrix().also(view::transformMatrixToGlobal)
        val hitRect = Rect().also(view::getHitRect)
        val scale = ShortcutIconScale(view)
        scale.update(1.5f)
        repeat(20) { scale.update(1.5f) }
        assertEquals(1, view.writes)
        assertEquals(1, view.reads)
        assertEquals(nativeMatrix, Matrix().also(view::transformMatrixToGlobal))
        assertEquals(hitRect, Rect().also(view::getHitRect))
        assertEquals(.8f, view.scaleX)
        assertEquals(.9f, view.scaleY)
        assertEquals(12f, view.translationX)
        assertEquals(40, view.width)
        view.translationX = 16f
        scale.update(1.5f)
        assertEquals(2, view.writes)
        scale.restore()
        assertNull(view.animationMatrix)
    }
    @Test fun existingAnimationMatrixIsComposedAndRestoredWithoutErasingLaterWrites() {
        val view = button()
        val original = Matrix().apply { setTranslate(5f, 7f) }
        view.animationMatrix = original
        val scale = ShortcutIconScale(view)
        scale.update(1.5f)
        val center = floatArrayOf(20f, 20f)
        view.animationMatrix!!.mapPoints(center)
        assertArrayEquals(floatArrayOf(25f, 27f), center, .001f)
        scale.restore()
        assertEquals(original, view.animationMatrix)
        scale.update(1.5f)
        val later = Matrix().apply { setRotate(10f) }
        view.animationMatrix = later
        scale.restore()
        assertEquals(later, view.animationMatrix)
    }
}
