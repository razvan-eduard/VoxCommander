package com.voxcommander.app.domain.intent.interpreter

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cloud-based Gemini interpreter using Google AI Generative API.
 * Requires a valid Gemini API key stored in settings.
 * Model: gemini-1.5-flash (fast, cost-effective for intent extraction).
 */
class GeminiCloudInterpreter(
    private val settingsRepo: SettingsRepository
) : AssistantEngine {

    private val TAG = Strings.Tags.GEMINI_NANO_INTERPRETER
    private val gson = Gson()

    override suspend fun processCommand(spokenText: String): IntentPayload? = withContext(Dispatchers.IO) {
        val apiKey = settingsRepo.getSettingsSnapshot().geminiApiKey
        if (apiKey.isNullOrBlank()) {
            Logger.log("Gemini API key not set — cannot use Gemini Cloud", TAG)
            return@withContext null
        }

        val model = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey
        )

        val systemPrompt = PromptProvider.getNluPrompt("")

        try {
            val response = model.generateContent(
                content {
                    text(systemPrompt)
                    text("Input: \"$spokenText\"")
                    text("JSON:")
                }
            )

            val responseText = response.text ?: return@withContext null
            Logger.log("Gemini Cloud response: $responseText", TAG)

            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val cleanJson = responseText.substring(jsonStart, jsonEnd)
                return@withContext gson.fromJson(cleanJson, IntentPayload::class.java)
            }
        } catch (e: Exception) {
            Logger.log("Gemini Cloud inference failed: ${e.message}", TAG)
        }
        null
    }
}
