package com.aritxonly.myhypermodifier

import org.junit.Assert.*
import org.junit.Test

class SpotifyMediaActionPolicyTest {
    @Test fun spotifyCollectionCommandsDoNotDependOnTranslatedLabels() {
        assertTrue(SpotifyMediaActionPolicy.isFavorite("ADD_TO", ""))
        assertTrue(SpotifyMediaActionPolicy.isFavorite("CHECK_FILL", ""))
        assertTrue(SpotifyMediaActionPolicy.isFavorite("unknown", "mediaservice_vector_plus_alt"))
        assertTrue(SpotifyMediaActionPolicy.isFavorite("unknown", "mediaservice_vector_check_alt_fill"))
        assertFalse(SpotifyMediaActionPolicy.isFavorite("CHAPTER_LIST", "mediaservice_vector_chapter_list"))
        assertFalse(SpotifyMediaActionPolicy.isFavorite("unknown", "encore_icon_plus_24"))
    }

    @Test fun selectedActionsKeepTheirNativePayloadAndLeaveOtherControlsInOrder() {
        data class Action(val command: String, val extras: Map<String, String>)
        val unrelated = Action("CHAPTER_LIST", emptyMap())
        val favorite = Action("ADD_TO", mapOf("track" to "spotify:track:123"))
        val shuffle = Action("TURN_SHUFFLE_ON", mapOf("source" to "notification"))
        val original = listOf(unrelated, shuffle, favorite)
        val ordered = SpotifyMediaActionPolicy.prioritize(original, favorite, shuffle)
        assertEquals(listOf(favorite, shuffle, unrelated), ordered)
        assertSame(favorite, ordered[0])
        assertSame(shuffle, ordered[1])
        assertEquals(listOf(unrelated, shuffle, favorite), original)
        assertEquals(original, SpotifyMediaActionPolicy.prioritize(original, null, null))
    }

    @Test fun syntheticShuffleCanFillTheSecondSlotWithoutDroppingNativeActions() {
        val favorite = Any()
        val unrelated = Any()
        val shuffle = Any()
        assertEquals(listOf(favorite, shuffle, unrelated), SpotifyMediaActionPolicy.prioritize(
            listOf(unrelated, favorite), favorite, shuffle,
        ))
    }

    @Test fun smartShuffleIsRecognizedAndUnknownModeIsNotActive() {
        assertTrue(SpotifyMediaActionPolicy.isShuffle("TURN_SMART_SHUFFLE_OFF"))
        assertFalse(SpotifyMediaActionPolicy.isShuffle("TURN_REPEAT_ON"))
        assertTrue(SpotifyMediaActionPolicy.shuffleActive(1))
        assertTrue(SpotifyMediaActionPolicy.shuffleActive(2))
        assertFalse(SpotifyMediaActionPolicy.shuffleActive(0))
        assertFalse(SpotifyMediaActionPolicy.shuffleActive(-1))
    }
}
