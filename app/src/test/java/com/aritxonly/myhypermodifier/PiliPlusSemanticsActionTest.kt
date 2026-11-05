package com.aritxonly.myhypermodifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiliPlusSemanticsActionTest {
    @Test fun distinguishesFlutterTapFromAndroidClick() {
        assertTrue(PiliPlusSemanticsBridge.hasTapAction(1))
        assertTrue(PiliPlusSemanticsBridge.hasTapAction(1 or 16))
        assertFalse(PiliPlusSemanticsBridge.hasTapAction(16))
        assertFalse(PiliPlusSemanticsBridge.hasTapAction(0))
    }
}
