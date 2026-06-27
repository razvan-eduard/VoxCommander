package com.voxcommander.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class SettingsRepositoryImpl(
    context: Context
) : SettingsRepository {

    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = DataStoreProvider.get(appContext)
    private val gson = Gson()

    // Encrypted storage for API key only
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        appContext,
        "vox_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // --- DATASTORE KEYS ---
    private object Keys {
        // Language
        val LANGUAGE = stringPreferencesKey("language")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")

        // Voice engine
        val VOICE_PROCESSOR = stringPreferencesKey("voice_processor")
        val ACTIVE_VOICE_MODEL_ID = stringPreferencesKey("active_voice_model_id")

        // Intent engine
        val AI_PROCESSOR = stringPreferencesKey("ai_processor")
        val ACTIVE_INTENT_MODEL_ID = stringPreferencesKey("active_intent_model_id")
        val CLOUD_INTELLIGENCE_ENABLED = booleanPreferencesKey("cloud_intelligence_enabled")

        // Wake word
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD_MODEL_PATH = stringPreferencesKey("wake_word_model_path")

        // Offline fallback
        val OFFLINE_FALLBACK_TIMEOUT = intPreferencesKey("offline_fallback_timeout")
        val DEFAULT_OFFLINE_MODEL = stringPreferencesKey("default_offline_model")
        val DEFAULT_VOICE_FALLBACK_PROCESSOR = stringPreferencesKey("default_voice_fallback_processor")
        val DEFAULT_VOICE_FALLBACK_MODEL = stringPreferencesKey("default_voice_fallback_model")
        val DEFAULT_INTENT_FALLBACK_PROCESSOR = stringPreferencesKey("default_intent_fallback_processor")
        val DEFAULT_INTENT_FALLBACK_MODEL = stringPreferencesKey("default_intent_fallback_model")

        // Logging
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val VERBOSE_LOGGING_ENABLED = booleanPreferencesKey("verbose_logging_enabled")

        // Vulkan
        val VULKAN_INCOMPATIBLE = booleanPreferencesKey("vulkan_incompatible")
        val VULKAN_PROBE_DONE = booleanPreferencesKey("vulkan_probe_done")
        val VULKAN_RUNTIME_ATTEMPT = booleanPreferencesKey("vulkan_runtime_attempt")
        val VULKAN_RUNTIME_VERIFIED = booleanPreferencesKey("vulkan_runtime_verified")
        val EXPERIMENTAL_VULKAN_ENABLED = booleanPreferencesKey("experimental_vulkan_enabled")

        // Gemini
        val GEMINI_INCOMPATIBLE = booleanPreferencesKey("gemini_incompatible")

        // Remote repository
        val MODEL_REPO_BASE_URL = stringPreferencesKey("model_repo_base_url")
        val MODELS_JSON_CACHE = stringPreferencesKey("models_json_cache")

        // Model download state
        val DOWNLOADED_MODEL_IDS = stringSetPreferencesKey("downloaded_model_ids")

        // Custom model paths (stored as JSON map)
        val CUSTOM_MODEL_PATHS_JSON = stringPreferencesKey("custom_model_paths_json")

        // Per-engine model selections (stored as JSON map)
        val ENGINE_MODEL_SELECTIONS_JSON = stringPreferencesKey("engine_model_selections_json")
    }

    private val TAG = "SettingsRepository"

    // Migration flag key
    private val KEY_MIGRATION_DONE = booleanPreferencesKey("migration_from_shared_prefs_done")

    /**
     * One-time migration from old EncryptedSharedPreferences to DataStore.
     * Reads all values from the old prefs file and writes them to DataStore.
     * Safe to call on every launch - only runs once.
     */
    suspend fun migrateFromSharedPreferencesIfNeeded() {
        val current = dataStore.data.first()
        if (current[KEY_MIGRATION_DONE] == true) return

        Logger.log("Starting migration from EncryptedSharedPreferences to DataStore", TAG)
        try {
            val oldPrefs = EncryptedSharedPreferences.create(
                appContext,
                Strings.Preferences.PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val all = oldPrefs.all
            if (all.isEmpty()) {
                Logger.log("Old prefs empty, marking migration done", TAG)
                dataStore.edit { it[KEY_MIGRATION_DONE] = true }
                return
            }

            // Migrate API key to new secure prefs
            oldPrefs.getString(Strings.Preferences.KEY_API_KEY, null)?.let { key ->
                encryptedPrefs.edit().putString("api_key", key).apply()
            }

            dataStore.edit { prefs ->
                // Language
                all[Strings.Preferences.KEY_LANGUAGE]?.let { prefs[Keys.LANGUAGE] = it as String }
                all[Strings.Preferences.KEY_VOICE_LANGUAGE]?.let { prefs[Keys.VOICE_LANGUAGE] = it as String }

                // Voice engine
                all[Strings.Preferences.KEY_VOICE_PROCESSOR]?.let { prefs[Keys.VOICE_PROCESSOR] = it as String }
                all[Strings.Preferences.KEY_ACTIVE_VOICE_MODEL_ID]?.let { prefs[Keys.ACTIVE_VOICE_MODEL_ID] = it as String }

                // Intent engine
                all[Strings.Preferences.KEY_AI_PROCESSOR]?.let { prefs[Keys.AI_PROCESSOR] = it as String }
                all[Strings.Preferences.KEY_ACTIVE_INTENT_MODEL_ID]?.let { prefs[Keys.ACTIVE_INTENT_MODEL_ID] = it as String }
                all[Strings.Preferences.KEY_CLOUD_INTELLIGENCE_ENABLED]?.let { prefs[Keys.CLOUD_INTELLIGENCE_ENABLED] = it as Boolean }

                // Wake word
                all[Strings.Preferences.KEY_WAKE_WORD]?.let { prefs[Keys.WAKE_WORD] = it as String }
                all[Strings.Preferences.KEY_WAKE_WORD_ENABLED]?.let { prefs[Keys.WAKE_WORD_ENABLED] = it as Boolean }
                all[Strings.Preferences.KEY_WAKE_WORD_MODEL_PATH]?.let { prefs[Keys.WAKE_WORD_MODEL_PATH] = it as String }

                // Offline fallback
                all[Strings.Preferences.KEY_OFFLINE_FALLBACK_TIMEOUT]?.let { prefs[Keys.OFFLINE_FALLBACK_TIMEOUT] = it as Int }
                all["default_offline_model"]?.let { prefs[Keys.DEFAULT_OFFLINE_MODEL] = it as String }
                all["default_voice_fallback_processor"]?.let { prefs[Keys.DEFAULT_VOICE_FALLBACK_PROCESSOR] = it as String }
                all["default_voice_fallback_model"]?.let { prefs[Keys.DEFAULT_VOICE_FALLBACK_MODEL] = it as String }
                all["default_intent_fallback_processor"]?.let { prefs[Keys.DEFAULT_INTENT_FALLBACK_PROCESSOR] = it as String }
                all["default_intent_fallback_model"]?.let { prefs[Keys.DEFAULT_INTENT_FALLBACK_MODEL] = it as String }

                // Logging
                all["log_level"]?.let { prefs[Keys.LOG_LEVEL] = it as String }
                all[Strings.Preferences.KEY_VERBOSE_LOGGING]?.let { prefs[Keys.VERBOSE_LOGGING_ENABLED] = it as Boolean }

                // Vulkan
                all[Strings.Preferences.KEY_VULKAN_INCOMPATIBLE]?.let { prefs[Keys.VULKAN_INCOMPATIBLE] = it as Boolean }
                all[Strings.Preferences.KEY_VULKAN_PROBE_DONE]?.let { prefs[Keys.VULKAN_PROBE_DONE] = it as Boolean }
                all[Strings.Preferences.KEY_VULKAN_RUNTIME_ATTEMPT]?.let { prefs[Keys.VULKAN_RUNTIME_ATTEMPT] = it as Boolean }
                all[Strings.Preferences.KEY_VULKAN_RUNTIME_VERIFIED]?.let { prefs[Keys.VULKAN_RUNTIME_VERIFIED] = it as Boolean }
                all["experimental_vulkan_enabled"]?.let { prefs[Keys.EXPERIMENTAL_VULKAN_ENABLED] = it as Boolean }

                // Gemini
                all["gemini_incompatible"]?.let { prefs[Keys.GEMINI_INCOMPATIBLE] = it as Boolean }

                // Remote repository
                all[Strings.Preferences.KEY_MODEL_REPO_BASE_URL]?.let { prefs[Keys.MODEL_REPO_BASE_URL] = it as String }
                all[Strings.Preferences.KEY_MODELS_JSON_CACHE]?.let { prefs[Keys.MODELS_JSON_CACHE] = it as String }

                // Model downloaded flags -> collect into set
                val downloadedIds = all.keys
                    .filter { it.startsWith(Strings.Preferences.KEY_MODEL_DOWNLOADED_PREFIX) }
                    .filter { all[it] as? Boolean == true }
                    .map { it.removePrefix(Strings.Preferences.KEY_MODEL_DOWNLOADED_PREFIX) }
                    .toSet()
                if (downloadedIds.isNotEmpty()) {
                    prefs[Keys.DOWNLOADED_MODEL_IDS] = downloadedIds
                }

                // Custom model paths -> collect into JSON map
                val customPaths = mutableMapOf<String, String>()
                all.keys.filter { it.startsWith("custom_model_path_") }.forEach { key ->
                    val path = all[key] as? String
                    if (!path.isNullOrBlank()) {
                        val mapKey = key.removePrefix("custom_model_path_")
                        customPaths[mapKey] = path
                    }
                }
                if (customPaths.isNotEmpty()) {
                    prefs[Keys.CUSTOM_MODEL_PATHS_JSON] = gson.toJson(customPaths)
                }

                prefs[KEY_MIGRATION_DONE] = true
            }

            // Clear old prefs after successful migration
            oldPrefs.edit().clear().apply()
            Logger.log("Migration complete, old prefs cleared", TAG)
        } catch (e: Exception) {
            Logger.log("Migration failed: ${e.message}", TAG)
            // Mark as done anyway to avoid retrying on every launch
            dataStore.edit { it[KEY_MIGRATION_DONE] = true }
        }
    }

    // --- REACTIVE FLOW ---
    override val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            apiKey = encryptedPrefs.getString("api_key", null),

            language = prefs[Keys.LANGUAGE] ?: Strings.Preferences.DEFAULT_LANGUAGE,
            voiceLanguage = prefs[Keys.VOICE_LANGUAGE] ?: Strings.Preferences.DEFAULT_LANGUAGE,

            voiceProcessor = prefs[Keys.VOICE_PROCESSOR] ?: com.voxcommander.app.data.remote.RemoteModelRegistry.getDefaultVoiceEngineKey() ?: "",
            activeVoiceModelId = prefs[Keys.ACTIVE_VOICE_MODEL_ID],

            aiProcessor = prefs[Keys.AI_PROCESSOR] ?: com.voxcommander.app.data.remote.RemoteModelRegistry.getDefaultLlmEngineKey() ?: "",
            activeIntentModelId = prefs[Keys.ACTIVE_INTENT_MODEL_ID],
            cloudIntelligenceEnabled = prefs[Keys.CLOUD_INTELLIGENCE_ENABLED] ?: false,

            engineModelSelections = parseStringMap(prefs[Keys.ENGINE_MODEL_SELECTIONS_JSON]),

            wakeWord = prefs[Keys.WAKE_WORD] ?: "hi vosk",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            wakeWordModelPath = prefs[Keys.WAKE_WORD_MODEL_PATH],

            offlineFallbackTimeout = prefs[Keys.OFFLINE_FALLBACK_TIMEOUT] ?: 10,
            defaultOfflineModel = prefs[Keys.DEFAULT_OFFLINE_MODEL] ?: "tiny",
            defaultVoiceFallbackProcessor = prefs[Keys.DEFAULT_VOICE_FALLBACK_PROCESSOR],
            defaultVoiceFallbackModel = prefs[Keys.DEFAULT_VOICE_FALLBACK_MODEL],
            defaultIntentFallbackProcessor = prefs[Keys.DEFAULT_INTENT_FALLBACK_PROCESSOR],
            defaultIntentFallbackModel = prefs[Keys.DEFAULT_INTENT_FALLBACK_MODEL],

            logLevel = prefs[Keys.LOG_LEVEL] ?: "LOGCAT_ONLY",
            verboseLoggingEnabled = prefs[Keys.VERBOSE_LOGGING_ENABLED] ?: false,

            vulkanIncompatible = prefs[Keys.VULKAN_INCOMPATIBLE] ?: false,
            vulkanProbeDone = prefs[Keys.VULKAN_PROBE_DONE] ?: false,
            vulkanRuntimeAttempt = prefs[Keys.VULKAN_RUNTIME_ATTEMPT] ?: false,
            vulkanRuntimeVerified = prefs[Keys.VULKAN_RUNTIME_VERIFIED] ?: false,
            experimentalVulkanEnabled = prefs[Keys.EXPERIMENTAL_VULKAN_ENABLED] ?: false,

            geminiIncompatible = prefs[Keys.GEMINI_INCOMPATIBLE] ?: false,

            modelRepoBaseUrl = prefs[Keys.MODEL_REPO_BASE_URL] ?: Strings.Preferences.DEFAULT_MODEL_REPO_URL,
            modelsJsonCache = prefs[Keys.MODELS_JSON_CACHE],

            downloadedModelIds = prefs[Keys.DOWNLOADED_MODEL_IDS] ?: emptySet(),
            customModelPaths = parseCustomModelPaths(prefs[Keys.CUSTOM_MODEL_PATHS_JSON])
        )
    }

    // --- SYNCHRONOUS READS ---
    override fun getSettingsSnapshot(): AppSettings = runBlocking { settingsFlow.first() }

    override fun getApiKeySync(): String? = encryptedPrefs.getString("api_key", null)

    // --- SYNCHRONOUS WRITE (crash cookie) ---
    override fun setVulkanRuntimeAttemptSync(active: Boolean) {
        // Must be synchronous: if process crashes during GPU work, this flag must already be on disk.
        // Use runBlocking to ensure DataStore writes before returning.
        runBlocking {
            dataStore.edit { prefs ->
                prefs[Keys.VULKAN_RUNTIME_ATTEMPT] = active
            }
        }
    }

    // --- API / CLOUD ---
    override suspend fun setApiKey(key: String?) {
        encryptedPrefs.edit().apply {
            if (key != null) putString("api_key", key) else remove("api_key")
        }.apply()
    }

    // --- LANGUAGE ---
    override suspend fun setLanguage(lang: String) {
        dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    override suspend fun setVoiceLanguage(lang: String) {
        dataStore.edit { it[Keys.VOICE_LANGUAGE] = lang }
    }

    // --- VOICE ENGINE ---
    override suspend fun setVoiceProcessor(processor: String) {
        dataStore.edit { it[Keys.VOICE_PROCESSOR] = processor }
    }

    override suspend fun setActiveVoiceModelId(modelId: String?) {
        dataStore.edit { prefs ->
            if (modelId != null) prefs[Keys.ACTIVE_VOICE_MODEL_ID] = modelId
            else prefs.remove(Keys.ACTIVE_VOICE_MODEL_ID)
        }
    }

    override suspend fun setEngineModelSelection(engineKey: String, modelId: String) {
        dataStore.edit { prefs ->
            val currentMap = parseStringMap(prefs[Keys.ENGINE_MODEL_SELECTIONS_JSON]).toMutableMap()
            currentMap[engineKey] = modelId
            prefs[Keys.ENGINE_MODEL_SELECTIONS_JSON] = gson.toJson(currentMap)
        }
    }

    // --- INTENT ENGINE ---
    override suspend fun setAiProcessor(processor: String) {
        dataStore.edit { it[Keys.AI_PROCESSOR] = processor }
    }

    override suspend fun setActiveIntentModelId(modelId: String?) {
        dataStore.edit { prefs ->
            if (modelId != null) prefs[Keys.ACTIVE_INTENT_MODEL_ID] = modelId
            else prefs.remove(Keys.ACTIVE_INTENT_MODEL_ID)
        }
    }

    override suspend fun setCloudIntelligenceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CLOUD_INTELLIGENCE_ENABLED] = enabled }
    }

    // --- WAKE WORD ---
    override suspend fun setWakeWord(word: String) {
        dataStore.edit { it[Keys.WAKE_WORD] = word }
    }

    override suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    override suspend fun setWakeWordModelPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.WAKE_WORD_MODEL_PATH] = path
            else prefs.remove(Keys.WAKE_WORD_MODEL_PATH)
        }
    }

    // --- OFFLINE FALLBACK ---
    override suspend fun setOfflineFallbackTimeout(seconds: Int) {
        dataStore.edit { it[Keys.OFFLINE_FALLBACK_TIMEOUT] = seconds }
    }

    override suspend fun setDefaultOfflineModel(modelId: String) {
        dataStore.edit { it[Keys.DEFAULT_OFFLINE_MODEL] = modelId }
    }

    override suspend fun clearDefaultOfflineModel() {
        dataStore.edit { it.remove(Keys.DEFAULT_OFFLINE_MODEL) }
    }

    override suspend fun setDefaultVoiceFallback(processor: String, modelId: String) {
        dataStore.edit {
            it[Keys.DEFAULT_VOICE_FALLBACK_PROCESSOR] = processor
            it[Keys.DEFAULT_VOICE_FALLBACK_MODEL] = modelId
        }
    }

    override suspend fun clearDefaultVoiceFallback() {
        dataStore.edit {
            it.remove(Keys.DEFAULT_VOICE_FALLBACK_PROCESSOR)
            it.remove(Keys.DEFAULT_VOICE_FALLBACK_MODEL)
        }
    }

    override suspend fun setDefaultIntentFallback(processor: String, modelId: String) {
        dataStore.edit {
            it[Keys.DEFAULT_INTENT_FALLBACK_PROCESSOR] = processor
            it[Keys.DEFAULT_INTENT_FALLBACK_MODEL] = modelId
        }
    }

    override suspend fun clearDefaultIntentFallback() {
        dataStore.edit {
            it.remove(Keys.DEFAULT_INTENT_FALLBACK_PROCESSOR)
            it.remove(Keys.DEFAULT_INTENT_FALLBACK_MODEL)
        }
    }

    override suspend fun clearDefaultOfflineFallback() {
        clearDefaultVoiceFallback()
        clearDefaultIntentFallback()
    }

    // --- LOGGING ---
    override suspend fun setLogLevel(level: String) {
        dataStore.edit { it[Keys.LOG_LEVEL] = level }
    }

    override suspend fun setVerboseLoggingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VERBOSE_LOGGING_ENABLED] = enabled }
    }

    // --- VULKAN ---
    override suspend fun setVulkanIncompatible(incompatible: Boolean) {
        dataStore.edit { it[Keys.VULKAN_INCOMPATIBLE] = incompatible }
    }

    override suspend fun setVulkanProbeDone(done: Boolean) {
        dataStore.edit { it[Keys.VULKAN_PROBE_DONE] = done }
    }

    override suspend fun setVulkanRuntimeVerified(verified: Boolean) {
        dataStore.edit { it[Keys.VULKAN_RUNTIME_VERIFIED] = verified }
    }

    override suspend fun setExperimentalVulkanEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.EXPERIMENTAL_VULKAN_ENABLED] = enabled }
    }

    // --- GEMINI ---
    override suspend fun setGeminiIncompatible(incompatible: Boolean) {
        dataStore.edit { it[Keys.GEMINI_INCOMPATIBLE] = incompatible }
    }

    // --- REMOTE REPOSITORY ---
    override suspend fun setModelRepoBaseUrl(url: String) {
        dataStore.edit { it[Keys.MODEL_REPO_BASE_URL] = url }
    }

    override suspend fun saveModelsJsonCache(json: String) {
        dataStore.edit { it[Keys.MODELS_JSON_CACHE] = json }
    }

    override suspend fun clearModelsJsonCache() {
        dataStore.edit { it.remove(Keys.MODELS_JSON_CACHE) }
    }

    // --- MODEL DOWNLOAD STATE ---
    override suspend fun setModelDownloaded(modelId: String, isDownloaded: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DOWNLOADED_MODEL_IDS] ?: emptySet()
            val updated = if (isDownloaded) current + modelId else current - modelId
            prefs[Keys.DOWNLOADED_MODEL_IDS] = updated
        }
    }

    override suspend fun clearUnusedModelFlags(protectedIds: Set<String>) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DOWNLOADED_MODEL_IDS] ?: emptySet()
            prefs[Keys.DOWNLOADED_MODEL_IDS] = current.intersect(protectedIds)
        }
    }

    // --- CUSTOM MODEL PATHS ---
    override suspend fun setCustomModelPath(engineKey: String, path: String, langCode: String?) {
        dataStore.edit { prefs ->
            val mapKey = if (langCode != null) "${engineKey}_$langCode" else engineKey
            val currentJson = prefs[Keys.CUSTOM_MODEL_PATHS_JSON] ?: "{}"
            val currentMap = parseCustomModelPaths(currentJson).toMutableMap()
            currentMap[mapKey] = path
            prefs[Keys.CUSTOM_MODEL_PATHS_JSON] = gson.toJson(currentMap)
        }
    }

    // --- HELPERS ---
    private fun parseCustomModelPaths(json: String?): Map<String, String> = parseStringMap(json)

    private fun parseStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                Map::class.java, String::class.java, String::class.java
            ).type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Logger.log("Failed to parse string map: ${e.message}", TAG)
            emptyMap()
        }
    }
}
