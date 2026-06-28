package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.preferences.AppSettings
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.intent.registry.AppRegistry
import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy

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
     * @param spokenText The user's voice/text command.
     * @param settings Current app settings (for injecting installed apps + defaults into prompt).
     */
    fun getNluPrompt(spokenText: String, settings: AppSettings? = null): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: getDefaultNluTemplate(settings)
        return template.replace(PLACEHOLDER_TEXT, spokenText)
    }

    /**
     * Returns only the system instructions (without the input line).
     * Used by cloud engines (OpenAI, Gemini Cloud) that separate system/user messages.
     * @param settings Current app settings (for injecting installed apps + defaults into prompt).
     */
    fun getNluSystemPrompt(settings: AppSettings? = null): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: getDefaultNluTemplate(settings)
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
     * Dynamically injects installed apps and user default app preferences.
     */
    private fun getDefaultNluTemplate(settings: AppSettings? = null): String {
        val appsSection = buildAppsSection(settings)
        return """
            System intent mapping for Vox Commander. Rules:
            1. domain: Choose STRICTLY from ["audio", "settings", "maps", "messaging", "system", "home"].
            2. action: Choose STRICTLY from ["play", "pause", "next", "prev", "volume_up", "volume_down", "wifi_toggle", "bluetooth_toggle", "navigate", "send"].
            3. targetApp: The app the user explicitly mentioned (e.g. "spotify", "youtube", "waze", "google maps"). Use null if not specified.
            4. parameters: A JSON object with relevant fields for the intent (e.g. artist, track, album, destination, contact, query, message_body).
            5. confidence: Your confidence level from 0.0 to 1.0.
            6. DEFAULT: If music platform is not specified, use the user's default audio app if set, otherwise use targetApp="youtube".
            7. RETURN: Return EXCLUSIVELY a JSON object with these 5 keys: domain, action, targetApp, parameters, confidence.
            8. "play X on spotify" → domain="audio", action="play". Never domain="settings" for music.

            Examples:
            Input: "play scorpions on spotify"
            Output: {"domain":"audio","action":"play","targetApp":"spotify","parameters":{"artist":"Scorpions"},"confidence":0.95}

            Input: "play scorpions"
            Output: {"domain":"audio","action":"play","targetApp":null,"parameters":{"artist":"Scorpions"},"confidence":0.9}

            Input: "volume up"
            Output: {"domain":"settings","action":"volume_up","targetApp":null,"parameters":{},"confidence":1.0}

            $appsSection

            Input: "$PLACEHOLDER_TEXT"
            JSON:
        """.trimIndent()
    }

    /**
     * Builds a section listing installed apps per domain and user default app selections.
     * This helps the LLM generate more refined targetApp values.
     */
    private fun buildAppsSection(settings: AppSettings?): String {
        val sb = StringBuilder()
        sb.appendLine("Available installed apps:")

        for (domain in IntentTaxonomy.Domains.ALL) {
            val apps = AppRegistry.getInstalledAppsForDomain(domain)
            if (apps.isEmpty()) continue

            val defaultPkg = settings?.defaultAppPackages?.get(domain)
            val defaultApp = apps.find { it.packageName == defaultPkg }

            sb.appendLine("  $domain:")
            for (app in apps) {
                val isDefault = defaultApp != null && app.packageName == defaultApp.packageName
                val marker = if (isDefault) " [USER DEFAULT]" else ""
                sb.appendLine("    - ${app.displayName} (package: ${app.packageName})$marker")
            }
        }

        return sb.toString().trim()
    }
}
