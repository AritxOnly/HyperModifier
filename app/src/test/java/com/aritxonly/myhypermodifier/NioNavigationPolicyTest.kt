package com.aritxonly.myhypermodifier

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NioNavigationPolicyTest {
    private val labels = listOf("发现", "朋友", "爱车", "惊喜", "我的")

    @Test fun hiddenTabsKeepNativePositionsAndAtLeastOneEntryRemains() {
        val mask = NioNavigationPolicy.mask(true, false, true, false, true)
        val positions = NativeBottomBarPolicy.visibleNavigationIndices(labels, -1) { label, _ ->
            NioNavigationPolicy.isVisible(label, mask)
        }
        assertEquals(listOf(0, 2, 4), positions)
        assertEquals(listOf("发现", "爱车", "我的"), positions.map(labels::get))
        assertEquals(listOf(0), NativeBottomBarPolicy.visibleNavigationIndices(labels, -1) { label, _ ->
            NioNavigationPolicy.isVisible(label, 0)
        })
        assertTrue(NioNavigationPolicy.isVisible("服务端新增入口", 0))
    }

    @Test fun eachPageCanBeTheOnlyVisibleEntry() {
        labels.forEachIndexed { selected, _ ->
            val mask = 1 shl selected
            val positions = NativeBottomBarPolicy.visibleNavigationIndices(labels, -1) { label, _ ->
                NioNavigationPolicy.isVisible(label, mask)
            }
            assertEquals(listOf(selected), positions)
        }
    }

    @Test fun visibilityPreferencesLoadAndEnterRemoteBundleAndBackupSchema() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences(ModifierSettingsStore.PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val defaults = ModifierSettingsStore.load(context)
        assertTrue(defaults.nioDiscoverTabVisible && defaults.nioFriendsTabVisible &&
            defaults.nioCarTabVisible && defaults.nioMallTabVisible && defaults.nioMineTabVisible)
        try {
            prefs.edit().putBoolean("nio_friends_tab_visible", false)
                .putBoolean("nio_mall_tab_visible", false).commit()
            val settings = ModifierSettingsStore.load(context)
            assertFalse(settings.nioFriendsTabVisible)
            assertFalse(settings.nioMallTabVisible)
            assertTrue(settings.nioCarTabVisible)
            val bundle = ModifierSettingsStore.toBundle(settings)
            assertFalse(bundle.getBoolean("nio_friends_tab_visible", true))
            assertFalse(bundle.getBoolean("nio_mall_tab_visible", true))
            val backup = ModifierSettingsStore.backupValues(context)
            assertEquals(false, backup["nio_friends_tab_visible"])
            assertEquals(backup, SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup),
                ModifierSettingsStore.backupSchema()))
        } finally { prefs.edit().clear().commit() }
    }
}
