package com.voxcommander.app.domain.engine.whisper

import android.content.Context
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.engine.SttEngine
import com.voxcommander.app.utils.Strings
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hybrid STT Engine: Supports Vulkan with automatic fallback to NEON (CPU).
 */
class WhisperCppSttEngine(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val forceGpu: Boolean = false,
    private val onVulkanIncompatible: () -> Unit = {}
) : SttEngine {

    private val TAG = Strings.Tags.WHISPER_CPP_STT_ENGINE
    private var whisperContext: WhisperContext? = null
    private var isUsingGpu = false
    private val loadMutex = Mutex()

    init {
        WhisperLib.load()
    }

    /**
     * Public method to trigger initialization and test compatibility.
     * Returns true if initialized (either GPU or CPU).
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        return@withContext whisperContext != null
    }

    private suspend fun ensureModelLoaded() = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (whisperContext != null) return@withContext

            // Check for custom model path first
            val snapshot = settingsRepo.getSettingsSnapshot()
            val whisperKey = com.voxcommander.app.data.remote.RemoteModelRegistry.getEngineKeyByExtension(".bin")
            val customPath = whisperKey?.let { snapshot.getCustomModelPath(it) }
            var modelPath = if (!customPath.isNullOrBlank()) {
                Log.d(TAG, "Using custom model path: $customPath")
                customPath
            } else {
                val selectedModelId = snapshot.activeVoiceModelId
                File(
                    context.getExternalFilesDir(null),
                    "$selectedModelId${whisperKey?.let { com.voxcommander.app.data.remote.RemoteModelRegistry.getExtension(it) } ?: ""}"
                ).absolutePath
            }

            if (!File(modelPath).exists()) {
                Log.e(TAG, "Model file not found at: $modelPath")
                return@withContext
            }

            // TIER 1: Try Vulkan (GPU)
            val attemptVulkan = forceGpu && !snapshot.vulkanIncompatible
            if (attemptVulkan) {
                Log.d(TAG, "Attempting to initialize with VULKAN...")
                try {
                    whisperContext = WhisperContext.createContextFromFile(modelPath, useGpu = true)
                    if (whisperContext != null) {
                        isUsingGpu = true
                        Log.d(TAG, "SUCCESS: Whisper context initialized with VULKAN")
                        return@withContext
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "VULKAN init failed. Marking as incompatible.", e)
                    kotlinx.coroutines.runBlocking { settingsRepo.setVulkanIncompatible(true) }
                    withContext(Dispatchers.Main) { onVulkanIncompatible() }
                }
            }

            // TIER 2: Fallback to NEON (CPU)
            Log.d(TAG, "Initializing with NEON/CPU...")
            try {
                whisperContext = WhisperContext.createContextFromFile(modelPath, useGpu = false)
                if (whisperContext != null) {
                    isUsingGpu = false
                    Log.d(TAG, "SUCCESS: Whisper context initialized Hex NEON/CPU")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "CRITICAL: NEON/CPU initialization failed", e)
            }
        }
    }

    override suspend fun transcribe(audio: ByteArray): String = transcribeWithLanguage(audio, null)

    suspend fun transcribeWithLanguage(audio: ByteArray, langCode: String?): String = withContext(Dispatchers.IO) {
        if (!WhisperLib.isReady()) return@withContext "Error: Native library failed to load"

        ensureModelLoaded()
        val currentContext =
            whisperContext ?: return@withContext "Error: Whisper engine not initialized"

        try {
            val floatAudio = pcm16ToFloat(audio)

            // Conservative thread count for CPU (NEON)
            val threads = if (isUsingGpu) 1 else 2

            Log.d(
                TAG,
                "Transcribing using ${if (isUsingGpu) "VULKAN" else "CPU"} ($threads threads), Lang: ${langCode ?: "auto"}"
            )
            
            // Crash-cookie: a native GPU crash during inference cannot be caught by
            // try/catch. Commit a marker before real GPU work; if the process dies,
            // AppContainer detects the leftover cookie next launch and disables Vulkan.
            val snapshot = settingsRepo.getSettingsSnapshot()
            val guardGpu = isUsingGpu && !snapshot.vulkanRuntimeVerified
            if (guardGpu) settingsRepo.setVulkanRuntimeAttemptSync(true)

            // Force language if provided to prevent Cyrillic/Slavic hallucinations
            val result = currentContext.transcribeData(floatAudio, threads, language = langCode, printTimestamp = false)

            if (guardGpu) {
                // Survived a real GPU transcription -> device is genuinely compatible.
                settingsRepo.setVulkanRuntimeAttemptSync(false)
                kotlinx.coroutines.runBlocking { settingsRepo.setVulkanRuntimeVerified(true) }
            }

            return@withContext result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            if (isUsingGpu) settingsRepo.setVulkanRuntimeAttemptSync(false)
            "Error: ${e.message}"
        }
    }

    private fun pcm16ToFloat(audio: ByteArray): FloatArray {
        val shorts = ShortArray(audio.size / 2)
        ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val floats = FloatArray(shorts.size)
        for (i in shorts.indices) {
            floats[i] = shorts[i] / 32768.0f
        }
        return floats
    }

    override fun releaseHardware() {
        Log.d(TAG, "Releasing native context")
        whisperContext?.release()
    }

    override fun releaseResources() {
        whisperContext = null
    }
}
