package com.voxcommander.app.domain.diagnostic

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.engine.google.GoogleSttEngine
import com.voxcommander.app.domain.engine.vosk.VoskSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperCppSttEngine
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.domain.intent.interpreter.GeminiNanoInterpreter
import com.voxcommander.app.domain.intent.interpreter.LocalLlmInterpreter
import com.voxcommander.app.domain.intent.interpreter.OpenAiInterpreter
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.domain.engine.whisper.WhisperSttEngine
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.BenchmarkResult
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
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
    private val modelDownloader: ModelDownloader,
    private val localLlmInterpreter: LocalLlmInterpreter? = null,
    private val geminiNanoInterpreter: GeminiNanoInterpreter? = null
) {
    companion object {
        private const val TAG = "BenchmarkEngine"
        private const val DUMMY_AUDIO_DURATION_MS = 5000L
        private const val SAMPLE_RATE = 16000

        // Standardized test command for intent engines — exercises audio category with artist/track extraction
        private const val INTENT_TEST_COMMAND = "play bohemian rhapsody by queen on youtube"
        private const val INTENT_TEST_EXPECTED_CATEGORY = "audio"
        private const val INTENT_TEST_EXPECTED_ACTION = "audio_youtube"
    }

    suspend fun runFullBenchmark() = withContext(Dispatchers.Default) {
        appStateManager.setVoiceState(VoiceState.BENCHMARKING)
        appStateManager.clearBenchmarkResults()

        // 5 seconds dummy audio (silence — measures init + inference overhead)
        val dummyAudio = ByteArray(SAMPLE_RATE * 2 * (DUMMY_AUDIO_DURATION_MS / 1000).toInt()) { 0 }

        val diagInfo = StringBuilder()
        val snapshot = settingsRepo.getSettingsSnapshot()

        // --- 1. HARDWARE INFO ---
        diagInfo.append("--- HARDWARE CAPABILITIES ---\n")
        diagInfo.append(WhisperLib.getSystemInfo())
        diagInfo.append("\n")

        // --- 2. VULKAN STATUS ---
        diagInfo.append("--- WHISPER VULKAN COMPATIBILITY ---\n")
        if (snapshot.vulkanIncompatible) {
            diagInfo.append("Status: INCOMPATIBLE (GPU crashes during Whisper inference)\n")
        } else if (snapshot.vulkanRuntimeVerified) {
            diagInfo.append("Status: VERIFIED (GPU inference tested successfully)\n")
        } else if (snapshot.vulkanProbeDone) {
            diagInfo.append("Status: COMPATIBLE (probe passed, inference not yet verified)\n")
        } else {
            diagInfo.append("Status: UNKNOWN (probe not yet run)\n")
        }
        diagInfo.append("\n")

        // --- 3. WHISPER STT BENCHMARKS (CPU + GPU per downloaded model) ---
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

        // --- 4. VOSK STT BENCHMARKS (all downloaded Vosk models) ---
        val voskKey = RemoteModelRegistry.getEngineKeyByExtension(".zip")
        val downloadedVoskModels = voskKey?.let { RemoteModelRegistry.getModels(it) }?.filter {
            snapshot.isModelDownloaded(it.id)
        } ?: emptyList()

        if (downloadedVoskModels.isNotEmpty()) {
            diagInfo.append("--- VOSK MODELS DETECTED ---\n")
            downloadedVoskModels.forEach {
                diagInfo.append("ID: ${it.id} | Label: ${it.label} | Lang: ${it.langCode ?: "multi"}\n")
            }
            diagInfo.append("Backend: Kaldi-based (libvosk.so)\n\n")

            for (model in downloadedVoskModels) {
                val langCode = model.langCode ?: snapshot.voiceLanguage
                runVoskBenchmark(model.id, model.label, langCode, dummyAudio)
            }
        }

        // --- 5. WHISPER API STT BENCHMARK ---
        val apiKey = snapshot.apiKey
        if (!apiKey.isNullOrBlank()) {
            diagInfo.append("--- CLOUD CONNECTIVITY ---\n")
            diagInfo.append("Whisper API: Active (Endpoint: OpenAI)\n")
            diagInfo.append("Key Masked: ${apiKey.take(4)}...${apiKey.takeLast(4)}\n\n")
            runApiBenchmark(apiKey, dummyAudio)
        }

        // --- 6. GOOGLE STT (Initialization-only — intent-based, no direct API) ---
        runGoogleBenchmark()

        // --- 7. LOCAL LLM INTENT BENCHMARK (MediaPipe GenAI) ---
        // Reuse the shared LocalLlmInterpreter from AppContainer to avoid native crash
        // (two LlmInference instances loading the same model causes SIGSEGV in MediaPipe)
        diagInfo.append("--- LOCAL LLM DIAGNOSTICS ---\n")
        if (localLlmInterpreter != null) {
            val activeModelId = snapshot.activeIntentModelId
            if (activeModelId != null) {
                val nluKey = RemoteModelRegistry.getEngineKeysByType("llm").firstOrNull()
                val activeModel = nluKey?.let { RemoteModelRegistry.getModels(it) }?.find { it.id == activeModelId }
                val modelLabel = activeModel?.label ?: activeModelId
                diagInfo.append("Model: $activeModelId | Label: $modelLabel (active)\n")
                runLocalLlmBenchmark(modelLabel, localLlmInterpreter)
            } else {
                diagInfo.append("NLU Model: No active model selected\n")
            }
        } else {
            diagInfo.append("NLU Model: Interpreter not available\n")
        }
        diagInfo.append("\n")

        // --- 8. OPENAI INTENT BENCHMARK (Cloud) ---
        if (!apiKey.isNullOrBlank() && snapshot.cloudIntelligenceEnabled) {
            diagInfo.append("--- OPENAI INTENT ENGINE ---\n")
            runOpenAiIntentBenchmark()
            diagInfo.append("\n")
        }

        // --- 9. GEMINI NANO INTENT BENCHMARK ---
        diagInfo.append("--- GEMINI NANO DIAGNOSTICS ---\n")
        val geminiSupported = !snapshot.geminiIncompatible
        diagInfo.append("AICore: ${if (geminiSupported) "SUPPORTED" else "INCOMPATIBLE"}\n")
        if (geminiSupported) {
            runGeminiNanoBenchmark()
        }
        diagInfo.append("\n")

        appStateManager.setSystemInfo(diagInfo.toString())
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

    private suspend fun runVoskBenchmark(modelId: String, modelLabel: String, langCode: String, audioData: ByteArray) {
        try {
            val engine = VoskSttEngine(context, settingsRepo, langCode)
            val start = System.currentTimeMillis()
            engine.transcribe(audioData)
            val end = System.currentTimeMillis()
            val elapsed = end - start
            appStateManager.updateBenchmarkResult(BenchmarkResult("Vosk", "$modelLabel ($langCode)", elapsed, elapsed.toFloat() / DUMMY_AUDIO_DURATION_MS, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Vosk", "$modelLabel ($langCode)", 0, 0f, false, e.message))
        }
    }

    private suspend fun runGoogleBenchmark() {
        try {
            val start = System.currentTimeMillis()
            val engine = GoogleSttEngine(context)
            val end = System.currentTimeMillis()
            val available = engine.isAvailable
            engine.release()
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                "Google STT",
                "Intent-based",
                end - start,
                0f,
                available,
                if (available) null else "SpeechRecognizer not available"
            ))
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Google STT", "Intent-based", 0, 0f, false, e.message))
        }
    }

    private suspend fun runApiBenchmark(apiKey: String, audioData: ByteArray) {
        try {
            val engine = WhisperSttEngine(apiKey)
            val start = System.currentTimeMillis()
            engine.transcribe(audioData)
            val end = System.currentTimeMillis()
            val elapsed = end - start
            appStateManager.updateBenchmarkResult(BenchmarkResult("Whisper API", "Cloud", elapsed, elapsed.toFloat() / DUMMY_AUDIO_DURATION_MS, true))
            engine.release()
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult("Whisper API", "Cloud", 0, 0f, false, e.message))
        }
    }

    private suspend fun runLocalLlmBenchmark(modelLabel: String, interpreter: LocalLlmInterpreter) {
        try {
            val start = System.currentTimeMillis()
            val result = interpreter.processCommand(INTENT_TEST_COMMAND)
            val end = System.currentTimeMillis()
            val elapsed = end - start

            val validation = validateIntentPayload(result)
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Local LLM",
                model = modelLabel,
                inferenceTimeMs = elapsed,
                rtf = 0f,
                isSuccess = validation.isSuccess,
                error = validation.error
            ))
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Local LLM",
                model = modelLabel,
                inferenceTimeMs = 0,
                rtf = 0f,
                isSuccess = false,
                error = e.message
            ))
        }
    }

    private suspend fun runOpenAiIntentBenchmark() {
        try {
            val engine = OpenAiInterpreter(settingsRepo)
            val start = System.currentTimeMillis()
            val result = engine.processCommand(INTENT_TEST_COMMAND)
            val end = System.currentTimeMillis()
            val elapsed = end - start

            val validation = validateIntentPayload(result)
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "OpenAI Intent",
                model = "gpt-4o-mini",
                inferenceTimeMs = elapsed,
                rtf = 0f,
                isSuccess = validation.isSuccess,
                error = validation.error
            ))
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "OpenAI Intent",
                model = "gpt-4o-mini",
                inferenceTimeMs = 0,
                rtf = 0f,
                isSuccess = false,
                error = e.message
            ))
        }
    }

    private suspend fun runGeminiNanoBenchmark() {
        if (geminiNanoInterpreter == null) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Gemini Nano",
                model = "gemini-1.5-flash",
                inferenceTimeMs = 0,
                rtf = 0f,
                isSuccess = false,
                error = "Interpreter not available"
            ))
            return
        }
        try {
            val start = System.currentTimeMillis()
            val result = geminiNanoInterpreter.processCommand(INTENT_TEST_COMMAND)
            val end = System.currentTimeMillis()
            val elapsed = end - start

            val validation = validateIntentPayload(result)
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Gemini Nano",
                model = "gemini-1.5-flash",
                inferenceTimeMs = elapsed,
                rtf = 0f,
                isSuccess = validation.isSuccess,
                error = validation.error
            ))
        } catch (e: Exception) {
            appStateManager.updateBenchmarkResult(BenchmarkResult(
                engine = "Gemini Nano",
                model = "gemini-1.5-flash",
                inferenceTimeMs = 0,
                rtf = 0f,
                isSuccess = false,
                error = e.message
            ))
        }
    }

    private data class IntentValidation(val isSuccess: Boolean, val error: String?)

    private fun validateIntentPayload(payload: IntentPayload?): IntentValidation {
        if (payload == null) {
            return IntentValidation(false, "Returned null (no JSON generated)")
        }
        if (payload.category.isBlank()) {
            return IntentValidation(false, "category is blank")
        }
        if (payload.actionType.isBlank()) {
            return IntentValidation(false, "actionType is blank")
        }
        // Check if category/action match expected values for the test command
        if (payload.category != INTENT_TEST_EXPECTED_CATEGORY) {
            return IntentValidation(false, "category='${payload.category}' (expected '$INTENT_TEST_EXPECTED_CATEGORY')")
        }
        if (payload.actionType != INTENT_TEST_EXPECTED_ACTION) {
            return IntentValidation(false, "actionType='${payload.actionType}' (expected '$INTENT_TEST_EXPECTED_ACTION')")
        }
        return IntentValidation(true, null)
    }
}
