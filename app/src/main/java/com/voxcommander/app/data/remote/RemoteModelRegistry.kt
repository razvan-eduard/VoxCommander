package com.voxcommander.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.voxcommander.app.data.preferences.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Models.json Schema Objects
 */
data class RemoteModelSchema(
    val schema_version: Int,
    val engines: RemoteEngines
)

data class RemoteEngines(
    val stt_whisper: List<RemoteModelItem>?,
    val wake_vosk: List<RemoteModelItem>?,
    val nlu_llm: List<RemoteModelItem>?
)

data class RemoteModelItem(
    val id: String,
    val label: String,
    val path: String,
    val size_mb: Int,
    val size_label: String? = null,
    val is_multilingual: Boolean? = null,
    val lang_code: String? = null,
    val engine_type: String? = null,
    val is_remote: Boolean = false
)

/**
 * Orchestrator for Dynamic Model Registration.
 * Fetches models.json from GitHub/Remote Repo and provides data to UI.
 */
object RemoteModelRegistry {
    private const val TAG = "RemoteModelRegistry"
    private val gson = Gson()
    private var cachedSchema: RemoteModelSchema? = null

    /**
     * Fetches models.json from the configured base URL.
     * Uses RAW content URL for GitHub automatically.
     */
    suspend fun fetchJson(settingsManager: SettingsManager, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && cachedSchema != null) return@withContext true
        
        // Use cached string if available and not forcing
        if (!force) {
            settingsManager.getModelsJsonCache()?.let {
                try {
                    cachedSchema = gson.fromJson(it, RemoteModelSchema::class.java)
                    if (cachedSchema != null) return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse cached JSON", e)
                }
            }
        }

        val baseUrl = settingsManager.getModelRepoBaseUrl()
        
        // Logic: Transform normal GitHub URL to RAW if needed
        // From: https://github.com/user/repo
        // To:   https://raw.githubusercontent.com/user/repo/main/models.json
        val rawUrl = if (baseUrl.contains("github.com") && !baseUrl.contains("raw.githubusercontent.com")) {
            baseUrl.replace("github.com", "raw.githubusercontent.com")
                .removeSuffix("/") + "/main/models.json"
        } else {
            if (baseUrl.endsWith("/")) "${baseUrl}models.json" else "$baseUrl/models.json"
        }

        Log.d(TAG, "Fetching remote registry from: $rawUrl")

        return@withContext try {
            val jsonText = URL(rawUrl).readText()
            val schema = gson.fromJson(jsonText, RemoteModelSchema::class.java)
            if (schema != null) {
                cachedSchema = schema
                settingsManager.saveModelsJsonCache(jsonText)
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote JSON: ${e.message}")
            false
        }
    }

    fun getWhisperModels(): List<RemoteModelItem> = cachedSchema?.engines?.stt_whisper ?: emptyList()
    fun getVoskModels(): List<RemoteModelItem> = cachedSchema?.engines?.wake_vosk ?: emptyList()
    fun getLlmModels(): List<RemoteModelItem> = cachedSchema?.engines?.nlu_llm ?: emptyList()

    /**
     * Resolves the final download URL.
     * If 'path' is already an absolute HTTP URL, use it.
     */
    fun resolveUrl(item: RemoteModelItem, settingsManager: SettingsManager): String {
        if (item.path.startsWith("http")) return item.path
        
        val baseUrl = settingsManager.getModelRepoBaseUrl()
        return if (baseUrl.contains("github.com")) {
            val cleanBase = baseUrl.removeSuffix("/")
            "$cleanBase/releases/download/${item.path}"
        } else {
            val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val cleanPath = if (item.path.startsWith("/")) item.path.substring(1) else item.path
            "$cleanBase$cleanPath"
        }
    }
}
