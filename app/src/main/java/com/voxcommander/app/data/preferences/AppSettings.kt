package com.voxcommander.app.data.preferences

import com.voxcommander.app.utils.Strings

/**
 * Immutable snapshot of all persisted application settings.
 * This is the reactive payload emitted by SettingsRepository.
 * AppStateManager combines this with runtime state to produce AppState.
 */
data class AppSettings(
    // --- API / CLOUD ---
    val apiKey: String? = null,

    // --- LANGUAGE ---
    val language: String = Strings.Preferences.DEFAULT_LANGUAGE,
    val voiceLanguage: String = Strings.Preferences.DEFAULT_LANGUAGE,

    // --- VOICE ENGINE ---
    val voiceProcessor: String = Strings.Preferences.DEFAULT_PROCESSOR,
    val activeVoiceModelId: String? = null,

    // --- INTENT ENGINE ---
    val aiProcessor: String = Strings.Preferences.DEFAULT_PROCESSOR,
    val activeIntentModelId: String? = null,
    val cloudIntelligenceEnabled: Boolean = false,

    // --- PER-ENGINE MODEL SELECTIONS ---
    val engineModelSelections: Map<String, String> = emptyMap(),

    // --- WAKE WORD ---
    val wakeWord: String = "hi vosk",
    val wakeWordEnabled: Boolean = false,
    val wakeWordModelPath: String? = null,

    // --- OFFLINE FALLBACK ---
    val offlineFallbackTimeout: Int = 10,
    val defaultOfflineModel: String = "tiny",
    val defaultVoiceFallbackProcessor: String? = null,
    val defaultVoiceFallbackModel: String? = null,
    val defaultIntentFallbackProcessor: String? = null,
    val defaultIntentFallbackModel: String? = null,

    // --- LOGGING ---
    val logLevel: String = "LOGCAT_ONLY",
    val verboseLoggingEnabled: Boolean = false,

    // --- VULKAN ---
    val vulkanIncompatible: Boolean = false,
    val vulkanProbeDone: Boolean = false,
    val vulkanRuntimeAttempt: Boolean = false,
    val vulkanRuntimeVerified: Boolean = false,
    val experimentalVulkanEnabled: Boolean = false,

    // --- GEMINI ---
    val geminiIncompatible: Boolean = false,

    // --- REMOTE REPOSITORY ---
    val modelRepoBaseUrl: String = Strings.Preferences.DEFAULT_MODEL_REPO_URL,
    val modelsJsonCache: String? = null,

    // --- MODEL DOWNLOAD STATE ---
    val downloadedModelIds: Set<String> = emptySet(),
    val customModelPaths: Map<String, String> = emptyMap()
) {
    /**
     * Key for custom model path: "engineKey" or "engineKey_langCode"
     */
    fun customModelPathKey(engineKey: String, langCode: String? = null): String {
        return if (langCode != null) "${engineKey}_$langCode" else engineKey
    }

    fun isModelDownloaded(modelId: String): Boolean = modelId in downloadedModelIds

    fun getCustomModelPath(engineKey: String, langCode: String? = null): String? {
        return customModelPaths[customModelPathKey(engineKey, langCode)]
    }
}
