package com.aritxonly.myhypermodifier

import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.util.TypedValue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class AuthenticatorGridTest {
    @Test fun windowThresholdsDisableAndInvalidDensity() {
        for ((width, expected) in listOf(0 to 1, 599 to 1, 600 to 2, 839 to 2, 840 to 3, 1600 to 3)) {
            assertEquals(expected, AuthenticatorGridPolicy.columns(true, width * 2, 2f))
            assertEquals(1, AuthenticatorGridPolicy.columns(false, width * 2, 2f))
        }
        for (density in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY))
            assertEquals(1, AuthenticatorGridPolicy.columns(true, 1800, density))
        assertEquals(0, AuthenticatorGridPolicy.dragDirections(0, true))
        assertEquals(0x30, AuthenticatorGridPolicy.dragDirections(0x30, false))
        assertEquals(0x33, AuthenticatorGridPolicy.dragDirections(0x30, true))
    }

    @Test fun cardRestoresNativeGeometryAndPreservesContents() {
        val context = RuntimeEnvironment.getApplication()
        val name = TextView(context)
        val code = TextView(context)
        val nameParams = RelativeLayout.LayoutParams(150, 40).apply { marginStart = 24; marginEnd = 24 }
        val codeParams = RelativeLayout.LayoutParams(180, 50).apply {
            marginStart = 24; addRule(RelativeLayout.BELOW, 123)
        }
        name.layoutParams = nameParams; code.layoutParams = codeParams
        code.setTextSize(TypedValue.COMPLEX_UNIT_PX, 38f)
        name.text = "Example account"; code.text = "123 456"
        val style = AuthenticatorGridHooks.RowStyle(name, code)
        style.apply(name, code)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, code.layoutParams.width)
        assertTrue(code.textSize <= 38f)
        style.restore(name, code)
        val restored = code.layoutParams as RelativeLayout.LayoutParams
        assertEquals(180, restored.width)
        assertEquals(24, restored.marginStart)
        assertEquals(123, restored.getRule(RelativeLayout.BELOW))
        assertEquals(38f, code.textSize)
        assertEquals("123 456", code.text.toString())
        assertEquals("Example account", name.text.toString())
    }

    @Test fun restorationDoesNotOverwriteLaterGeometryFromAnotherModule() {
        val context = RuntimeEnvironment.getApplication()
        val name = TextView(context); val code = TextView(context)
        name.layoutParams = RelativeLayout.LayoutParams(100, 40)
        code.layoutParams = RelativeLayout.LayoutParams(100, 40)
        val style = AuthenticatorGridHooks.RowStyle(name, code)
        style.apply(name, code)
        val replacement = RelativeLayout.LayoutParams(222, 70)
        code.layoutParams = replacement
        code.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f)
        style.restore(name, code)
        assertSame(replacement, code.layoutParams)
        assertEquals(19f, code.textSize)
    }

    @Test fun optInSurvivesStorageBundleBackupAndReset() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(ModifierSettings().authenticatorGridEnabled)
        assertFalse(ModifierSettingsPresets.systemDefault().authenticatorGridEnabled)
        assertFalse(ModifierSettingsPresets.moduleDefault().authenticatorGridEnabled)
        val value = ModifierSettings(authenticatorGridEnabled = true)
        ModifierSettingsStore.save(context, value)
        assertTrue(ModifierSettingsStore.load(context).authenticatorGridEnabled)
        assertTrue(ModifierSettingsStore.toBundle(value).getBoolean("authenticator_grid_enabled"))
        val backup = ModifierSettingsStore.backupValues(context)
        ModifierSettingsStore.save(context, ModifierSettingsPresets.systemDefault())
        assertFalse(ModifierSettingsStore.load(context).authenticatorGridEnabled)
        ModifierSettingsStore.restoreBackup(context, backup)
        assertTrue(ModifierSettingsStore.load(context).authenticatorGridEnabled)
        assertTrue(ModuleScopePackage.PACKAGES.contains(ModuleScopePackage.AUTHENTICATOR))
    }
}
