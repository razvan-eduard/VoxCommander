package com.voxcommander.app.state

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcommander.app.data.preferences.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global voice state to coordinate between wake word service and command recognition.
 */
enum class VoiceState {
    IDLE,               // No one is listening
    LISTENING_WAKEWORD, // Vosk is active (wake word detection)
    LISTENING_COMMAND,  // Whisper/Google is active (command recognition)
    PROCESSING,         // Engine is transcribing text
    CLEANING            // Engine is releasing resources
}

/**
 * Centralized State Hub for global application state.
 * Observes SharedPreferences and exposes StateFlow for realtime state access.
 */
class AppStateManager(
    private val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {

    // --- FUNCTIONAL VOICE STATE (Merged from VoiceStateManager) ---
    private val voiceMutex = Mutex()
    
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _wakeWordDetected = MutableStateFlow(false)
    val wakeWordDetected: StateFlow<Boolean> = _wakeWordDetected.asStateFlow()

    /**
     * SECURE EXECUTION: Ensures only one component (Vosk or Whisper) 
     * can touch the audio hardware or native memory at a time.
     */
    suspend fun <T> executeSecureVoiceAction(action: suspend () -> T): T {
        return voiceMutex.withLock {
            action()
        }
    }

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    fun onWakeWordDetected() {
        _wakeWordDetected.value = true
    }

    fun resetWakeWordDetection() {
        _wakeWordDetected.value = false
    }

    fun canStartWakeWord(): Boolean {
        return _voiceState.value == VoiceState.IDLE
    }

    fun canStartCommand(): Boolean {
        return _voiceState.value == VoiceState.LISTENING_WAKEWORD
    }

    // --- PERSISTENT SETTINGS STATE ---

    // Voice Model Ready State
    private val _voiceModelReady = MutableStateFlow(settingsManager.isVoiceModelReady())
    val voiceModelReady: StateFlow<Boolean> = _voiceModelReady.asStateFlow()

    // Voice Processor State
    private val _voiceProcessor = MutableStateFlow(settingsManager.getVoiceProcessor())
    val voiceProcessor: StateFlow<String> = _voiceProcessor.asStateFlow()

    // Voice Language State
    private val _voiceLanguage = MutableStateFlow(settingsManager.getVoiceLanguage())
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    // Selected Whisper Model ID
    private val _selectedWhisperModelId = MutableStateFlow(settingsManager.getSelectedWhisperModelId())
    val selectedWhisperModelId: StateFlow<String> = _selectedWhisperModelId.asStateFlow()

    // Selected Vosk Model Name
    private val _selectedVoskModelName = MutableStateFlow(settingsManager.getSelectedVoskModelName())
    val selectedVoskModelName: StateFlow<String?> = _selectedVoskModelName.asStateFlow()

    // Custom Whisper Model Path
    private val _customWhisperModelPath = MutableStateFlow(settingsManager.getCustomWhisperModelPath())
    val customWhisperModelPath: StateFlow<String?> = _customWhisperModelPath.asStateFlow()

    // Custom Vosk Model Path (per language)
    private val _customVoskModelPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val customVoskModelPaths: StateFlow<Map<String, String>> = _customVoskModelPaths.asStateFlow()

    // API Key State
    private val _apiKey = MutableStateFlow(settingsManager.getApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    // Wake Word State
    private val _wakeWord = MutableStateFlow(settingsManager.getWakeWord())
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    // Wake Word Enabled State
    private val _wakeWordEnabled = MutableStateFlow(settingsManager.isWakeWordEnabled())
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    // Initialize with current SharedPreferences values
    init {
        loadCustomVoskPaths()
    }

    // State Update Methods
    fun setVoiceModelReady(ready: Boolean) {
        settingsManager.setVoiceModelReady(ready)
        _voiceModelReady.value = ready
    }

    fun setVoiceProcessor(processor: String) {
        settingsManager.saveVoiceProcessor(processor)
        _voiceProcessor.value = processor
    }

    fun setVoiceLanguage(language: String) {
        settingsManager.saveVoiceLanguage(language)
        _voiceLanguage.value = language
    }

    fun setSelectedWhisperModelId(modelId: String) {
        settingsManager.saveSelectedWhisperModelId(modelId)
        _selectedWhisperModelId.value = modelId
    }

    fun setSelectedVoskModelName(modelName: String) {
        settingsManager.saveSelectedVoskModelName(modelName)
        _selectedVoskModelName.value = modelName
    }

    fun setCustomWhisperModelPath(path: String?) {
        if (path != null) {
            settingsManager.saveCustomWhisperModelPath(path)
        } else {
            settingsManager.saveCustomWhisperModelPath("")
        }
        _customWhisperModelPath.value = path
    }

    fun setCustomVoskModelPath(language: String, path: String?) {
        if (path != null) {
            settingsManager.saveCustomVoskModelPath(language, path)
        } else {
            settingsManager.saveCustomVoskModelPath(language, "")
        }
        loadCustomVoskPaths()
    }

    fun setApiKey(key: String?) {
        if (key != null) {
            settingsManager.saveApiKey(key)
        } else {
            settingsManager.saveApiKey("")
        }
        _apiKey.value = key
    }

    fun setWakeWord(word: String) {
        settingsManager.saveWakeWord(word)
        _wakeWord.value = word
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        settingsManager.saveWakeWordEnabled(enabled)
        _wakeWordEnabled.value = enabled
    }

    fun onWhisperDownloadComplete(modelId: String) {
        settingsManager.setModelDownloaded(modelId, true)
        setSelectedWhisperModelId(modelId)
        setVoiceModelReady(true)
    }

    fun onVoskDownloadComplete(modelName: String) {
        settingsManager.setModelDownloaded(modelName, true)
        setSelectedVoskModelName(modelName)
        setVoiceModelReady(true)
    }

    // Helper to load all custom Vosk paths
    private fun loadCustomVoskPaths() {
        viewModelScope.launch {
            val paths = mutableMapOf<String, String>()
            // Load for common languages
            listOf("ro", "en", "de", "fr", "es", "it").forEach { lang ->
                settingsManager.getCustomVoskModelPath(lang)?.let { path ->
                    paths[lang] = path
                }
            }
            _customVoskModelPaths.value = paths
        }
    }

    // Refresh all states from SharedPreferences
    fun refreshAll() {
        _voiceModelReady.value = settingsManager.isVoiceModelReady()
        _voiceProcessor.value = settingsManager.getVoiceProcessor()
        _voiceLanguage.value = settingsManager.getVoiceLanguage()
        _selectedWhisperModelId.value = settingsManager.getSelectedWhisperModelId()
        _selectedVoskModelName.value = settingsManager.getSelectedVoskModelName()
        _customWhisperModelPath.value = settingsManager.getCustomWhisperModelPath()
        _apiKey.value = settingsManager.getApiKey()
        _wakeWord.value = settingsManager.getWakeWord()
        _wakeWordEnabled.value = settingsManager.isWakeWordEnabled()
        loadCustomVoskPaths()
    }

    companion object {
        @Volatile
        private var instance: AppStateManager? = null

        fun getInstance(settingsManager: SettingsManager, context: Context): AppStateManager {
            return instance ?: synchronized(this) {
                instance ?: AppStateManager(settingsManager, context).also { instance = it }
            }
        }
    }
}
