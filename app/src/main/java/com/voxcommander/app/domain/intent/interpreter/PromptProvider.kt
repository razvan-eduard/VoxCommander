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
     * Returns the full NLU prompt with the user's command injected.
     * Used by local LLM engines (MediaPipe) that take a single combined prompt.
     */
    fun getNluPrompt(spokenText: String): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: getDefaultNluTemplate()
        return template.replace(PLACEHOLDER_TEXT, spokenText)
    }

    /**
     * Returns only the system instructions (without the input line).
     * Used by cloud engines (OpenAI, Gemini Cloud) that separate system/user messages.
     */
    fun getNluSystemPrompt(): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: getDefaultNluTemplate()
        // Strip the Input/JSON suffix — cloud engines add their own user message
        val inputIndex = template.indexOf("Input:")
        return if (inputIndex > 0) {
            template.substring(0, inputIndex).trim()
        } else {
            template.replace(PLACEHOLDER_TEXT, "")
        }
    }

    /**
     * Formats the user command as an input line for cloud engines.
     */
    fun formatUserInput(spokenText: String): String {
        return "Input: \"$spokenText\"\nJSON:"
    }

    /**
     * Fallback template if models.json is not loaded or key is missing.
     * Uses the new flexible NluIntent schema (domain, action, targetApp, parameters, confidence).
     */
    private fun getDefaultNluTemplate(): String {
        return """
            System intent mapping for Vox Commander. Rules:
            1. domain: Choose STRICTLY from ["audio", "settings", "maps", "messaging", "system", "home"].
            2. action: Choose STRICTLY from ["play", "pause", "next", "prev", "volume_up", "volume_down", "wifi_toggle", "bluetooth_toggle", "navigate", "send"].
            3. targetApp: The app the user explicitly mentioned (e.g. "spotify", "youtube", "waze", "google maps"). Use null if not specified.
            4. parameters: A JSON object with relevant fields for the intent (e.g. artist, track, album, destination, contact, query, message_body).
            5. confidence: Your confidence level from 0.0 to 1.0.
            6. DEFAULT: If music platform is not specified, use targetApp="youtube".
            7. RETURN: Return EXCLUSIVELY a JSON object with these 5 keys: domain, action, targetApp, parameters, confidence.

            Input: "$PLACEHOLDER_TEXT"
            JSON:
        """.trimIndent()
    }
}
