package com.aritxonly.myhypermodifier

/** Resource names and command IDs verified against Spotify 9.1.80.2221. */
internal object SpotifyMediaActionPolicy {
    fun isFavorite(action: String, iconName: String): Boolean =
        action in setOf("ADD_TO", "CHECK_FILL") || iconName in setOf(
            "mediaservice_vector_plus_alt", "mediaservice_vector_check_alt_fill",
            "encore_icon_heart_24", "encore_icon_heart_active_24",
            "encore_icon_heart_16", "encore_icon_heart_active_16",
        )

    fun isShuffle(action: String): Boolean = action in setOf(
        "TURN_SHUFFLE_ON", "TURN_SHUFFLE_OFF", "TURN_SMART_SHUFFLE_OFF",
    )

    fun shuffleActive(mode: Int): Boolean = mode == 1 || mode == 2

    /** Move the selected native objects intact; their extras carry Spotify command arguments. */
    fun <T> prioritize(original: List<T>, favorite: T?, shuffle: T?): List<T> = buildList {
        favorite?.let(::add)
        shuffle?.let(::add)
        original.forEach { if (it !== favorite && it !== shuffle) add(it) }
    }
}
