package com.aritxonly.myhypermodifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicNavigationPolicyTest {
    @Test
    fun visibilityFollowsThePhoneDockDownwardHideAnimation() {
        assertTrue(musicNavigationVisible(
            shown = true,
            height = 60,
            translationY = 0f,
        ))
        assertTrue(musicNavigationVisible(
            shown = true,
            height = 60,
            translationY = -18f,
        ))
        assertFalse(musicNavigationVisible(
            shown = true,
            height = 60,
            translationY = 50f,
        ))
        assertFalse(musicNavigationVisible(
            shown = false,
            height = 60,
            translationY = 0f,
        ))
    }
}
