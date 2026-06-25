package com.voxcommander.app.data.remote

import com.voxcommander.app.utils.Logger
import com.google.gson.Gson
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Models.json Schema Objects - The Wrapper
 */
data class RemoteModelSchema(
    val schema_version: Int,
    val prompts: Map<String, String>? = null,
    val engines: RemoteEngines
)

data class RemoteEngines(
    val stt_whisper: RemoteEngineConfig?,
    val wake_vosk: RemoteEngineConfig?,
    val nlu_llm: RemoteEngineConfig?
)

data class RemoteEngineConfig(
    val is_multilingual: Boolean,
    val models: List<RemoteModelItem>
)

/**
 * The Unified Model Item. Implements AppModel directly to avoid manual mapping.
 */
data class RemoteModelItem(
    override val id: String,
    override val label: String,
    val path: String,
    val size_mb: Int,
    val size_label: String? = null,
    val is_multilingual: Boolean? = null,
    val lang_code: String? = null,
    val engine_type: String? = null,
    val is_remote: Boolean = false
) : AppModel {
    override val url: String get() = path
    override val sizeDescription: String get() = size_label ?: "$size_mb MB"
    override val engineType: String get() = engine_type ?: ""
}

/**
 * Orchestrator for Dynamic Model Registration.
 * Single Source of Truth for all available models across all engines.
 * Acts as a ModelManagementParser: fetch -> cache -> wrapper.
 */
object RemoteModelRegistry {
    private const val TAG = Strings.Tags.REMOTE_MODEL_REGISTRY
    private val gson = Gson()
    
    // The Wrapper Object (The SSOT in memory)
    private var cachedSchema: RemoteModelSchema? = null

    // Reactive signal that the registry has updated
    private val _registryUpdateSignal = MutableStateFlow(0L)
    val registryUpdateSignal: StateFlow<Long> = _registryUpdateSignal.asStateFlow()

    /**
     * Fetches models.json from network or cache and populates the memory wrapper.
     */
    suspend fun fetchJson(settingsManager: SettingsManager, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // 1. Try to load from assets first (new structure)
        if (cachedSchema == null) {
            try {
                val context = settingsManager.getContext()
                val inputStream = context.assets.open("models.json")
                val localJson = inputStream.bufferedReader().use { it.readText() }
                cachedSchema = gson.fromJson(localJson, RemoteModelSchema::class.java)
                Logger.log("Loaded from assets: ${cachedSchema?.engines?.stt_whisper?.models?.size} Whisper models", TAG)
            } catch (e: Exception) {
                Logger.log("Failed to load from assets: ${e.message}", TAG)
            }
        }

        // 2. Return early if we have data and no force refresh is requested
        if (!force && cachedSchema != null) return@withContext true

        // 3. Perform network fetch (fallback)
        val baseUrl = settingsManager.getModelRepoBaseUrl()
        val rawUrlBase = if (baseUrl.contains("github.com") && !baseUrl.contains("raw.githubusercontent.com")) {
            baseUrl.replace("github.com", "raw.githubusercontent.com").removeSuffix("/") + "/main/models.json"
        } else {
            if (baseUrl.endsWith("/")) "${baseUrl}models.json" else "$baseUrl/models.json"
        }

        val rawUrl = "$rawUrlBase?t=${System.currentTimeMillis()}"

        return@withContext try {
            val jsonText = URL(rawUrl).readText()
            val schema = gson.fromJson(jsonText, RemoteModelSchema::class.java)
            if (schema != null) {
                cachedSchema = schema
                settingsManager.saveModelsJsonCache(jsonText)
                Logger.log("Parsed schema from network: ${schema.engines.stt_whisper?.models?.size} Whisper models, ${schema.engines.wake_vosk?.models?.size} Vosk models", TAG)
                _registryUpdateSignal.value++ // Signal observers to re-read from getters
                true
            } else {
                Logger.log("Schema is null after parsing", TAG)
                false
            }
        } catch (e: Exception) {
            Logger.log("Network fetch failed: ${e.message}", TAG)
            cachedSchema != null // Return true if we at least have local data
        }
    }

    // --- DIRECT GETTERS (Zero manual mapping in VM) ---
    fun getWhisperModels(): List<RemoteModelItem> = cachedSchema?.engines?.stt_whisper?.models ?: emptyList()
    fun getVoskModels(): List<RemoteModelItem> = cachedSchema?.engines?.wake_vosk?.models ?: emptyList()
    fun getLlmModels(): List<RemoteModelItem> = cachedSchema?.engines?.nlu_llm?.models ?: emptyList()

    fun isWhisperMultilingual(): Boolean = cachedSchema?.engines?.stt_whisper?.is_multilingual ?: true
    fun isVoskMultilingual(): Boolean = cachedSchema?.engines?.wake_vosk?.is_multilingual ?: false
    fun isLlmMultilingual(): Boolean = cachedSchema?.engines?.nlu_llm?.is_multilingual ?: false

    fun getVoskLanguages(): List<String> = getVoskModels()
        .mapNotNull { it.lang_code }
        .distinct()
        .sorted()

    fun getPrompt(id: String): String? = cachedSchema?.prompts?.get(id)

    /**
     * Resolves the final download URL from the item.
     */
    fun resolveUrl(item: AppModel, settingsManager: SettingsManager): String {
        if (item.url.startsWith("http")) return item.url
        val baseUrl = settingsManager.getModelRepoBaseUrl()
        return if (baseUrl.contains("github.com")) {
            val cleanBase = baseUrl.removeSuffix("/")
            "$cleanBase/releases/download/${item.url}"
        } else {
            val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val cleanPath = if (item.url.startsWith("/")) item.url.substring(1) else item.url
            "$cleanBase$cleanPath"
        }
    }
}
