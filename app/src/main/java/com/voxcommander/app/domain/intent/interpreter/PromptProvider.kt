package com.voxcommander.app.domain.intent.interpreter

import com.voxcommander.app.data.preferences.AppSettings
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.intent.registry.AppRegistry
import com.voxcommander.app.domain.intent.taxonomy.IntentTaxonomy

/**
 * Provides hydrated prompt templates for AI agents.
 * Loads the static template from models.json and injects dynamic placeholders:
 * - ${installedApps}: list of installed apps per domain (with user default markers)
 * - ${spokenText}: user's voice/text command (stripped for system prompt)
 */
object PromptProvider {

    private const val ID_STANDARD_NLU = "standard_nlu"
    private const val PLACEHOLDER_TEXT = "\${spokenText}"
    private const val PLACEHOLDER_APPS = "\${installedApps}"

    /**
     * Returns only the system instructions (without the input line).
     * Used by all engines (OpenAI, Gemini Cloud, Local LLM) — they add user input separately.
     */
    fun getNluSystemPrompt(settings: AppSettings? = null, voiceLanguage: String? = null): String {
        val template = RemoteModelRegistry.getPrompt(ID_STANDARD_NLU) ?: return ""
        val langHint = voiceLanguage?.let { "\nInput language: $it." } ?: ""
        // Strip the Input/JSON suffix — engines add their own user message
        val inputIndex = template.indexOf("Input:")
        val systemPart = if (inputIndex > 0) {
            template.substring(0, inputIndex).trim()
        } else {
            template.replace(PLACEHOLDER_TEXT, "")
        }
        return systemPart
            .replace(PLACEHOLDER_APPS, buildAppsSection(settings))
            .plus(langHint)
    }

    /**
     * Formats the user command as an input line.
     */
    fun formatUserInput(spokenText: String): String {
        return "Input: \"$spokenText\"\nJSON:"
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
