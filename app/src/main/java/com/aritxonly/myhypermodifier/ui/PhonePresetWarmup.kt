package com.aritxonly.myhypermodifier

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare auto-selected images when opening the module; hooked Settings never downloads on its UI thread. */
@Composable
internal fun PhonePresetWarmup(settings: ModifierSettings) {
    val context = LocalContext.current
    LaunchedEffect(settings.aboutPhoneImageSource, settings.aboutPhonePresetUrl) {
        try { withContext(Dispatchers.IO) { PhonePresetRepository.ensureSelected(context, settings) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Log.w("MyHyperModifier", "Online phone preset unavailable; retaining cache", failure) }
    }
}
