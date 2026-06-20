package com.voxcommander.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.voxcommander.app.utils.Strings

class SettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun saveLanguage(langCode: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun saveVoiceLanguage(langCode: String) {
        sharedPreferences.edit().putString(KEY_VOICE_LANGUAGE, langCode).apply()
    }

    fun getVoiceLanguage(): String {
        return sharedPreferences.getString(KEY_VOICE_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun saveVoiceProcessor(processor: String) {
        sharedPreferences.edit().putString(KEY_VOICE_PROCESSOR, processor).apply()
    }

    fun getVoiceProcessor(): String {
        return sharedPreferences.getString(KEY_VOICE_PROCESSOR, DEFAULT_PROCESSOR) ?: DEFAULT_PROCESSOR
    }

    fun setModelDownloaded(modelKey: String, isDownloaded: Boolean) {
        sharedPreferences.edit().putBoolean("${KEY_MODEL_DOWNLOADED_PREFIX}$modelKey", isDownloaded).apply()
    }

    fun isModelDownloaded(modelKey: String): Boolean {
        return sharedPreferences.getBoolean("${KEY_MODEL_DOWNLOADED_PREFIX}$modelKey", false)
    }

    fun clearUnusedModelFlags(activeVoskModelName: String, activeWhisperId: String) {
        val all = sharedPreferences.all
        val activeVoskKey = "${KEY_MODEL_DOWNLOADED_PREFIX}$activeVoskModelName"
        val activeWhisperKey = "${KEY_MODEL_DOWNLOADED_PREFIX}$activeWhisperId"
        
        sharedPreferences.edit().let { edit ->
            all.keys.forEach { key ->
                if (key.startsWith(KEY_MODEL_DOWNLOADED_PREFIX) && 
                    key != activeVoskKey && 
                    key != activeWhisperKey) {
                    edit.remove(key)
                }
            }
            edit.apply()
        }
    }

    fun saveCustomVoskModelPath(langCode: String, path: String) {
        sharedPreferences.edit().putString("${KEY_CUSTOM_VOSK_MODEL_PATH}_$langCode", path).apply()
    }

    fun getCustomVoskModelPath(langCode: String): String? {
        return sharedPreferences.getString("${KEY_CUSTOM_VOSK_MODEL_PATH}_$langCode", null)
    }

    fun saveCustomWhisperModelPath(path: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_WHISPER_MODEL_PATH, path).apply()
    }

    fun getCustomWhisperModelPath(): String? {
        return sharedPreferences.getString(KEY_CUSTOM_WHISPER_MODEL_PATH, null)
    }

    fun saveSelectedWhisperModelId(modelId: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_WHISPER_MODEL_ID, modelId).apply()
    }

    fun getSelectedWhisperModelId(): String {
        return sharedPreferences.getString(KEY_SELECTED_WHISPER_MODEL_ID, DEFAULT_WHISPER_MODEL) ?: DEFAULT_WHISPER_MODEL
    }

    fun saveSelectedVoskModelName(modelName: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_VOSK_MODEL_NAME, modelName).apply()
    }

    fun getSelectedVoskModelName(): String? {
        return sharedPreferences.getString(KEY_SELECTED_VOSK_MODEL_NAME, null)
    }

    // Wake word settings
    fun saveWakeWord(wakeWord: String) {
        sharedPreferences.edit().putString(KEY_WAKE_WORD, wakeWord).apply()
    }

    fun getWakeWord(): String {
        return sharedPreferences.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
    }

    fun saveWakeWordEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
    }

    fun isWakeWordEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_WAKE_WORD_ENABLED, false)
    }

    fun saveWakeWordModelPath(path: String) {
        sharedPreferences.edit().putString(KEY_WAKE_WORD_MODEL_PATH, path).apply()
    }

    fun getWakeWordModelPath(): String? {
        return sharedPreferences.getString(KEY_WAKE_WORD_MODEL_PATH, null)
    }

    // Offline fallback settings
    fun saveOfflineFallbackTimeout(timeoutSeconds: Int) {
        sharedPreferences.edit().putInt(KEY_OFFLINE_FALLBACK_TIMEOUT, timeoutSeconds).apply()
    }

    fun getOfflineFallbackTimeout(): Int {
        return sharedPreferences.getInt(KEY_OFFLINE_FALLBACK_TIMEOUT, DEFAULT_OFFLINE_FALLBACK_TIMEOUT)
    }

    fun saveDefaultOfflineModel(modelId: String) {
        sharedPreferences.edit().putString(KEY_DEFAULT_OFFLINE_MODEL, modelId).apply()
    }

    fun getDefaultOfflineModel(): String {
        return sharedPreferences.getString(KEY_DEFAULT_OFFLINE_MODEL, DEFAULT_OFFLINE_MODEL) ?: DEFAULT_OFFLINE_MODEL
    }

    fun clearDefaultOfflineModel() {
        sharedPreferences.edit().remove(KEY_DEFAULT_OFFLINE_MODEL).apply()
    }

    // Single default offline fallback tuple (processor, model)
    fun saveDefaultOfflineFallback(processor: String, modelId: String) {
        sharedPreferences.edit()
            .putString(KEY_DEFAULT_OFFLINE_FALLBACK_PROCESSOR, processor)
            .putString(KEY_DEFAULT_OFFLINE_FALLBACK_MODEL, modelId)
            .apply()
    }

    fun getDefaultOfflineFallbackProcessor(): String? {
        return sharedPreferences.getString(KEY_DEFAULT_OFFLINE_FALLBACK_PROCESSOR, null)
    }

    fun getDefaultOfflineFallbackModel(): String? {
        return sharedPreferences.getString(KEY_DEFAULT_OFFLINE_FALLBACK_MODEL, null)
    }

    fun clearDefaultOfflineFallback() {
        sharedPreferences.edit()
            .remove(KEY_DEFAULT_OFFLINE_FALLBACK_PROCESSOR)
            .remove(KEY_DEFAULT_OFFLINE_FALLBACK_MODEL)
            .apply()
    }

    // Logging settings
    fun saveLogLevel(level: String) {
        sharedPreferences.edit().putString(KEY_LOG_LEVEL, level).apply()
    }

    fun getLogLevel(): String {
        return sharedPreferences.getString(KEY_LOG_LEVEL, "TOAST_AND_LOGCAT") ?: "TOAST_AND_LOGCAT"
    }

    fun saveVerboseLoggingEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VERBOSE_LOGGING_ENABLED, enabled).apply()
    }

    fun isVerboseLoggingEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VERBOSE_LOGGING_ENABLED, false)
    }

    fun setVulkanIncompatible(incompatible: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VULKAN_INCOMPATIBLE, incompatible).apply()
    }

    fun isVulkanIncompatible(): Boolean {
        return sharedPreferences.getBoolean(KEY_VULKAN_INCOMPATIBLE, false)
    }

    fun saveCloudIntelligenceEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(Strings.Preferences.KEY_CLOUD_INTELLIGENCE_ENABLED, enabled).apply()
    }

    fun isCloudIntelligenceEnabled(): Boolean {
        return sharedPreferences.getBoolean(Strings.Preferences.KEY_CLOUD_INTELLIGENCE_ENABLED, false)
    }

    fun saveAiProcessor(processor: String) {
        sharedPreferences.edit().putString(Strings.Preferences.KEY_AI_PROCESSOR, processor).apply()
    }

    fun getAiProcessor(): String {
        return sharedPreferences.getString(Strings.Preferences.KEY_AI_PROCESSOR, Strings.AiProcessors.OPENAI) ?: Strings.AiProcessors.OPENAI
    }

    fun saveSelectedLlamaModelId(modelId: String) {
        sharedPreferences.edit().putString(Strings.Preferences.KEY_SELECTED_LLAMA_MODEL_ID, modelId).apply()
    }

    fun getSelectedLlamaModelId(): String {
        return sharedPreferences.getString(Strings.Preferences.KEY_SELECTED_LLAMA_MODEL_ID, "3.2-1b") ?: "3.2-1b"
    }

    /**
     * VOICE MODEL READY FLAG: Set by dropdown when model is selected.
     * This is the source of truth for MainScreen - dropdown knows if model exists.
     */
    fun setVoiceModelReady(ready: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_VOICE_MODEL_READY, ready).apply()
    }

    fun isVoiceModelReady(): Boolean {
        return sharedPreferences.getBoolean(KEY_VOICE_MODEL_READY, false)
    }

    /**
     * UNIFIED CHECK: Checks if the currently selected picklist item is ready on device.
     * This makes the UI model-agnostic.
     * Simplified to just check the flag set by dropdown.
     */
    fun isCurrentVoiceModelReady(context: android.content.Context): Boolean {
        return isVoiceModelReady()
    }

    companion object {
        private const val PREFS_NAME = Strings.Preferences.PREFS_NAME
        private const val DEFAULT_LANGUAGE = Strings.Preferences.DEFAULT_LANGUAGE
        private const val DEFAULT_PROCESSOR = Strings.Processors.WHISPER_NEON // Default to NEON for safety
        private const val DEFAULT_WHISPER_MODEL = Strings.Preferences.DEFAULT_WHISPER_MODEL
        private const val DEFAULT_WAKE_WORD = "salut vox"
        private const val DEFAULT_OFFLINE_FALLBACK_TIMEOUT = 30 // 30 seconds
        private const val DEFAULT_OFFLINE_MODEL = "tiny" // Default to tiny Whisper model

        private const val KEY_API_KEY = Strings.Preferences.KEY_API_KEY
        private const val KEY_LANGUAGE = Strings.Preferences.KEY_LANGUAGE
        private const val KEY_VOICE_LANGUAGE = Strings.Preferences.KEY_VOICE_LANGUAGE
        private const val KEY_VOICE_PROCESSOR = Strings.Preferences.KEY_VOICE_PROCESSOR
        private const val KEY_CUSTOM_VOSK_MODEL_PATH = Strings.Preferences.KEY_CUSTOM_VOSK_MODEL_PATH
        private const val KEY_CUSTOM_WHISPER_MODEL_PATH = Strings.Preferences.KEY_CUSTOM_WHISPER_MODEL_PATH
        private const val KEY_SELECTED_WHISPER_MODEL_ID = Strings.Preferences.KEY_SELECTED_WHISPER_MODEL_ID
        private const val KEY_SELECTED_VOSK_MODEL_NAME = Strings.Preferences.KEY_SELECTED_VOSK_MODEL_NAME
        private const val KEY_MODEL_DOWNLOADED_PREFIX = Strings.Preferences.KEY_MODEL_DOWNLOADED_PREFIX
        private const val KEY_VULKAN_INCOMPATIBLE = Strings.Preferences.KEY_VULKAN_INCOMPATIBLE
        private const val KEY_WAKE_WORD = Strings.Preferences.KEY_WAKE_WORD
        private const val KEY_WAKE_WORD_ENABLED = Strings.Preferences.KEY_WAKE_WORD_ENABLED
        private const val KEY_WAKE_WORD_MODEL_PATH = Strings.Preferences.KEY_WAKE_WORD_MODEL_PATH
        private const val KEY_OFFLINE_FALLBACK_TIMEOUT = Strings.Preferences.KEY_OFFLINE_FALLBACK_TIMEOUT
        private const val KEY_DEFAULT_OFFLINE_MODEL = "default_offline_model"
        private const val KEY_DEFAULT_OFFLINE_FALLBACK_PROCESSOR = "default_offline_fallback_processor"
        private const val KEY_DEFAULT_OFFLINE_FALLBACK_MODEL = "default_offline_fallback_model"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_VERBOSE_LOGGING_ENABLED = Strings.Preferences.KEY_VERBOSE_LOGGING
        private const val KEY_VOICE_MODEL_READY = "voice_model_ready"
        private const val KEY_CLOUD_INTELLIGENCE_ENABLED = "cloud_intelligence_enabled"
    }
}
