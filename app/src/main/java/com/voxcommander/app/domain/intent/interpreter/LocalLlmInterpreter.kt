package com.voxcommander.app.domain.intent.interpreter

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.data.preferences.SettingsManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * L2 Engine: Local LLM interpretation using MediaPipe (Llama 3.2 1B).
 */
class LocalLlmInterpreter(
    private val context: Context,
    private val settingsManager: SettingsManager
) : AssistantEngine {

    private val TAG = "LocalLlmInterpreter"
    private var llmInference: LlmInference? = null
    private val gson = Gson()

    private fun setupLlm() {
        if (llmInference != null) return

        val modelId = settingsManager.getSelectedLlamaModelId()
        val modelPath = File(context.getExternalFilesDir(null), "llama-model-$modelId.bin").absolutePath

        if (!File(modelPath).exists()) {
            Log.e(TAG, "Llama model not found at $modelPath. Make sure it is downloaded.")
            return
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(128)
            .build()

        llmInference = LlmInference.createFromOptions(context, options)
    }

    override suspend fun processCommand(spokenText: String): IntentPayload? = withContext(Dispatchers.IO) {
        setupLlm()
        val engine = llmInference ?: return@withContext null

        val hydratedPrompt = PromptProvider.getNluPrompt(spokenText)

        try {
            val response = engine.generateResponse(hydratedPrompt)
            Log.d(TAG, "Llama response: $response")

            // Basic cleaning to ensure we have only JSON
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val cleanJson = response.substring(jsonStart, jsonEnd)
                return@withContext gson.fromJson(cleanJson, IntentPayload::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Llama generation failed", e)
        }
        null
    }
}
