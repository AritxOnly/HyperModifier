package com.aritxonly.myhypermodifier

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Adds Spotify-owned actions to PlaybackState without taking ownership of its MediaSession. */
internal object SpotifyMediaSessionBridge {
    private data class NativeAction(
        val action: String,
        val name: CharSequence,
        val icon: Int,
        val extras: Bundle?,
    )

    private data class SessionSnapshot(
        val favoriteDelegate: NativeAction?,
        val shuffleActive: Boolean,
    )

    private val lock = Any()
    private val sessions = WeakHashMap<Any, SessionSnapshot>()
    private val callbackSessions = WeakHashMap<Any, WeakReference<Any>>()

    @JvmStatic
    fun bindCallback(session: Any?, callback: Any?) {
        if (session == null || callback == null) return
        synchronized(lock) {
            callbackSessions[callback] = WeakReference(session)
        }
    }

    @JvmStatic
    fun augmentPlaybackState(session: Any?, state: Any?): Any? {
        if (session == null || state == null) return state
        ModuleSettings.ensureLoaded()
        return runCatching {
            val context = currentApplication() ?: return@runCatching state
            val stateClass = state.javaClass
            val originalActions = (stateClass.getMethod("getCustomActions").invoke(state) as? List<*>)
                .orEmpty()
                .filterNotNull()
                .filterNot { value ->
                    readNativeAction(value)?.action in setOf(ACTION_FAVORITE, ACTION_SHUFFLE)
                }
            val favoriteEntry = originalActions.firstNotNullOfOrNull { value ->
                readNativeAction(value)
                    ?.takeIf { it.isSpotifyFavorite(context) }
                    ?.let { value to it }
            }
            val favorite = favoriteEntry?.second
            val shuffleActive = readShuffleActive(session, state)
            synchronized(lock) {
                sessions[session] = SessionSnapshot(favorite, shuffleActive)
            }

            val orderedActions = buildList<Any> {
                if (ModuleSettings.spotifyFavoriteButtonEnabled && favorite != null) {
                    add(buildCustomAction(stateClass, ACTION_FAVORITE, favorite.name, favorite.icon))
                }
                if (ModuleSettings.spotifyShuffleButtonEnabled) {
                    val shuffleIcon = context.resources.getIdentifier(
                        "encore_icon_shuffle_24",
                        "drawable",
                        context.packageName,
                    )
                    if (shuffleIcon != 0) {
                        val titleResource = if (shuffleActive) {
                            "np_content_desc_shuffle_active"
                        } else {
                            "np_content_desc_shuffle_inactive"
                        }
                        val titleId = context.resources.getIdentifier(
                            titleResource,
                            "string",
                            context.packageName,
                        )
                        val title = titleId.takeIf { it != 0 }?.let(context::getString)
                            ?: if (shuffleActive) "关闭随机播放" else "随机播放"
                        add(buildCustomAction(stateClass, ACTION_SHUFFLE, title, shuffleIcon))
                    }
                }
                originalActions.forEach { value ->
                    if (value !== favoriteEntry?.first ||
                        !ModuleSettings.spotifyFavoriteButtonEnabled
                    ) add(value)
                }
            }
            if (orderedActions == originalActions) return@runCatching state

            val builderClass = Class.forName(
                "${stateClass.name}\$Builder",
                false,
                stateClass.classLoader,
            )
            val builder = builderClass.getConstructor(stateClass).newInstance(state)
            val actionsField = builderClass.getDeclaredField("customActions").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val builderActions = actionsField.get(builder) as MutableList<Any>
            builderActions.clear()
            builderActions.addAll(orderedActions)
            builderClass.getMethod("build").invoke(builder)
        }.onFailure {
            Log.w(TAG, "Could not augment Spotify MediaSession actions", it)
        }.getOrDefault(state)
    }

