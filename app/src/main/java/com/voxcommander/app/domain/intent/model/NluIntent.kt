package com.voxcommander.app.domain.intent.model

/**
 * Universal NLU intent produced by all interpreters (LLM + FastMap).
 * Decouples intent parsing from intent execution.
 *
 * @param domain       Broad category: "audio", "settings", "maps", "messaging", "system", "home"
 * @param action       Specific action: "play", "pause", "next", "prev", "volume_up", "volume_down",
 *                     "wifi_toggle", "bluetooth_toggle", "navigate", "send"
 * @param targetApp    Explicitly requested app (e.g. "spotify", "youtube", "waze"). null = use default.
 * @param parameters   Flexible key-value map (artist, track, album, destination, contact, query, etc.)
 * @param confidence   LLM confidence 0.0–1.0. FastMap rules always 1.0.
 */
data class NluIntent(
    val domain: String,
    val action: String,
    val targetApp: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f
) {
    fun param(key: String): String? = parameters[key]

    companion object {
        const val PARAM_ARTIST = "artist"
        const val PARAM_TRACK = "track"
        const val PARAM_ALBUM = "album"
        const val PARAM_DESTINATION = "destination"
        const val PARAM_CONTACT = "contact"
        const val PARAM_QUERY = "query"
        const val PARAM_MESSAGE = "message_body"
    }
}
