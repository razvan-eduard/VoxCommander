package com.voxcommander.app.domain.engine.whisper

import android.content.Context
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.engine.SttEngine
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
    private val settingsManager: SettingsManager,
    private val forceGpu: Boolean = false,
    private val onVulkanIncompatible: () -> Unit = {}
) : SttEngine {

    private val TAG = "WhisperCppSttEngine"
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
            val customPath = settingsManager.getCustomWhisperModelPath()
            var modelPath = if (!customPath.isNullOrBlank()) {
                Log.d(TAG, "Using custom model path: $customPath")
                customPath
            } else {
                val selectedModelId = settingsManager.getSelectedWhisperModelId()
                File(
                    context.getExternalFilesDir(null),
                    "whisper-model-$selectedModelId.bin"
                ).absolutePath
            }

            if (!File(modelPath).exists()) {
                Log.e(TAG, "Model file not found at: $modelPath")
                return@withContext
            }

            // TIER 1: Try Vulkan (GPU)
            val attemptVulkan = forceGpu && !settingsManager.isVulkanIncompatible()
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
                    settingsManager.setVulkanIncompatible(true)
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
            
            // Force language if provided to prevent Cyrillic/Slavic hallucinations
            val result = currentContext.transcribeData(floatAudio, threads, language = langCode, printTimestamp = false)

            return@withContext result.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
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
