package com.voxcommander.app.domain.intent.interpreter

import android.util.Log
import com.google.gson.Gson
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.intent.model.IntentPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * L2 Engine: Cloud-based AI interpretation using OpenAI API.
 * High intelligence, requires internet and API key.
 */
class OpenAiInterpreter(
    private val settingsManager: SettingsManager
) : AssistantEngine {

    private val TAG = "OpenAiInterpreter"
    private val client = OkHttpClient()
    private val gson = Gson()

    override suspend fun processCommand(spokenText: String): IntentPayload? = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.getApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "OpenAI API Key is missing")
            return@withContext null
        }

        val systemPrompt = """
            Mapare intenții sistem Vox Commander. Reguli:
            1. category: Alege STRICT din ["audio", "settings", "maps", "home", "app"].
            2. actionType: Alege STRICT din:
               - audio: ["audio_youtube", "audio_spotify", "media_pause", "media_play", "media_next", "media_prev"]
               - settings: ["vol_up", "vol_down", "wifi_toggle", "bluetooth_toggle"]
               - maps: ["waze_nav", "maps_nav"]
            3. DACĂ este muzică și nu se specifică platforma, setează implicit actionType="audio_youtube".
            4. SINTAXĂ: "track de la artist" -> category="audio", track=track, artist=artist.
            5. Returnează EXCLUSIV un obiect JSON cu aceste 6 chei: category, actionType, artist, track, album, destination.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("temperature", 0.0) // Match MacroDroid precision
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", spokenText) })
            })
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()
            
            if (response.isSuccessful && bodyString != null) {
                val jsonResponse = JSONObject(bodyString)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                Log.d(TAG, "OpenAI Response: $content")
                return@withContext gson.fromJson(content, IntentPayload::class.java)
            } else {
                Log.e(TAG, "OpenAI API Error: ${response.code} - $bodyString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI Request Failed", e)
        }
        null
    }
}
