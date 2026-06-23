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
import com.voxcommander.app.domain.engine.vosk.VoskLanguageGroup
import com.voxcommander.app.domain.engine.vosk.VoskModelRegistry
import com.voxcommander.app.domain.intent.interpreter.LlmModelInfo
import com.voxcommander.app.domain.model.AppModel
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.FileHelper
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for managing model downloads and cleanup.
 * Handles download progress, completion, and model management logic.
 */
class ModelManagementViewModel(
    private val settingsManager: SettingsManager,
    private val appStateManager: AppStateManager,
    private val modelDownloader: ModelDownloader,
    private val context: Context
) : ViewModel() {

    // --- STATE FLOWS ---
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _selectionSuccessMessage = MutableStateFlow<String?>(null)
    val selectionSuccessMessage: StateFlow<String?> = _selectionSuccessMessage.asStateFlow()

    // --- VOSK MODEL STATE ---
    private val _voskGroups = MutableStateFlow<List<VoskLanguageGroup>>(emptyList())
    val voskGroups: StateFlow<List<VoskLanguageGroup>> = _voskGroups.asStateFlow()

    private val _isVoskLoading = MutableStateFlow(false)
    val isVoskLoading: StateFlow<Boolean> = _isVoskLoading.asStateFlow()

    private val _isVoskOffline = MutableStateFlow(false)
    val isVoskOffline: StateFlow<Boolean> = _isVoskOffline.asStateFlow()

    private val _voskError = MutableStateFlow<String?>(null)
    val voskError: StateFlow<String?> = _voskError.asStateFlow()

    // --- VULKAN TEST MODAL STATE ---
    private val _showVulkanError = MutableStateFlow(false)
    val showVulkanError: StateFlow<Boolean> = _showVulkanError.asStateFlow()

    fun dismissVulkanError() {
        _showVulkanError.value = false
    }

    // Expose Vulkan test state from AppStateManager
    val vulkanTestState = appStateManager.vulkanTestState
    val vulkanTestPassed = appStateManager.vulkanTestPassed

    fun dismissVulkanTestResult() {
        appStateManager.dismissVulkanTestResult()
    }

    // --- DOWNLOAD TRACKING ---
    private var progressJob: Job? = null
    private var currentDownloadId: Long? = null
    private var lastDownloadedVoskModelName: String? = null
    private var lastDownloadedWhisperModelId: String? = null
    private var lastDownloadedLlamaModelId: String? = null
    private var lastDownloadType: String? = null // "vosk", "whisper", or "llama"

    // Unified download item state
    private val _downloadingItem = MutableStateFlow<AppModel?>(null)
    val downloadingItem: StateFlow<AppModel?> = _downloadingItem.asStateFlow()

    // --- BROADCAST RECEIVER FOR DOWNLOAD COMPLETION ---
    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
            if (id != -1L) {
                _downloadProgress.value = null
                progressJob?.cancel()

                val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager == null) {
                    handleDownloadFailure()
                    return
                }

                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)

                var success = false
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            success = true
                        }
                    }
                }
                cursor.close()

                if (!success) {
                    handleDownloadFailure()
                    return
                }

                handleDownloadSuccess()
            }
        }
    }

    init {
        // Register broadcast receiver
        ContextCompat.registerReceiver(
            context,
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        // Initial load
        viewModelScope.launch { loadVoskModels(force = true) }
    }

    /**
     * Reactively loads Vosk models from the registry.
     */
    suspend fun loadVoskModels(force: Boolean = false) {
        _isVoskLoading.value = true
        val result = VoskModelRegistry.getModels(force)
        _voskGroups.value = result.groups
        _isVoskOffline.value = !result.isOnline
        _voskError.value = result.errorMessage
        _isVoskLoading.value = false
    }

    // --- DOWNLOAD METHODS ---

    /**
     * Downloads a Vosk model.
     */
    fun downloadVoskModel(lang: String, url: String, name: String) {
        lastDownloadedVoskModelName = name
        lastDownloadType = "vosk"
        
        // Find the model object for UI tracking
        _downloadingItem.value = _voskGroups.value.flatMap { it.models }.find { it.name == name }
        
        appStateManager.setSelectedVoskModelName(name)
        
        val resolvedUrl = if (!url.startsWith("http")) {
            val item = com.voxcommander.app.data.remote.RemoteModelRegistry.getVoskModels().find { it.id == name }
            if (item != null) com.voxcommander.app.data.remote.RemoteModelRegistry.resolveUrl(item, settingsManager) else url
        } else url
        
        val id = modelDownloader.downloadVoskModel(lang, resolvedUrl, name)
        startProgressTracking(id)
    }

    /**
     * Downloads a Whisper model.
     */
    fun downloadWhisperModel(modelId: String, url: String) {
        lastDownloadedWhisperModelId = modelId
        lastDownloadType = "whisper"
        
        // Find the model object for UI tracking
        _downloadingItem.value = com.voxcommander.app.domain.engine.whisper.WhisperModelRegistry.getModelById(modelId)
        
        appStateManager.setSelectedWhisperModelId(modelId)
        
        val resolvedUrl = if (!url.startsWith("http")) {
            val item = com.voxcommander.app.data.remote.RemoteModelRegistry.getWhisperModels().find { it.id == modelId }
            if (item != null) com.voxcommander.app.data.remote.RemoteModelRegistry.resolveUrl(item, settingsManager) else url
        } else url
        
        val id = modelDownloader.downloadWhisperModel(modelId, resolvedUrl)
        startProgressTracking(id)
    }

    /**
     * Downloads a Llama model.
     */
    fun downloadLlamaModel(modelId: String, url: String) {
        lastDownloadedLlamaModelId = modelId
        lastDownloadType = "llama"
        
        // Find model for tracking
        _downloadingItem.value = com.voxcommander.app.data.remote.RemoteModelRegistry.getLlmModels().find { it.id == modelId }?.let {
            LlmModelInfo(
                id = it.id,
                label = it.label,
                url = it.path,
                sizeDescription = "${it.size_mb} MB",
                engineTypeTag = it.engine_type ?: "MEDIAPIPE_GENAI"
            )
        }
        
        appStateManager.setSelectedLlamaModelId(modelId)
        
        val resolvedUrl = if (!url.startsWith("http")) {
            val item = com.voxcommander.app.data.remote.RemoteModelRegistry.getLlmModels().find { it.id == modelId }
            if (item != null) com.voxcommander.app.data.remote.RemoteModelRegistry.resolveUrl(item, settingsManager) else url
        } else url
        
        val id = modelDownloader.downloadLlamaModel(modelId, resolvedUrl)
        startProgressTracking(id)
    }

    /**
     * Cancels the current download.
     */
    fun cancelDownload() {
        currentDownloadId?.let { downloadId ->
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.remove(downloadId)
            progressJob?.cancel()
            _downloadProgress.value = null
            _downloadingItem.value = null
            currentDownloadId = null

            // Delete partial download files
            lastDownloadedVoskModelName?.let { modelName ->
                FileHelper.deletePartialDownload(context, "vosk-model-$modelName.zip")
            }
            lastDownloadedWhisperModelId?.let { modelId ->
                FileHelper.deleteModelFile(context, modelId, "whisper")
            }
            lastDownloadedLlamaModelId?.let { modelId ->
                FileHelper.deleteModelFile(context, modelId, "llama")
            }

            lastDownloadType = null
        }
    }

    // --- CUSTOM MODEL SELECTION ---

    /**
     * Selects a custom Vosk model from a URI.
     */
    fun selectCustomVoskModel(uri: Uri, lang: String) {
        val path = uri.path ?: uri.toString()
        settingsManager.saveCustomVoskModelPath(lang, path)
        settingsManager.setVoiceModelReady(true)
        showSuccessMessage("Custom Vosk model path saved")
    }

    /**
     * Selects a custom Whisper model from a URI.
     */
    fun selectCustomWhisperModel(uri: Uri) {
        FileHelper.copyUriToInternal(context, uri, "custom-whisper-model.bin")?.let { path ->
            settingsManager.saveCustomWhisperModelPath(path)
            settingsManager.setVoiceModelReady(true)
            showSuccessMessage("Custom Whisper model saved")
        }
    }

    // --- MODEL MANAGEMENT ---

    /**
     * Clears all default offline fallbacks for both Voice and Intent.
     */
    fun clearDefaultOfflineFallback() {
        settingsManager.clearDefaultOfflineFallback()
        appStateManager.refreshAll()
    }

    /**
     * Deletes unused models, keeping ONLY the currently selected models (plus the
     * wake-word model, which powers a separate feature).
     *
     * Old default fallbacks are NOT protected: they are deleted along with everything
     * else, and the selected model becomes the new default fallback (being the last
     * one remaining), as long as it is actually present on disk.
     */
    fun deleteUnusedModels() {
        val voiceProcessor = settingsManager.getVoiceProcessor()
        val selectedVosk = settingsManager.getSelectedVoskModelName()
        val selectedWhisper = settingsManager.getSelectedWhisperModelId()
        val selectedLlama = settingsManager.getSelectedLlamaModelId()

        // 1. Protect ONLY the selected models (+ wake-word model).
        val protectedVosk = mutableSetOf<String>()
        selectedVosk?.let { protectedVosk.add(it) }
        settingsManager.getWakeWordModelPath()?.let { protectedVosk.add(it) }

        val protectedWhisper = mutableSetOf<String>()
        protectedWhisper.add(selectedWhisper)

        val protectedLlama = mutableSetOf<String>()
        protectedLlama.add(selectedLlama)

        android.util.Log.d(
            "ModelCleanup",
            "VM deleteUnusedModels -> voiceProcessor=$voiceProcessor aiProcessor=${settingsManager.getAiProcessor()} " +
                "selectedVosk=$selectedVosk selectedWhisper=$selectedWhisper selectedLlama=$selectedLlama " +
                "protectedVosk=$protectedVosk protectedWhisper=$protectedWhisper protectedLlama=$protectedLlama"
        )

        modelDownloader.deleteUnusedModels(protectedVosk, protectedWhisper, protectedLlama)

        // 2. The selected model is now the last one remaining -> make it the default fallback.
        reassignDefaultFallbacks(voiceProcessor, selectedVosk, selectedWhisper, selectedLlama)

        // 3. Refresh flags in settings (after fallback reassignment, so the new default is protected).
        settingsManager.clearUnusedModelFlags(
            selectedVosk ?: "",
            selectedWhisper
        )

        appStateManager.refreshAll()
    }

    /**
     * After a cleanup, points the default voice/intent fallback at the currently selected
     * model (the last one remaining). If the selected model is not present on disk, the
     * stale fallback is cleared instead of leaving a dangling reference.
     */
    private fun reassignDefaultFallbacks(
        voiceProcessor: String,
        selectedVosk: String?,
        selectedWhisper: String,
        selectedLlama: String
    ) {
        // Voice fallback follows the active voice processor.
        when {
            voiceProcessor == Strings.Processors.VOSK -> {
                if (selectedVosk != null && isModelPresent("vosk", selectedVosk)) {
                    settingsManager.saveDefaultVoiceFallback(voiceProcessor, selectedVosk)
                } else {
                    settingsManager.clearDefaultVoiceFallback()
                }
            }
            voiceProcessor.startsWith("WHISPER") -> {
                if (isModelPresent("whisper", selectedWhisper)) {
                    settingsManager.saveDefaultVoiceFallback(voiceProcessor, selectedWhisper)
                } else {
                    settingsManager.clearDefaultVoiceFallback()
                }
            }
        }

        // Intent (Llama) fallback.
        if (isModelPresent("llama", selectedLlama)) {
            settingsManager.saveDefaultIntentFallback(Strings.AiProcessors.LLAMA_LOCAL, selectedLlama)
        } else {
            settingsManager.clearDefaultIntentFallback()
        }
    }

    /**
     * Checks whether a model's file/directory currently exists on disk.
     */
    private fun isModelPresent(type: String, id: String): Boolean {
        val root = context.getExternalFilesDir(null) ?: return false
        val target = when (type) {
            "whisper" -> File(root, "whisper-model-$id.bin")
            "llama" -> File(root, "llama-model-$id.bin")
            "vosk" -> File(root, "vosk-model-$id")
            else -> return false
        }
        return target.exists()
    }

    /**
     * Deletes a specific Llama model.
     */
    fun deleteLlamaModel(modelId: String) {
        settingsManager.setModelDownloaded(modelId, false)
        FileHelper.deleteModelFile(context, modelId, "llama")
        appStateManager.refreshAll()
    }

    // --- PRIVATE HELPER METHODS ---

    private fun startProgressTracking(downloadId: Long) {
        progressJob?.cancel()
        currentDownloadId = downloadId
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            _downloadProgress.value = null
            _downloadingItem.value = null
            return
        }

        progressJob = viewModelScope.launch(Dispatchers.IO) {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)

                        if (bytesTotal > 0) {
                            _downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                        }
                    }

                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                            if (status == DownloadManager.STATUS_FAILED) {
                                _downloadProgress.value = null
                                _downloadingItem.value = null
                            }
                        }
                    }
                }
                cursor.close()
                delay(500)
            }
        }
    }

    private fun handleDownloadSuccess() {
        _downloadProgress.value = null
        _downloadingItem.value = null

        when (lastDownloadType) {
            "whisper" -> {
                val modelId = lastDownloadedWhisperModelId ?: settingsManager.getSelectedWhisperModelId()
                modelDownloader.verifyWhisperModel(modelId) { verified ->
                    if (verified) {
                        appStateManager.onWhisperDownloadComplete(modelId)
                        showSuccessMessage("Whisper Model $modelId ready!")
                    } else {
                        showSuccessMessage("Verification failed for Whisper $modelId")
                    }
                }
            }
            "vosk" -> {
                val modelName = lastDownloadedVoskModelName ?: ""
                modelDownloader.unzipVoskModel(modelName) { unzipped ->
                    if (unzipped) {
                        appStateManager.onVoskDownloadComplete(modelName)
                        showSuccessMessage("Vosk Model $modelName ready!")
                    } else {
                        showSuccessMessage("Extraction failed for Vosk $modelName")
                    }
                }
            }
            "llama" -> {
                val modelId = lastDownloadedLlamaModelId ?: settingsManager.getSelectedLlamaModelId()
                val file = File(context.getExternalFilesDir(null), "llama-model-$modelId.bin")
                if (file.exists()) {
                    settingsManager.setModelDownloaded(modelId, true)
                    appStateManager.refreshAll()
                    showSuccessMessage("Llama Model $modelId ready!")
                } else {
                    showSuccessMessage("File missing after Llama download")
                }
            }
        }
        lastDownloadType = null
    }

    private fun handleDownloadFailure() {
        showSuccessMessage("Download failed or cancelled")
        lastDownloadType = null
    }

    private fun showSuccessMessage(message: String) {
        _selectionSuccessMessage.value = message
        viewModelScope.launch {
            delay(5000)
            _selectionSuccessMessage.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(onDownloadComplete)
        progressJob?.cancel()
    }
}
