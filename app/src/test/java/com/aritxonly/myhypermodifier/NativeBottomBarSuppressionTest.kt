package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class NativeBottomBarSuppressionTest {
    @Test fun adsDialogsAndResumeNeverReviveTheOriginalBilibiliDock() {
        // Home -> ad -> home -> dialog -> background -> home.
        val overlayStates = listOf(true, false, true, false, false, true)
        assertTrue(overlayStates.all { visible ->
            NativeBottomBarPolicy.shouldSuppressNative(true, visible, true)
        })
    }

    @Test fun disablingReplacementRestoresTheNativeDockEvenDuringACover() {
        assertTrue(NativeBottomBarPolicy.shouldSuppressNative(true, false, true))
        assertFalse(NativeBottomBarPolicy.shouldSuppressNative(false, false, true))
        assertFalse(NativeBottomBarPolicy.shouldSuppressNative(false, true, true))
    }

    @Test fun otherAdaptersKeepTheirExistingFallbackBehavior() {
        assertTrue(NativeBottomBarPolicy.shouldSuppressNative(true, true, false))
        assertFalse(NativeBottomBarPolicy.shouldSuppressNative(true, false, false))
        assertFalse(NativeBottomBarPolicy.shouldSuppressNative(false, true, false))
    }
}
