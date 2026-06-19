package com.voxcommander.app.domain.diagnostic

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.engine.google.GoogleSttEngine
import com.voxcommander.app.domain.engine.vosk.VoskSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperCppSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperModelInfo
import com.voxcommander.app.domain.engine.whisper.WhisperModelRegistry
import com.voxcommander.app.domain.engine.whisper.WhisperSttEngine
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.BenchmarkResult
import com.voxcommander.app.state.VoiceState
import com.whispercpp.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Isolated Benchmark Engine to avoid cluttering production VoiceManager.
 */
class BenchmarkEngine(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val appStateManager: AppStateManager
) {
    suspend fun runFullBenchmark() = withContext(Dispatchers.Default) {
        appStateManager.setVoiceState(VoiceState.BENCHMARKING)
        appStateManager.clearBenchmarkResults()

        // 5 seconds dummy audio
        val dummyAudio = ByteArray(80000 * 2) { 0 }

        // Start collecting comprehensive system info
        val diagInfo = StringBuilder()
        
        // 1. Hardware Info (from Whisper/GGML)
        diagInfo.append("--- HARDWARE CAPABILITIES ---\n")
        diagInfo.append(WhisperLib.getSystemInfo())
        diagInfo.append("\n\n")

        // 2. Whisper Diagnostics
        val downloadedWhisperModels = WhisperModelRegistry.models.filter {
            settingsManager.isModelDownloaded(it.id) 
        }

        if (downloadedWhisperModels.isNotEmpty()) {
            diagInfo.append("--- WHISPER MODELS DETECTED ---\n")
            downloadedWhisperModels.forEach { 
                diagInfo.append("ID: ${it.id} | Size: ${it.sizeDescription} | Label: ${it.label}\n")
            }
            diagInfo.append("\n")
        }

        for (model in downloadedWhisperModels) {
            runSingleWhisperBenchmark(model, forceGpu = false, dummyAudio)
            
            if (settingsManager.isVulkanIncompatible()) {
                appStateManager.updateBenchmarkResult(BenchmarkResult(
                    engine = "Whisper Vulkan",
                    model = model.label,
                    inferenceTimeMs = 0,
                    rtf = 0f,
                    isSuccess = false,
                    error = "Skipped (Hardware Incompatible)"
                ))
            } else {
                runSingleWhisperBenchmark(model, forceGpu = true, dummyAudio)
            }
        }

        // 3. Vosk Diagnostics
        val selectedVosk = settingsManager.getSelectedVoskModelName()
        if (selectedVosk != null && settingsManager.isModelDownloaded(selectedVosk)) {
            val lang = settingsManager.getVoiceLanguage()
            diagInfo.append("--- VOSK ENGINE INFO ---\n")
            diagInfo.append("Active Model: $selectedVosk\n")
            diagInfo.append("Active Language: $lang\n")
            diagInfo.append("Backend: Kaldi-based (libvosk.so)\n\n")
            
            runVoskBenchmark(selectedVosk, dummyAudio)
        }

        // 4. API Diagnostics
        val apiKey = settingsManager.getApiKey()
        if (!apiKey.isNullOrBlank()) {
            diagInfo.append("--- CLOUD CONNECTIVITY ---\n")
            diagInfo.append("Whisper API: Active (Endpoint: OpenAI)\n")
            diagInfo.append("Key Masked: ${apiKey.take(4)}...${apiKey.takeLast(4)}\n\n")
            
            runApiBenchmark(apiKey, dummyAudio)
        }

        // Finalize system info view
        appStateManager.setSystemInfo(diagInfo.toString())
        
        // 5. Google STT
        runGoogleBenchmark()

        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    private suspend fun runSingleWhisperBenchmark(model: WhisperModelInfo, forceGpu: Boolean, audioData: ByteArray) {
        val label = if (forceGpu) "Whisper Vulkan" else "Whisper NEON"
        try {
            val engine = WhisperCppSttEngine(context, settingsManager, forceGpu = forceGpu)
            val start = System.currentTimeMillis()
            engine.transcribe(audioData)
            val end = System.currentTimeMillis()
            appStateManager.updateBenchmarkResult(BenchmarkResult(label, model.label, end - start, (end - start).toFloat() / 5000f, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(label, model.label, 0, 0f, false, e.message))
        }
    }

    private suspend fun runVoskBenchmark(modelName: String, audioData: ByteArray) {
        try {
            val engine = VoskSttEngine(context, settingsManager, settingsManager.getVoiceLanguage())
            val start = System.currentTimeMillis()
            engine.transcribe(audioData)
            val end = System.currentTimeMillis()
            appStateManager.updateBenchmarkResult(BenchmarkResult("Vosk", modelName, end - start, (end - start).toFloat() / 5000f, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Vosk", modelName, 0, 0f, false, e.message))
        }
    }

    private suspend fun runGoogleBenchmark() {
        try {
            val start = System.currentTimeMillis()
            val engine = GoogleSttEngine(context)
            val end = System.currentTimeMillis()
            appStateManager.updateBenchmarkResult(BenchmarkResult("Google STT", "Native", end - start, 0f, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Google STT", "Native", 0, 0f, false, e.message))
        }
    }

    private suspend fun runApiBenchmark(apiKey: String, audioData: ByteArray) {
        try {
            val engine = WhisperSttEngine(apiKey)
            val start = System.currentTimeMillis()
            engine.transcribe(audioData)
            val end = System.currentTimeMillis()
            appStateManager.updateBenchmarkResult(BenchmarkResult("Whisper API", "Cloud", end - start, (end - start).toFloat() / 5000f, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Whisper API", "Cloud", 0, 0f, false, e.message))
        }
    }
}
