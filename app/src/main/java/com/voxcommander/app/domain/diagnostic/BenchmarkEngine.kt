package com.voxcommander.app.domain.diagnostic

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.engine.google.GoogleSttEngine
import com.voxcommander.app.domain.engine.vosk.VoskSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperCppSttEngine
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.domain.engine.whisper.WhisperSttEngine
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.BenchmarkResult
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Strings
import com.whispercpp.whisper.WhisperLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Isolated Benchmark Engine to avoid cluttering production VoiceManager.
 */
class BenchmarkEngine(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val appStateManager: AppStateManager,
    private val modelDownloader: ModelDownloader
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
        diagInfo.append("\n")

        // Add Whisper Vulkan compatibility status
        val snapshot = settingsRepo.getSettingsSnapshot()
        diagInfo.append("--- WHISPER VULKAN COMPATIBILITY ---\n")
        if (snapshot.vulkanIncompatible) {
            diagInfo.append("Status: INCOMPATIBLE (GPU crashes during Whisper inference)\n")
            diagInfo.append("Note: Hardware supports Vulkan but Whisper GPU workload fails.\n")
        } else if (snapshot.vulkanRuntimeVerified) {
            diagInfo.append("Status: VERIFIED (GPU inference tested successfully)\n")
        } else if (snapshot.vulkanProbeDone) {
            diagInfo.append("Status: COMPATIBLE (probe passed, inference not yet verified)\n")
        } else {
            diagInfo.append("Status: UNKNOWN (probe not yet run)\n")
        }
        diagInfo.append("\n")

        // 2. Whisper Diagnostics
        val whisperKey = RemoteModelRegistry.getEngineKeyByExtension(".bin")
        val downloadedWhisperModels = whisperKey?.let { RemoteModelRegistry.getModels(it) }?.filter {
            snapshot.isModelDownloaded(it.id)
        } ?: emptyList()

        if (downloadedWhisperModels.isNotEmpty()) {
            diagInfo.append("--- WHISPER MODELS DETECTED ---\n")
            downloadedWhisperModels.forEach {
                diagInfo.append("ID: ${it.id} | Size: ${it.sizeDescription} | Label: ${it.label}\n")
            }
            diagInfo.append("\n")
        }

        for (model in downloadedWhisperModels) {
            runSingleWhisperBenchmark(model, forceGpu = false, dummyAudio)
            
            if (settingsRepo.getSettingsSnapshot().vulkanIncompatible) {
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
        val selectedVosk = snapshot.activeVoiceModelId
        if (selectedVosk != null && snapshot.isModelDownloaded(selectedVosk)) {
            val lang = snapshot.voiceLanguage
            diagInfo.append("--- VOSK ENGINE INFO ---\n")
            diagInfo.append("Active Model: $selectedVosk\n")
            diagInfo.append("Active Language: $lang\n")
            diagInfo.append("Backend: Kaldi-based (libvosk.so)\n\n")

            runVoskBenchmark(selectedVosk, dummyAudio)
        }

        // 4. API Diagnostics
        val apiKey = snapshot.apiKey
        if (!apiKey.isNullOrBlank()) {
            diagInfo.append("--- CLOUD CONNECTIVITY ---\n")
            diagInfo.append("Whisper API: Active (Endpoint: OpenAI)\n")
            diagInfo.append("Key Masked: ${apiKey.take(4)}...${apiKey.takeLast(4)}\n\n")
            
            runApiBenchmark(apiKey, dummyAudio)
        }

        // 5. LLM Diagnostics (Local LLM via MediaPipe)
        val nluModelId = snapshot.activeIntentModelId

        diagInfo.append("--- LOCAL LLM DIAGNOSTICS ---\n")
        val geminiSupported = !snapshot.geminiIncompatible
        diagInfo.append("Gemini Nano Native: ${if (geminiSupported) "SUPPORTED" else "INCOMPATIBLE"}\n")

        if (nluModelId != null && snapshot.isModelDownloaded(nluModelId)) {
            diagInfo.append("Active Model: $nluModelId\n\n")
            runLlamaBenchmark(nluModelId)
        } else {
            diagInfo.append("NLU Model: Not Downloaded\n\n")
        }

        // Finalize system info view
        appStateManager.setSystemInfo(diagInfo.toString())
        
        // 5. Google STT
        runGoogleBenchmark()

        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    private suspend fun runSingleWhisperBenchmark(model: AppModel, forceGpu: Boolean, audioData: ByteArray) {
        val label = if (forceGpu) "Whisper Vulkan" else "Whisper NEON"
        try {
            val engine = WhisperCppSttEngine(context, settingsRepo, forceGpu = forceGpu)
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
            val engine = VoskSttEngine(context, settingsRepo, settingsRepo.getSettingsSnapshot().voiceLanguage)
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

    private suspend fun runLlamaBenchmark(modelId: String) {
        try {
            val engine = com.voxcommander.app.domain.intent.interpreter.LocalLlmInterpreter(context, settingsRepo, modelDownloader)
            val start = System.currentTimeMillis()
            // We'll test with a simple "ping" command
            engine.processCommand("ping")
            val end = System.currentTimeMillis()
            
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Local LLM",
                model = modelId,
                inferenceTimeMs = end - start,
                rtf = 0f, // RTF not applicable to LLMs
                isSuccess = true
            ))
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Local LLM",
                model = modelId,
                inferenceTimeMs = 0,
                rtf = 0f,
                isSuccess = false,
                error = e.message
            ))
        }
    }
}
