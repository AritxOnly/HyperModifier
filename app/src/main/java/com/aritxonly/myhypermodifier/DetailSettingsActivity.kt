package com.aritxonly.myhypermodifier

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

/**
 * Hosts a single secondary settings page. Keeping details outside the home Activity lets Android
 * animate predictive-back and ordinary back navigation with the platform's native transitions.
 */
class DetailSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = SettingsDestination.fromKey(intent.getStringExtra(EXTRA_DESTINATION))
            ?.takeUnless { it.isTopLevel }
            ?: run {
                finish()
                return
            }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val darkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
        setContent {
            val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationOwner) {
                MyHyperModifierDetailSettingsApp(destination) { finishAfterTransition() }
            }
        }
    }

    @Deprecated("Use the system back gesture or the toolbar action instead.")
    override fun onBackPressed() {
        finishAfterTransition()
    }

    companion object {
        const val EXTRA_DESTINATION = "com.aritxonly.myhypermodifier.detail_destination"
    }
}
