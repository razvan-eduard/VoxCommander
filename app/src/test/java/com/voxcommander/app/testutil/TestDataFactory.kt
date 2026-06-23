package com.voxcommander.app.testutil

import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.domain.intent.model.IntentPayload

/**
 * Object Mother / Test Data Factory for Vox Commander tests.
 * Centralizes object creation with sensible defaults to improve test readability
 * and maintainability when data structures change.
 */
object TestDataFactory {

    // --- INTENT PAYLOADS ---

    fun createIntentPayload(
        category: String = "music",
        actionType: String = "play",
        artist: String? = null,
        track: String? = null,
        album: String? = null,
        destination: String? = null
    ) = IntentPayload(
        category = category,
        actionType = actionType,
        artist = artist,
        track = track,
        album = album,
        destination = destination
    )

    fun createMusicPayload(
        artist: String? = "Smiley",
        track: String? = "Perfect"
    ) = createIntentPayload(
        category = "music",
        actionType = "play",
        artist = artist,
        track = track
    )

    fun createNavigationPayload(
        destination: String = "Brasov"
    ) = createIntentPayload(
        category = "navigation",
        actionType = "navigate",
        destination = destination
    )

    // --- FAST MAP RULES ---

    fun createFastMapRule(
        id: Long = 1L,
        category: String = "MUSIC",
        actionType: String = "PLAY",
        triggerPattern: String = "pune muzica de la (.*)",
        artist: String? = null,
        track: String? = null,
        album: String? = null,
        destination: String? = null
    ) = FastMapRule(
        id = id,
        category = category,
        actionType = actionType,
        triggerPattern = triggerPattern,
        artist = artist,
        track = track,
        album = album,
        destination = destination
    )
}
