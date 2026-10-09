package com.aritxonly.myhypermodifier

import android.app.Application
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import java.util.WeakHashMap

/** Retains the native action objects so Spotify still owns icons, arguments and callbacks. */
internal object SpotifyMediaSessionBridge {
    private val diagnostics = WeakHashMap<Any, String>()

    @JvmStatic
    fun augmentPlaybackState(session: Any?, state: PlaybackState?): PlaybackState? {
        session ?: return state
        if (state == null) {
            synchronized(diagnostics) { diagnostics.remove(session) }
            return null
        }
        ModuleSettings.ensureLoaded()
        return runCatching {
            val favoriteEnabled = ModuleSettings.moduleHooksEnabled && ModuleSettings.spotifyFavoriteButtonEnabled
            val shuffleEnabled = ModuleSettings.moduleHooksEnabled && ModuleSettings.spotifyShuffleButtonEnabled
            val original = state.customActions.orEmpty()
            if (!favoriteEnabled && !shuffleEnabled) {
                logState(session, "framework; favorite=false, shuffle=false; native=${original.map { it.action }}")
                return@runCatching state
            }
            val context = currentApplication() ?: return@runCatching state
            val favorite = original.firstOrNull { value ->
                val iconName = runCatching { context.resources.getResourceEntryName(value.icon) }.getOrDefault("")
                SpotifyMediaActionPolicy.isFavorite(value.action, iconName)
            }.takeIf { favoriteEnabled }
            val shuffle = original.firstOrNull { SpotifyMediaActionPolicy.isShuffle(it.action) }
                .takeIf { shuffleEnabled }
            val ordered = SpotifyMediaActionPolicy.prioritize(original, favorite, shuffle)
            logState(session, "framework; favorite=$favoriteEnabled, shuffle=$shuffleEnabled, " +
                "native=${original.map { it.action }}, published=${ordered.map { it.action }}")
            if (ordered == original) return@runCatching state

            // Rebuild with public framework APIs. The copy builder has no clearCustomActions;
            // rebuilding avoids hidden-field access while retaining every PlaybackState field.
            PlaybackState.Builder()
                .setState(state.state, state.position, state.playbackSpeed, state.lastPositionUpdateTime)
                .setBufferedPosition(state.bufferedPosition)
                .setActions(state.actions)
                .setActiveQueueItemId(state.activeQueueItemId)
                .setErrorMessage(state.errorMessage)
                .setExtras(state.extras?.let(::Bundle))
                .apply { ordered.forEach(::addCustomAction) }
                .build()
        }.onFailure { Log.w(TAG, "Could not augment Spotify framework PlaybackState", it) }.getOrDefault(state)
    }

    private fun logState(session: Any, detail: String) {
        val changed = synchronized(diagnostics) {
            if (diagnostics[session] == detail) false else {
                diagnostics[session] = detail
                true
            }
        }
        if (changed) Log.i(TAG, "Spotify MediaSession: $detail")
    }

    private fun currentApplication(): Application? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application
    }.getOrNull()

    private const val TAG = "MyHyperModifier"
}