    @JvmStatic
    fun dispatchCustomAction(callback: Any?, action: String?, extras: Bundle?): Boolean {
        if (callback == null || action == null) return false
        val session = synchronized(lock) { callbackSessions[callback]?.get() }
            ?: readField(callback, "h")
        val snapshot = synchronized(lock) { session?.let(sessions::get) }
        return when (action) {
            ACTION_FAVORITE -> {
                val delegate = snapshot?.favoriteDelegate ?: return true
                runCatching {
                    callback.javaClass.getMethod(
                        "onCustomAction",
                        String::class.java,
                        Bundle::class.java,
                    ).invoke(callback, delegate.action, delegate.extras?.let(::Bundle))
                }.onFailure {
                    Log.w(TAG, "Could not dispatch Spotify favorite MediaSession action", it)
                }
                true
            }
            ACTION_SHUFFLE -> {
                val nextMode = if (snapshot?.shuffleActive == true) SHUFFLE_NONE else SHUFFLE_ALL
                if (session != null && snapshot != null) {
                    synchronized(lock) {
                        sessions[session] = snapshot.copy(shuffleActive = nextMode != SHUFFLE_NONE)
                    }
                }
                runCatching {
                    callback.javaClass.getMethod(
                        "onSetShuffleMode",
                        Int::class.javaPrimitiveType,
                    ).invoke(callback, nextMode)
                }.onFailure {
                    Log.w(TAG, "Could not dispatch Spotify shuffle MediaSession action", it)
                }
                true
            }
            else -> false
        }
    }

    private fun readNativeAction(value: Any?): NativeAction? {
        value ?: return null
        return runCatching {
            NativeAction(
                action = value.javaClass.getMethod("getAction").invoke(value) as String,
                name = value.javaClass.getMethod("getName").invoke(value) as CharSequence,
                icon = value.javaClass.getMethod("getIcon").invoke(value) as Int,
                extras = (value.javaClass.getMethod("getExtras").invoke(value) as? Bundle)
                    ?.let(::Bundle),
            )
        }.getOrNull()
    }

    private fun NativeAction.isSpotifyFavorite(context: Context): Boolean {
        val resources = context.resources
        val heartIcons = listOf(
            "encore_icon_heart_24",
            "encore_icon_heart_active_24",
            "encore_icon_heart_16",
            "encore_icon_heart_active_16",
        ).map { resources.getIdentifier(it, "drawable", context.packageName) }.filter { it != 0 }
        if (icon in heartIcons) return true
        val identity = "$action $name".lowercase()
        return FAVORITE_IDENTIFIERS.any(identity::contains)
    }

    private fun readShuffleActive(session: Any, state: Any): Boolean {
        val extras = runCatching {
            state.javaClass.getMethod("getExtras").invoke(state) as? Bundle
        }.getOrNull()
        if (extras?.containsKey(SPOTIFY_SHUFFLE_EXTRA) == true) {
            return extras.getBoolean(SPOTIFY_SHUFFLE_EXTRA)
        }
        return runCatching {
            val controller = session.javaClass.getMethod("getController").invoke(session)
            (controller.javaClass.getMethod("getShuffleMode").invoke(controller) as Int) != SHUFFLE_NONE
        }.getOrDefault(false)
    }

    private fun buildCustomAction(
        stateClass: Class<*>,
        action: String,
        name: CharSequence,
        icon: Int,
    ): Any {
        val customActionClass = Class.forName(
            "${stateClass.name}\$CustomAction",
            false,
            stateClass.classLoader,
        )
        val builderClass = Class.forName(
            "${customActionClass.name}\$Builder",
            false,
            stateClass.classLoader,
        )
        val builder = builderClass.getConstructor(
            String::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType,
        ).newInstance(action, name, icon)
        return builderClass.getMethod("build").invoke(builder)
    }

    private fun currentApplication(): Application? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Application
    }.getOrNull()

    private fun readField(target: Any, name: String): Any? = runCatching {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    }.getOrNull()

    private const val ACTION_FAVORITE =
        "com.aritxonly.myhypermodifier.spotify.action.TOGGLE_FAVORITE"
    private const val ACTION_SHUFFLE =
        "com.aritxonly.myhypermodifier.spotify.action.TOGGLE_SHUFFLE"
    private const val SPOTIFY_SHUFFLE_EXTRA = "com.spotify.music.extra.IS_SHUFFLE_ACTIVE"
    private const val SHUFFLE_NONE = 0
    private const val SHUFFLE_ALL = 1
    private const val TAG = "MyHyperModifier"
    private val FAVORITE_IDENTIFIERS = listOf(
        "favorite", "favourite", "liked", "like track", "save track",
        "add to your library", "remove from your library", "收藏", "喜欢",
    )
}
