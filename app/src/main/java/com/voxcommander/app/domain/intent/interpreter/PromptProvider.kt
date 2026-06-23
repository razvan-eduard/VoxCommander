package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.remote.RemoteModelRegistry

/**
 * Provides hydrated prompt templates for AI agents.
 * Centralizes instructions to ensure consistency between Cloud and Local engines.
 */
object PromptProvider {

    private const val ID_STANDARD_NLU = "standard_nlu"
    private const val PLACEHOLDER_TEXT = "\${spokenText}"

    /**
     * Returns the primary NLU prompt with the user's command injected.
     */
    fun getNluPrompt(spokenText: String): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: getDefaultNluTemplate()
        return template.replace(PLACEHOLDER_TEXT, spokenText)
    }

    /**
     * Fallback template if models.json is not loaded or key is missing.
     */
    private fun getDefaultNluTemplate(): String {
        return """
            System intent mapping for Vox Commander. Rules:
            1. category: Choose STRICTLY from ["audio", "settings", "maps", "home", "app"].
            2. actionType: Choose STRICTLY from:
               - audio: ["audio_youtube", "audio_spotify", "media_pause", "media_play", "media_next", "media_prev"]
               - settings: ["vol_up", "vol_down", "wifi_toggle", "bluetooth_toggle"]
               - maps: ["waze_nav", "maps_nav"]
            3. RETURN: Return EXCLUSIVELY a JSON object with these 6 keys: category, actionType, artist, track, album, destination.
            
            Input: "$PLACEHOLDER_TEXT"
            JSON:
        """.trimIndent()
    }
}
