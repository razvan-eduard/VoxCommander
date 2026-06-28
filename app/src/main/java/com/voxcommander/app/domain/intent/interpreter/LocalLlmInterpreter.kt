package com.voxcommander.app.domain.intent.interpreter

import android.content.Context
import com.voxcommander.app.utils.Logger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.google.gson.Gson
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * L2/L3 Engine: Local LLM interpretation using MediaPipe GenAI.
 * Model path resolved dynamically from models.json via ModelDownloader.
 */
class LocalLlmInterpreter(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val modelDownloader: ModelDownloader
) : AssistantEngine {

    private val TAG = Strings.Tags.LOCAL_LLM_INTERPRETER
    private var llmInference: LlmInference? = null
    private val gson = Gson()

    private fun setupLlm() {
        if (llmInference != null) return

        val snapshot = settingsRepo.getSettingsSnapshot()
        val modelId = snapshot.activeIntentModelId ?: return
        val engineKey = snapshot.aiProcessor

        val modelFile = modelDownloader.resolveLocalFile(modelId, engineKey)
        if (modelFile == null || !modelFile.exists()) {
            Logger.log("LLM model not found for $modelId ($engineKey). Make sure it is downloaded.", TAG)
            return
        }

        // Clear potentially corrupted XNNPACK cache files from previous native crashes
        val cacheDir = context.cacheDir
        cacheDir.listFiles { f -> f.name.contains("xnnpack_cache") }?.forEach { f ->
            Logger.log("Removing stale XNNPACK cache: ${f.name}", TAG)
            f.delete()
        }

        val modelPath = modelFile.absolutePath
        Logger.log("Loading LLM model: $modelPath", TAG)

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()

        val instance = LlmInference.createFromOptions(context, options)
        if (instance == null) {
            Logger.log("LlmInference.createFromOptions returned null — model failed to load", TAG)
            return
        }
        llmInference = instance
    }

    override suspend fun processCommand(spokenText: String): NluIntent? = withContext(Dispatchers.IO) {
        setupLlm()
        val engine = llmInference ?: return@withContext null

        val hydratedPrompt = PromptProvider.getNluPrompt(spokenText, settingsRepo.getSettingsSnapshot())

        try {
            val response = engine.generateResponse(hydratedPrompt)
            Logger.log("LLM response: $response", TAG)

            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val cleanJson = response.substring(jsonStart, jsonEnd)
                return@withContext NluIntentParser.parse(cleanJson)
            }
        } catch (e: Exception) {
            Logger.log("LLM generation failed: ${e.message}", TAG)
        }
        null
    }
}
