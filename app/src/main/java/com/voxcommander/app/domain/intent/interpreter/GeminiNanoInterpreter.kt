package com.voxcommander.app.domain.intent.interpreter

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * L2/L3 Engine: Gemini Nano (System-level LLM) using Google AI Edge SDK.
 * Note: Actual on-device Gemini Nano requires AICore on supported devices (Pixel 8+, S24+).
 */
class GeminiNanoInterpreter(private val context: Context) : AssistantEngine {

    private val TAG = Strings.Tags.GEMINI_NANO_INTERPRETER
    private val gson = Gson()
    
    // The modelName "gemini-1.5-flash" is used as a proxy; on-device Gemini Nano
    // is often routed through specific system-bound initializers in newer SDKs.
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "" // On-device system LLM doesn't use standard cloud API keys
    )

    override suspend fun processCommand(spokenText: String): IntentPayload? = withContext(Dispatchers.IO) {
        val systemPrompt = """
            Mapare intenții sistem. Reguli:
            1. category: ["audio", "settings", "maps", "home", "app"].
            2. actionType: ["audio_youtube", "audio_spotify", "media_pause", "media_play", "media_next", "media_prev", "vol_up", "vol_down", "wifi_toggle", "bluetooth_toggle", "waze_nav", "maps_nav"].
            3. Returnează EXCLUSIV JSON: category, actionType, artist, track, album, destination.
        """.trimIndent()

        try {
            val response = model.generateContent(
                content {
                    text(systemPrompt)
                    text("Input: \"$spokenText\"")
                    text("JSON:")
                }
            )

            val responseText = response.text ?: return@withContext null
            Log.d(TAG, "Gemini Nano Raw Response: $responseText")

            // Universal JSON Extraction
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val cleanJson = responseText.substring(jsonStart, jsonEnd)
                return@withContext gson.fromJson(cleanJson, IntentPayload::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Nano inference failed", e)
        }
        null
    }
}
