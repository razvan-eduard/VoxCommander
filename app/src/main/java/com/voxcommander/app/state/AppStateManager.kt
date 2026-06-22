package com.voxcommander.app.state

import android.content.Context
import androidx.room.Room
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.model.FastMapRule
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State Management Hub for Vox Commander.
 * Centralizes all reactive states (Voice, Benchmark, Native Libs).
 */
enum class VoiceState {
    IDLE,               // Waiting for user
    LISTENING_WAKEWORD, // Background service active
    LISTENING_COMMAND,  // Actively recording user command
    PROCESSING,         // Engine is transcribing text
    CLEANING,           // Resetting engines
    BENCHMARKING        // Running diagnostics
}

data class BenchmarkResult(
    val engine: String,
    val model: String,
    val inferenceTimeMs: Long,
    val rtf: Float,
    val isSuccess: Boolean,
    val error: String? = null
)

data class NativeLibStatus(
    val name: String,
    val exists: Boolean,
    val isActive: Boolean,
    val description: String,
    val isIncompatible: Boolean = false
)

class AppStateManager private constructor(
    private val settingsManager: SettingsManager,
    private val context: Context
) {
    private val voiceMutex = Mutex()

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _wakeWordDetected = MutableStateFlow(false)
    val wakeWordDetected: StateFlow<Boolean> = _wakeWordDetected.asStateFlow()

    // --- REACIVE SETTINGS STATE ---
    private val _voiceProcessor = MutableStateFlow(settingsManager.getVoiceProcessor())
    val voiceProcessor: StateFlow<String> = _voiceProcessor.asStateFlow()

    private val _voiceLanguage = MutableStateFlow(settingsManager.getVoiceLanguage())
    val voiceLanguage: StateFlow<String> = _voiceLanguage.asStateFlow()

    private val _selectedWhisperModelId = MutableStateFlow(settingsManager.getSelectedWhisperModelId())
    val selectedWhisperModelId: StateFlow<String> = _selectedWhisperModelId.asStateFlow()

    private val _selectedVoskModelName = MutableStateFlow(settingsManager.getSelectedVoskModelName())
    val selectedVoskModelName: StateFlow<String?> = _selectedVoskModelName.asStateFlow()

    private val _customWhisperModelPath = MutableStateFlow(settingsManager.getCustomWhisperModelPath())
    val customWhisperModelPath: StateFlow<String?> = _customWhisperModelPath.asStateFlow()

    private val _customVoskModelPaths = MutableStateFlow<Map<String, String>>(emptyMap())
    val customVoskModelPaths: StateFlow<Map<String, String>> = _customVoskModelPaths.asStateFlow()

    private val _apiKey = MutableStateFlow(settingsManager.getApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _wakeWord = MutableStateFlow(settingsManager.getWakeWord())
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(settingsManager.isWakeWordEnabled())
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _isWakeWordServiceListening = MutableStateFlow(false)
    val isWakeWordServiceListening: StateFlow<Boolean> = _isWakeWordServiceListening.asStateFlow()

    private val _cloudIntelligenceEnabled = MutableStateFlow(settingsManager.isCloudIntelligenceEnabled())
    val cloudIntelligenceEnabled: StateFlow<Boolean> = _cloudIntelligenceEnabled.asStateFlow()

    private val _aiProcessor = MutableStateFlow(settingsManager.getAiProcessor())
    val aiProcessor: StateFlow<String> = _aiProcessor.asStateFlow()

    private val _selectedLlamaModelId = MutableStateFlow(settingsManager.getSelectedLlamaModelId())
    val selectedLlamaModelId: StateFlow<String> = _selectedLlamaModelId.asStateFlow()

    // --- DERIVED READY STATE (The Source of Truth for "Selected model on device") ---
    val voiceModelReady: StateFlow<Boolean> = combine(
        voiceProcessor,
        voiceLanguage,
        selectedWhisperModelId,
        selectedVoskModelName,
        customWhisperModelPath
    ) { processor, language, whisperId, voskName, customWhisper ->
        when (processor) {
            Strings.Processors.WHISPER_CPP,
            Strings.Processors.WHISPER_VULKAN,
            Strings.Processors.WHISPER_NEON -> {
                settingsManager.isModelDownloaded(whisperId) || customWhisper != null
            }
            Strings.Processors.VOSK -> {
                val customVosk = settingsManager.getCustomVoskModelPath(language)
                if (!customVosk.isNullOrBlank()) {
                    java.io.File(customVosk).exists()
                } else {
                    !voskName.isNullOrBlank() && settingsManager.isModelDownloaded(voskName)
                }
            }
            Strings.Processors.GOOGLE,
            Strings.Processors.WHISPER_API -> true
            else -> false
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        started = SharingStarted.Eagerly,
        initialValue = settingsManager.isCurrentVoiceModelReady(context)
    )

    // --- BENCHMARK & DIAGNOSTIC STATE ---
    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()

    private val _nativeLibsStatus = MutableStateFlow<List<NativeLibStatus>>(emptyList())
    val nativeLibsStatus: StateFlow<List<NativeLibStatus>> = _nativeLibsStatus.asStateFlow()

    private val _systemInfo = MutableStateFlow<String>("")
    val systemInfo: StateFlow<String> = _systemInfo.asStateFlow()

    init {
        loadCustomVoskPaths()
        refreshNativeLibsStatus()
    }

    // Secure access to native resources
    suspend fun <T> executeSecureVoiceAction(block: suspend () -> T): T {
        return voiceMutex.withLock {
            block()
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

    fun canStartWakeWord(): Boolean = _voiceState.value == VoiceState.IDLE
    fun canStartCommand(): Boolean = _voiceState.value == VoiceState.IDLE

    // State Update Methods
    fun setVoiceModelReady(ready: Boolean) {
        // Now handled reactively, but we keep the preference update for legacy reasons
        settingsManager.setVoiceModelReady(ready)
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
        if (path != null) settingsManager.saveCustomWhisperModelPath(path)
        _customWhisperModelPath.value = path
    }

    fun setCustomVoskModelPath(language: String, path: String?) {
        if (path != null) settingsManager.saveCustomVoskModelPath(language, path)
        loadCustomVoskPaths()
    }

    fun setApiKey(key: String?) {
        if (key != null) settingsManager.saveApiKey(key)
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

    fun setWakeWordServiceListening(listening: Boolean) {
        _isWakeWordServiceListening.value = listening
    }

    fun setCloudIntelligenceEnabled(enabled: Boolean) {
        settingsManager.saveCloudIntelligenceEnabled(enabled)
        _cloudIntelligenceEnabled.value = enabled
    }

    fun setAiProcessor(processor: String) {
        settingsManager.saveAiProcessor(processor)
        _aiProcessor.value = processor
    }

    fun setSelectedLlamaModelId(modelId: String) {
        settingsManager.saveSelectedLlamaModelId(modelId)
        _selectedLlamaModelId.value = modelId
    }

    fun onWhisperDownloadComplete(modelId: String) {
        settingsManager.setModelDownloaded(modelId, true)
        _selectedWhisperModelId.value = modelId
    }

    fun onVoskDownloadComplete(modelName: String) {
        settingsManager.setModelDownloaded(modelName, true)
        _selectedVoskModelName.value = modelName
    }

    // Diagnostic Helpers
    fun refreshNativeLibsStatus() {
        // List of SO files and system components we depend on
        val soFiles = listOf(
            "libwhisper.so" to "Core Whisper STT Engine",
            "libggml.so" to "GGML Tensor Library",
            "libggml-cpu.so" to "GGML CPU Operations",
            "libggml-base.so" to "GGML Base Library",
            "libggml-vulkan.so" to "Vulkan GPU Acceleration",
            "libomp.so" to "OpenMP Multi-threading",
            "libvosk.so" to "Vosk Voice Engine",
            "libllm_inference_engine_jni.so" to "MediaPipe Llama Engine",
            "Google AICore" to "Gemini Nano System Service"
        )

        val statusList = soFiles.map { (name, desc) ->
            val exists: Boolean
            val isIncompatible: Boolean
            
            if (name == "Google AICore") {
                isIncompatible = settingsManager.isGeminiIncompatible()
                exists = !isIncompatible
            } else {
                val file = java.io.File(context.applicationInfo.nativeLibraryDir, name)
                exists = file.exists()
                isIncompatible = name.contains("ggml-vulkan") && settingsManager.isVulkanIncompatible()
            }
            
            val isActive: Boolean
            val adjustedDesc: String
            
            when {
                isIncompatible -> {
                    isActive = false
                    adjustedDesc = "$desc (Incompatible)"
                }
                name.contains("whisper") && _voiceProcessor.value.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("ggml") && _voiceProcessor.value.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("omp") && _voiceProcessor.value.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("vosk") && _voiceProcessor.value == "VOSK" -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("llm") && settingsManager.getAiProcessor() == Strings.AiProcessors.LLAMA_LOCAL -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name == "Google AICore" && settingsManager.getAiProcessor() == Strings.AiProcessors.GEMINI_NATIVE -> {
                    isActive = true
                    adjustedDesc = desc
                }
                else -> {
                    isActive = false
                    adjustedDesc = desc
                }
            }
            NativeLibStatus(name, exists, isActive, adjustedDesc, isIncompatible)
        }
        _nativeLibsStatus.value = statusList
    }

    fun updateBenchmarkResult(result: BenchmarkResult) {
        val current = _benchmarkResults.value.toMutableList()
        current.add(result)
        _benchmarkResults.value = current
    }

    fun clearBenchmarkResults() {
        _benchmarkResults.value = emptyList()
    }

    fun setSystemInfo(info: String) {
        _systemInfo.value = info
    }

    fun loadCustomVoskPaths() {
        val languages = listOf("en", "ro", "de", "fr")
        val paths = mutableMapOf<String, String>()
        languages.forEach { lang ->
            settingsManager.getCustomVoskModelPath(lang)?.let { path ->
                paths[lang] = path
            }
        }
        _customVoskModelPaths.value = paths
    }

    fun refreshAll() {
        _voiceProcessor.value = settingsManager.getVoiceProcessor()
        _voiceLanguage.value = settingsManager.getVoiceLanguage()
        _selectedWhisperModelId.value = settingsManager.getSelectedWhisperModelId()
        _selectedVoskModelName.value = settingsManager.getSelectedVoskModelName()
        _customWhisperModelPath.value = settingsManager.getCustomWhisperModelPath()
        _apiKey.value = settingsManager.getApiKey()
        _wakeWord.value = settingsManager.getWakeWord()
        _wakeWordEnabled.value = settingsManager.isWakeWordEnabled()
        _cloudIntelligenceEnabled.value = settingsManager.isCloudIntelligenceEnabled()
        _aiProcessor.value = settingsManager.getAiProcessor()
        _selectedLlamaModelId.value = settingsManager.getSelectedLlamaModelId()
        loadCustomVoskPaths()
        refreshNativeLibsStatus()
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
