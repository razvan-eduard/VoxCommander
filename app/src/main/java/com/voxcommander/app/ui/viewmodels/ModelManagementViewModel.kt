package com.voxcommander.app.ui.viewmodels

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.data.remote.ModelDownloader
import com.voxcommander.app.data.remote.RemoteModelRegistry
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.domain.intent.interpreter.LlmModelInfo
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.FileHelper
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for managing model downloads and cleanup.
 * Orchestrates UI state for all model types (Vosk, Whisper, Llama) directly from RemoteModelRegistry.
 */
class ModelManagementViewModel(
    private val settingsManager: SettingsManager,
    private val appStateManager: AppStateManager,
    private val modelDownloader: ModelDownloader,
    private val languageManager: LanguageManager,
    private val context: Context
) : ViewModel() {

    // --- REACTIVE MODEL LISTS (Zero manual mapping) ---
    private val _voskModels = MutableStateFlow<List<AppModel>>(emptyList())
    val voskModels: StateFlow<List<AppModel>> = _voskModels.asStateFlow()

    private val _whisperModels = MutableStateFlow<List<AppModel>>(emptyList())
    val whisperModels: StateFlow<List<AppModel>> = _whisperModels.asStateFlow()

    private val _llamaModels = MutableStateFlow<List<AppModel>>(emptyList())
    val llamaModels: StateFlow<List<AppModel>> = _llamaModels.asStateFlow()

    // --- OTHER UI STATES ---
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _selectionSuccessMessage = MutableStateFlow<String?>(null)
    val selectionSuccessMessage: StateFlow<String?> = _selectionSuccessMessage.asStateFlow()

    private val _isVoskLoading = MutableStateFlow(false)
    val isVoskLoading: StateFlow<Boolean> = _isVoskLoading.asStateFlow()

    private val _isVoskOffline = MutableStateFlow(false)
    val isVoskOffline: StateFlow<Boolean> = _isVoskOffline.asStateFlow()

    private val _voskError = MutableStateFlow<String?>(null)
    val voskError: StateFlow<String?> = _voskError.asStateFlow()

    private val _showVulkanError = MutableStateFlow(false)
    val showVulkanError: StateFlow<Boolean> = _showVulkanError.asStateFlow()

    fun dismissVulkanError() { _showVulkanError.value = false }

    val vulkanTestState = appStateManager.vulkanTestState
    val vulkanTestPassed = appStateManager.vulkanTestPassed

    fun dismissVulkanTestResult() { appStateManager.dismissVulkanTestResult() }

    // --- DOWNLOAD TRACKING ---
    private var progressJob: Job? = null
    private var currentDownloadId: Long? = null
    private var lastDownloadType: String? = null
    private var lastDownloadedId: String? = null

    private val _downloadingItem = MutableStateFlow<AppModel?>(null)
    val downloadingItem: StateFlow<AppModel?> = _downloadingItem.asStateFlow()

    private var handledDownloadIds = mutableSetOf<Long>()

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id != -1L && !handledDownloadIds.contains(id)) {
                handledDownloadIds.add(id)
                _downloadProgress.value = null
                progressJob?.cancel()

                val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)

                var success = false
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true
                    }
                }
                cursor?.close()

                if (success) handleDownloadSuccess() else handleDownloadFailure()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(context, onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        
        // AUTO-SYNC: Rebuild all UI lists whenever the Registry Wrapper is updated
        viewModelScope.launch {
            RemoteModelRegistry.registryUpdateSignal.collectLatest { 
                rebuildUiLists()
            }
        }

        // Initial fetch
        viewModelScope.launch { loadModels(force = false) }
    }

    private fun rebuildUiLists() {
        _whisperModels.value = RemoteModelRegistry.getWhisperModels()
        _voskModels.value = RemoteModelRegistry.getVoskModels()
        _llamaModels.value = RemoteModelRegistry.getLlmModels()

        _isVoskOffline.value = _voskModels.value.isEmpty()
    }

    suspend fun loadModels(force: Boolean = false) {
        _isVoskLoading.value = true
        RemoteModelRegistry.fetchJson(settingsManager, force)
        _isVoskLoading.value = false
    }

    suspend fun loadVoskModels(force: Boolean = false) = loadModels(force)

    // --- DOWNLOAD METHODS ---

    fun downloadVoskModel(lang: String, url: String, name: String) {
        lastDownloadedId = name; lastDownloadType = "vosk"
        val item = RemoteModelRegistry.getVoskModels().find { it.id == name } ?: return
        _downloadingItem.value = item
        appStateManager.setSelectedVoskModelName(name)
        val id = modelDownloader.downloadVoskModel(lang, RemoteModelRegistry.resolveUrl(item, settingsManager), name)
        currentDownloadId = id; startProgressTracking(id)
    }

    fun downloadWhisperModel(modelId: String, url: String) {
        lastDownloadedId = modelId; lastDownloadType = "whisper"
        val item = RemoteModelRegistry.getWhisperModels().find { it.id == modelId } ?: return
        _downloadingItem.value = item
        appStateManager.setSelectedWhisperModelId(modelId)
        val id = modelDownloader.downloadWhisperModel(modelId, RemoteModelRegistry.resolveUrl(item, settingsManager))
        currentDownloadId = id; startProgressTracking(id)
    }

    fun downloadLlamaModel(modelId: String, url: String) {
        lastDownloadedId = modelId; lastDownloadType = "llama"
        val item = RemoteModelRegistry.getLlmModels().find { it.id == modelId } ?: return
        _downloadingItem.value = item
        appStateManager.setSelectedLlamaModelId(modelId)
        val id = modelDownloader.downloadNluModel(modelId, RemoteModelRegistry.resolveUrl(item, settingsManager))
        currentDownloadId = id; startProgressTracking(id)
    }

    fun cancelDownload() {
        currentDownloadId?.let { id ->
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)?.remove(id)
            progressJob?.cancel(); _downloadProgress.value = null
            _downloadingItem.value?.let { item ->
                val root = context.getExternalFilesDir(null)
                when (item.engineType) {
                    "Whisper" -> root?.let { File(it, "whisper-model-${item.id}.bin").delete() }
                    "Llama" -> root?.let { File(it, "nlu-model-${item.id}.bin").delete() }
                    "Vosk" -> context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "vosk-model-${item.id}.zip").delete() }
                }
            }
            _downloadingItem.value = null; appStateManager.refreshAll()
            showSuccessMessage(languageManager.getString("error_download_failed"))
        }
    }

    fun selectCustomVoskModel(uri: Uri, lang: String) {
        settingsManager.saveCustomVoskModelPath(lang, uri.path ?: uri.toString())
        settingsManager.setVoiceModelReady(true)
        showSuccessMessage(languageManager.getString("success_custom_vosk"))
    }

    fun selectCustomWhisperModel(uri: Uri) {
        FileHelper.copyUriToInternal(context, uri, "custom-whisper-model.bin")?.let { path ->
            settingsManager.saveCustomWhisperModelPath(path)
            settingsManager.setVoiceModelReady(true)
            showSuccessMessage(languageManager.getString("success_custom_whisper"))
        }
    }

    fun clearDefaultOfflineFallback() { settingsManager.clearDefaultOfflineFallback(); appStateManager.refreshAll() }

    fun deleteUnusedModels() {
        val voiceProc = settingsManager.getVoiceProcessor()
        val selVosk = settingsManager.getSelectedVoskModelName()
        val selWhisp = settingsManager.getSelectedWhisperModelId()
        val selLlama = settingsManager.getSelectedLlamaModelId()

        val protVosk = mutableSetOf<String>().apply { selVosk?.let { add(it) }; settingsManager.getWakeWordModelPath()?.let { add(it) } }
        val protWhisp = setOf(selWhisp)
        val protLlama = setOf(selLlama)

        modelDownloader.deleteUnusedModels(protVosk, protWhisp, protLlama)
        reassignDefaultFallbacks(voiceProc, selVosk, selWhisp, selLlama)
        settingsManager.clearUnusedModelFlags(selVosk ?: "", selWhisp)
        appStateManager.refreshAll()
    }

    private fun reassignDefaultFallbacks(proc: String, vosk: String?, whisp: String, llama: String) {
        if (proc == Strings.Processors.VOSK && vosk != null && isModelPresent("vosk", vosk)) settingsManager.saveDefaultVoiceFallback(proc, vosk)
        else if (proc.startsWith("WHISPER") && isModelPresent("whisper", whisp)) settingsManager.saveDefaultVoiceFallback(proc, whisp)
        if (isModelPresent("llama", llama)) settingsManager.saveDefaultIntentFallback(Strings.AiProcessors.NLU_LOCAL, llama)
    }

    private fun isModelPresent(type: String, id: String): Boolean {
        val root = context.getExternalFilesDir(null) ?: return false
        return when (type) {
            "whisper" -> File(root, "whisper-model-$id.bin").exists()
            "llama" -> File(root, "nlu-model-$id.bin").exists()
            "vosk" -> File(root, "vosk-model-$id").exists()
            else -> false
        }
    }

    fun deleteLlamaModel(id: String) {
        settingsManager.setModelDownloaded(id, false)
        FileHelper.deleteModelFile(context, id, "llama")
        appStateManager.refreshAll()
    }

    private fun startProgressTracking(id: Long) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return@launch
            while (true) {
                dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0) _downloadProgress.value = downloaded.toFloat() / total.toFloat()
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) return@launch
                    }
                }
                delay(500)
            }
        }
    }

    private fun handleDownloadSuccess() {
        _downloadProgress.value = null; _downloadingItem.value = null; progressJob?.cancel()
        when (lastDownloadType) {
            "whisper" -> {
                val id = lastDownloadedId ?: settingsManager.getSelectedWhisperModelId()
                modelDownloader.verifyWhisperModel(id) { if (it) appStateManager.onWhisperDownloadComplete(id) }
            }
            "vosk" -> {
                val name = lastDownloadedId ?: ""
                modelDownloader.unzipVoskModel(name) { if (it) appStateManager.onVoskDownloadComplete(name) }
            }
            "llama" -> {
                val id = lastDownloadedId ?: settingsManager.getSelectedLlamaModelId()
                if (File(context.getExternalFilesDir(null), "nlu-model-$id.bin").exists()) {
                    settingsManager.setModelDownloaded(id, true); appStateManager.refreshAll()
                }
            }
        }
        lastDownloadType = null
    }

    private fun handleDownloadFailure() {
        showSuccessMessage(languageManager.getString("error_download_failed"))
        lastDownloadType = null
    }

    private fun showSuccessMessage(msg: String) {
        _selectionSuccessMessage.value = msg
        viewModelScope.launch { delay(5000); _selectionSuccessMessage.value = null }
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(onDownloadComplete)
        progressJob?.cancel()
    }
}
