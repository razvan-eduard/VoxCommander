package com.voxcommander.app.state

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State Management Hub for Vox Commander.
 * Centralizes all reactive states (Voice, Intent, Benchmark, Native Libs).
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

enum class VulkanTestState {
    IDLE,
    RUNNING,
    RESULT
}

class AppStateManager private constructor(
    private val settingsManager: SettingsManager,
    private val context: Context
) {
    private val voiceMutex = Mutex()

    // --- CENTRALIZED UI STATE ---
    private val _uiState = MutableStateFlow(
        AppState.fromSettings(settingsManager, context, RemoteModelRegistry.getModelMapNow())
    )
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()
    
    // Alias for compatibility with older components
    val state: StateFlow<AppState> = uiState

    // --- NON-SETTINGS STATE (Kept separate as they don't come from SettingsManager) ---
    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()

    private val _nativeLibsStatus = MutableStateFlow<List<NativeLibStatus>>(emptyList())
    val nativeLibsStatus: StateFlow<List<NativeLibStatus>> = _nativeLibsStatus.asStateFlow()

    // --- VULKAN TEST STATE ---
    private val _vulkanTestState = MutableStateFlow(VulkanTestState.IDLE)
    val vulkanTestState: StateFlow<VulkanTestState> = _vulkanTestState.asStateFlow()

    private val _vulkanTestPassed = MutableStateFlow<Boolean?>(null)
    val vulkanTestPassed: StateFlow<Boolean?> = _vulkanTestPassed.asStateFlow()

    private val _systemInfo = MutableStateFlow<String>("")
    val systemInfo: StateFlow<String> = _systemInfo.asStateFlow()

    init {
        refreshNativeLibsStatus()
        refreshPermissions()
        setupVulkanTestTrigger()
    }

    /**
     * Updates permission-related states in AppState.
     */
    fun refreshPermissions() {
        updateState { 
            copy(
                canDrawOverlays = com.voxcommander.app.utils.PermissionUtils.canDrawOverlays(context),
                hasMicrophonePermission = com.voxcommander.app.utils.PermissionUtils.hasMicrophonePermission(context),
                hasNotificationPermission = com.voxcommander.app.utils.PermissionUtils.hasNotificationPermission(context)
            ) 
        }
    }

    /**
     * Centralized wrapper for state updates.
     * Automatically increments refreshTrigger to notify UI observers.
     */
    private inline fun updateState(mutation: AppState.() -> AppState) {
        _uiState.update { currentState ->
            val newState = currentState.mutation()
            newState.copy(refreshTrigger = currentState.refreshTrigger + 1)
        }
    }

    // Secure access to native resources
    suspend fun <T> executeSecureVoiceAction(block: suspend () -> T): T {
        return voiceMutex.withLock {
            block()
        }
    }

    fun setVoiceState(state: VoiceState) {
        updateState { copy(voiceState = state) }
    }

    fun onWakeWordDetected() {
        updateState { copy(wakeWordDetected = true) }
    }

    fun resetWakeWordDetection() {
        updateState { copy(wakeWordDetected = false) }
    }

    // State Update Methods - Atomic updates using .copy()
    fun setVoiceProcessor(processor: String) {
        settingsManager.saveVoiceProcessor(processor)

        // Do NOT auto-change activeVoiceModelId - it's set manually by user selection
        // The green checkmark is only set when user manually selects a model from dropdown

        updateState {
            copy(
                voiceProcessor = processor,
                voiceModelReady = recalculateVoiceReady(processor, settingsManager)
            )
        }
    }

    fun setVoiceLanguage(language: String) {
        settingsManager.saveVoiceLanguage(language)
        updateState {
            copy(
                voiceLanguage = language,
                voiceModelReady = recalculateVoiceReady(voiceProcessor, settingsManager)
            )
        }
    }

    fun setActiveVoiceModelId(modelId: String) {
        settingsManager.saveActiveVoiceModelId(modelId)
        updateState {
            copy(
                activeVoiceModelId = modelId,
                voiceModelReady = recalculateVoiceReady(voiceProcessor, settingsManager)
            )
        }
    }

    fun setCustomWhisperModelPath(path: String?) {
        if (path != null) settingsManager.saveCustomModelPath("stt_whisper", path)
        updateState {
            copy(
                customWhisperModelPath = path,
                voiceModelReady = recalculateVoiceReady(voiceProcessor, settingsManager)
            )
        }
    }

    fun setCustomVoskModelPath(language: String, path: String?) {
        if (path != null) settingsManager.saveCustomModelPath("wake_vosk", path, language)
        updateState {
            val updatedPaths = customVoskModelPaths.toMutableMap()
            if (path != null) {
                updatedPaths[language] = path
            } else {
                updatedPaths.remove(language)
            }
            copy(
                customVoskModelPaths = updatedPaths,
                voiceModelReady = recalculateVoiceReady(voiceProcessor, settingsManager)
            )
        }
    }

    fun setApiKey(key: String?) {
        if (key != null) settingsManager.saveApiKey(key)
        updateState { copy(apiKey = key) }
    }

    fun setAppLanguage(lang: String) {
        settingsManager.saveLanguage(lang)
        updateState { copy(voiceLanguage = lang) } // Sync app language with voice default
        refreshAll()
    }

    fun setOfflineFallbackTimeout(seconds: Int) {
        settingsManager.saveOfflineFallbackTimeout(seconds)
        refreshAll()
    }

    fun setWakeWord(word: String) {
        settingsManager.saveWakeWord(word)
        updateState { copy(wakeWord = word) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        settingsManager.saveWakeWordEnabled(enabled)
        updateState { copy(wakeWordEnabled = enabled) }
    }

    fun setWakeWordServiceListening(listening: Boolean) {
        updateState { copy(isWakeWordServiceListening = listening) }
    }

    fun setWakeWordModelPath(path: String?) {
        if (path != null) settingsManager.saveWakeWordModelPath(path)
        updateState { copy(wakeWordModelPath = path) }
    }

    fun setCloudIntelligenceEnabled(enabled: Boolean) {
        settingsManager.saveCloudIntelligenceEnabled(enabled)
        updateState { copy(cloudIntelligenceEnabled = enabled) }
    }

    fun setVerboseLoggingEnabled(enabled: Boolean) {
        settingsManager.saveVerboseLoggingEnabled(enabled)
        updateState { copy(isVerboseLoggingEnabled = enabled) }
    }

    fun setExperimentalVulkanEnabled(enabled: Boolean) {
        settingsManager.saveExperimentalVulkanEnabled(enabled)
        updateState { copy(isExperimentalVulkanEnabled = enabled) }
    }

    fun setAiProcessor(processor: String) {
        settingsManager.saveAiProcessor(processor)

        // Do NOT auto-change activeIntentModelId - it's set manually by user selection
        // The green checkmark is only set when user manually selects a model from dropdown

        updateState {
            copy(
                aiProcessor = processor,
                intentModelReady = recalculateIntentReady(processor, settingsManager)
            )
        }
    }

    fun setActiveIntentModelId(modelId: String) {
        settingsManager.saveActiveIntentModelId(modelId)
        updateState {
            copy(
                activeIntentModelId = modelId,
                intentModelReady = recalculateIntentReady(aiProcessor, settingsManager)
            )
        }
    }

    // Diagnostic Helpers
    fun refreshNativeLibsStatus() {
        val currentState = _uiState.value
        val voiceProcessor = currentState.voiceProcessor
        val aiProcessor = currentState.aiProcessor
        
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
                name.contains("whisper") && voiceProcessor.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("ggml") && voiceProcessor.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("omp") && voiceProcessor.startsWith("WHISPER") -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("vosk") && voiceProcessor == "VOSK" -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name.contains("llm") && aiProcessor == Strings.AiProcessors.NLU_LOCAL -> {
                    isActive = true
                    adjustedDesc = desc
                }
                name == "Google AICore" && aiProcessor == Strings.AiProcessors.GEMINI_NATIVE -> {
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

    // Simplified refreshAll - just reload everything from SettingsManager
    fun refreshAll() {
        val current = _uiState.value
        val nextTrigger = current.refreshTrigger + 1
        _uiState.value = AppState.fromSettings(settingsManager, context, RemoteModelRegistry.getModelMapNow()).copy(
            refreshTrigger = nextTrigger,
            isWakeWordServiceListening = current.isWakeWordServiceListening,
            voiceState = current.voiceState,
            wakeWordDetected = current.wakeWordDetected
        )
        refreshNativeLibsStatus()
    }

    // --- VULKAN TEST TRIGGER ---
    private fun setupVulkanTestTrigger() {
        combine(
            _uiState,
            _vulkanTestState
        ) { uiState, testState ->
            Pair(uiState, testState)
        }.onEach { (uiState, testState) ->
            if (testState == VulkanTestState.IDLE &&
                uiState.voiceProcessor == Strings.Processors.WHISPER_VULKAN &&
                uiState.voiceModelReady &&
                !settingsManager.isVulkanProbeDone() &&
                !settingsManager.isVulkanIncompatible()) {
                startVulkanTest()
            }
        }.launchIn(CoroutineScope(Dispatchers.Default + SupervisorJob()))
    }

    private fun startVulkanTest() {
        _vulkanTestState.value = VulkanTestState.RUNNING
        _vulkanTestPassed.value = null

        val modelId = _uiState.value.activeVoiceModelId
        // FIX: Models are in getExternalFilesDir(null), NOT context.filesDir
        val extension = com.voxcommander.app.data.remote.RemoteModelRegistry.getExtension("whisper")
        val modelPath = java.io.File(context.getExternalFilesDir(null), "$modelId$extension").absolutePath

        Logger.log("Starting Vulkan compatibility test with model: $modelPath", "VulkanTest")

        com.voxcommander.app.domain.diagnostic.VulkanProbe(
            context = context,
            modelPath = modelPath
        ) { outcome ->
            when (outcome) {
                com.voxcommander.app.domain.diagnostic.VulkanProbe.Outcome.COMPATIBLE -> {
                    Logger.log("Vulkan test PASSED", "VulkanTest")
                    _vulkanTestState.value = VulkanTestState.RESULT
                    _vulkanTestPassed.value = true
                    settingsManager.setVulkanProbeDone(true)
                }
                com.voxcommander.app.domain.diagnostic.VulkanProbe.Outcome.INCOMPATIBLE -> {
                    Logger.log("Vulkan test FAILED - switching to NEON", "VulkanTest")
                    _vulkanTestState.value = VulkanTestState.RESULT
                    _vulkanTestPassed.value = false
                    settingsManager.setVulkanIncompatible(true)
                    settingsManager.setVulkanProbeDone(true)
                    
                    // Force switch to NEON on Main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        setVoiceProcessor(Strings.Processors.WHISPER_NEON)
                        refreshAll()
                    }
                }
                com.voxcommander.app.domain.diagnostic.VulkanProbe.Outcome.UNDECIDED -> {
                    Logger.log("Vulkan test UNDECIDED - will retry later", "VulkanTest")
                    _vulkanTestState.value = VulkanTestState.IDLE
                    _vulkanTestPassed.value = null
                }
            }
        }.start()
    }

    fun dismissVulkanTestResult() {
        _vulkanTestState.value = VulkanTestState.IDLE
        _vulkanTestPassed.value = null
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
