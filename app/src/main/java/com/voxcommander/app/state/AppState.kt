package com.voxcommander.app.state

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.utils.Strings

/**
 * Centralized application state data class.
 * Contains all UI-relevant state in a single immutable object.
 * Derived properties are calculated based on base state values.
 */
data class AppState(
    // --- VOICE SETTINGS ---
    val voiceProcessor: String,
    val voiceLanguage: String,
    val selectedWhisperModelId: String,
    val selectedVoskModelName: String?,
    val customWhisperModelPath: String?,
    val customVoskModelPaths: Map<String, String>,
    
    // --- INTENT SETTINGS ---
    val aiProcessor: String,
    val selectedLlamaModelId: String,
    val cloudIntelligenceEnabled: Boolean,
    
    // --- WAKE WORD SETTINGS ---
    val wakeWord: String,
    val wakeWordEnabled: Boolean,
    val wakeWordModelPath: String?,
    val isWakeWordServiceListening: Boolean,
    val isVerboseLoggingEnabled: Boolean,
    
    // --- API SETTINGS ---
    val apiKey: String?,
    
    // --- RUNTIME STATE ---
    val voiceState: VoiceState,
    val wakeWordDetected: Boolean,
    
    // --- FALLBACK MODEL SETTINGS ---
    val defaultVoiceFallbackProcessor: String?,
    val defaultVoiceFallbackModel: String?,
    val defaultIntentFallbackProcessor: String?,
    val defaultIntentFallbackModel: String?,
    
    // --- UI SYNC ---
    val refreshTrigger: Int = 0,
    val canDrawOverlays: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    
    // --- DERIVED PROPERTIES (Calculated from base state) ---
    val voiceModelReady: Boolean,
    val intentModelReady: Boolean
) {
    companion object {
        /**
         * Creates initial AppState by reading all values from SettingsManager.
         * Derived properties are calculated immediately.
         */
        fun fromSettings(settingsManager: SettingsManager, context: Context): AppState {
            val voiceProcessor = settingsManager.getVoiceProcessor()
            val voiceLanguage = settingsManager.getVoiceLanguage()
            val selectedWhisperModelId = settingsManager.getSelectedWhisperModelId()
            val selectedVoskModelName = settingsManager.getSelectedVoskModelName()
            val customWhisperModelPath = settingsManager.getCustomWhisperModelPath()
            
            // Calculate voiceModelReady
            val voiceModelReady = when (voiceProcessor) {
                Strings.Processors.WHISPER_CPP,
                Strings.Processors.WHISPER_VULKAN,
                Strings.Processors.WHISPER_NEON -> {
                    settingsManager.isModelDownloaded(selectedWhisperModelId) || customWhisperModelPath != null
                }
                Strings.Processors.VOSK -> {
                    val customVosk = settingsManager.getCustomVoskModelPath(voiceLanguage)
                    if (!customVosk.isNullOrBlank()) {
                        java.io.File(customVosk).exists()
                    } else {
                        !selectedVoskModelName.isNullOrBlank() && settingsManager.isModelDownloaded(selectedVoskModelName)
                    }
                }
                Strings.Processors.GOOGLE,
                Strings.Processors.WHISPER_API -> true
                else -> false
            }
            
            // Calculate intentModelReady
            val aiProcessor = settingsManager.getAiProcessor()
            val selectedLlamaModelId = settingsManager.getSelectedLlamaModelId()
            val intentModelReady = when (aiProcessor) {
                Strings.AiProcessors.NLU_LOCAL -> {
                    settingsManager.isModelDownloaded(selectedLlamaModelId)
                }
                Strings.AiProcessors.GEMINI_NATIVE -> {
                    !settingsManager.isGeminiIncompatible()
                }
                Strings.AiProcessors.OPENAI -> true
                else -> false
            }
            
            // Load custom Vosk paths
            val customVoskModelPaths = mutableMapOf<String, String>()
            val languages = listOf("en", "ro", "de", "fr")
            languages.forEach { lang ->
                settingsManager.getCustomVoskModelPath(lang)?.let { path ->
                    customVoskModelPaths[lang] = path
                }
            }
            
            return AppState(
                voiceProcessor = voiceProcessor,
                voiceLanguage = voiceLanguage,
                selectedWhisperModelId = selectedWhisperModelId,
                selectedVoskModelName = selectedVoskModelName,
                customWhisperModelPath = customWhisperModelPath,
                customVoskModelPaths = customVoskModelPaths,
                aiProcessor = aiProcessor,
                selectedLlamaModelId = selectedLlamaModelId,
                cloudIntelligenceEnabled = settingsManager.isCloudIntelligenceEnabled(),
                wakeWord = settingsManager.getWakeWord(),
                wakeWordEnabled = settingsManager.isWakeWordEnabled(),
                wakeWordModelPath = settingsManager.getWakeWordModelPath(),
                isWakeWordServiceListening = false,
                isVerboseLoggingEnabled = settingsManager.isVerboseLoggingEnabled(),
                apiKey = settingsManager.getApiKey(),
                voiceState = VoiceState.IDLE,
                wakeWordDetected = false,
                defaultVoiceFallbackProcessor = settingsManager.getDefaultVoiceFallbackProcessor(),
                defaultVoiceFallbackModel = settingsManager.getDefaultVoiceFallbackModel(),
                defaultIntentFallbackProcessor = settingsManager.getDefaultIntentFallbackProcessor(),
                defaultIntentFallbackModel = settingsManager.getDefaultIntentFallbackModel(),
                refreshTrigger = 0,
                canDrawOverlays = com.voxcommander.app.utils.PermissionUtils.canDrawOverlays(context),
                hasMicrophonePermission = com.voxcommander.app.utils.PermissionUtils.hasMicrophonePermission(context),
                hasNotificationPermission = com.voxcommander.app.utils.PermissionUtils.hasNotificationPermission(context),
                voiceModelReady = voiceModelReady,
                intentModelReady = intentModelReady
            )
        }
    }
    
    /**
     * Recalculates voiceModelReady based on current state and new processor.
     * Used when voiceProcessor changes.
     */
    fun recalculateVoiceReady(newProcessor: String, settingsManager: SettingsManager): Boolean {
        return when (newProcessor) {
            Strings.Processors.WHISPER_CPP,
            Strings.Processors.WHISPER_VULKAN,
            Strings.Processors.WHISPER_NEON -> {
                settingsManager.isModelDownloaded(selectedWhisperModelId) || customWhisperModelPath != null
            }
            Strings.Processors.VOSK -> {
                val customVosk = settingsManager.getCustomVoskModelPath(voiceLanguage)
                if (!customVosk.isNullOrBlank()) {
                    java.io.File(customVosk).exists()
                } else {
                    !selectedVoskModelName.isNullOrBlank() && settingsManager.isModelDownloaded(selectedVoskModelName)
                }
            }
            Strings.Processors.GOOGLE,
            Strings.Processors.WHISPER_API -> true
            else -> false
        }
    }
    
    /**
     * Recalculates intentModelReady based on current state and new processor.
     * Used when aiProcessor changes.
     */
    fun recalculateIntentReady(newProcessor: String, settingsManager: SettingsManager): Boolean {
        return when (newProcessor) {
            Strings.AiProcessors.NLU_LOCAL -> {
                settingsManager.isModelDownloaded(selectedLlamaModelId)
            }
            Strings.AiProcessors.GEMINI_NATIVE -> {
                !settingsManager.isGeminiIncompatible()
            }
            Strings.AiProcessors.OPENAI -> true
            else -> false
        }
    }
}
