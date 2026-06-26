package com.voxcommander.app.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Agnostic Model Downloader & File Manager.
 * Uses RemoteModelRegistry as the SSOT for extensions and keys.
 */
class ModelDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Resolves the local File object for a given model.
     * Handles both directory-based (Vosk) and file-based (Whisper/NLU) models.
     */
    fun resolveLocalFile(modelId: String, engineKey: String): File? {
        val rootDir = context.getExternalFilesDir(null) ?: return null
        val extension = RemoteModelRegistry.getExtension(engineKey)
        
        return if (extension.isBlank()) {
            File(rootDir, modelId) // Directory (e.g. wake_vosk)
        } else {
            File(rootDir, "$modelId$extension") // File (e.g. stt_whisper, nlu_llm)
        }
    }

    /**
     * Generic download method.
     */
    fun downloadModel(modelId: String, url: String, engineKey: String): Long {
        val extension = RemoteModelRegistry.getExtension(engineKey)
        val fileName = "$modelId$extension"

        // Pre-flight check
        val localFile = resolveLocalFile(modelId, engineKey)
        if (localFile?.exists() == true) {
            Logger.log("Model already exists: $modelId ($engineKey), skipping download", TAG)
            return -1L
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Model ($modelId)")
            .setDescription("Preparing offline engine: $engineKey")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        // Vosk ZIPs go to temporary Downloads dir for unzip, others directly to root
        val destination = if (engineKey == "wake_vosk") Environment.DIRECTORY_DOWNLOADS else null
        request.setDestinationInExternalFilesDir(context, destination, fileName)

        // NLU specific flags
        if (engineKey == "nlu_llm") {
            request.setAllowedOverMetered(true)
            request.setAllowedOverRoaming(true)
        }

        val downloadId = downloadManager.enqueue(request)
        Logger.log("Download started: modelId=$modelId, engine=$engineKey, downloadId=$downloadId", TAG)
        return downloadId
    }

    /**
     * Generic delete method.
     */
    fun deleteModelFile(modelId: String, engineKey: String) {
        val file = resolveLocalFile(modelId, engineKey)
        if (file?.exists() == true) {
            Logger.log("Deleting model: $modelId ($engineKey) at ${file.absolutePath}", TAG)
            file.deleteRecursively()
        } else {
            Logger.log("Model file not found for deletion: $modelId ($engineKey)", TAG)
        }
    }

    /**
     * Unzips Vosk models from temporary downloads to app root.
     */
    fun unzipVoskModel(modelId: String, onComplete: (Boolean) -> Unit) {
        val extension = RemoteModelRegistry.getExtension("wake_vosk")
        val zipFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelId$extension")
        val targetDir = resolveLocalFile(modelId, "wake_vosk") ?: return onComplete(false)

        if (!zipFile.exists()) {
            Logger.log("Unzip failed: ZIP file not found: ${zipFile.absolutePath}", TAG)
            onComplete(false)
            return
        }

        try {
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            Logger.log("Unzip successful: $modelId", TAG)
            onComplete(true)
        } catch (e: Exception) {
            Logger.log("Unzip failed for $modelId: ${e.message}", TAG)
            onComplete(false)
        }
    }

    /**
     * Agnostic cleanup of unused models.
     * Iterates through ALL engine types defined in JSON and protects only the active ones.
     * Everything else is purged.
     */
    fun deleteUnusedModels(
        settingsManager: SettingsManager,
        activeVoiceModelId: String?,
        activeIntentModelId: String?,
        appStateManager: AppStateManager? = null
    ) {
        val rootDir = context.getExternalFilesDir(null) ?: return
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        
        // 1. Build protected set based on actual files resolved from active IDs across all known engines
        val protectedNames = mutableSetOf<String>()
        val engineKeys = RemoteModelRegistry.getEngineTypes()
        
        // Essential system items
        protectedNames.addAll(listOf("Download", "transcriptions", "logs"))

        engineKeys.forEach { key ->
            activeVoiceModelId?.let { id ->
                resolveLocalFile(id, key)?.let { protectedNames.add(it.name) }
            }
            activeIntentModelId?.let { id ->
                resolveLocalFile(id, key)?.let { protectedNames.add(it.name) }
            }
        }

        Logger.log("Cleanup started. Protected items: $protectedNames", CLEANUP_TAG)

        // 2. Wipe EVERYTHING else in root
        rootDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name in protectedNames) {
                Logger.log("Keeping protected item: $name", CLEANUP_TAG)
                return@forEach
            }

            Logger.log("DELETING unused item: ${file.absolutePath}", CLEANUP_TAG)
            file.deleteRecursively()
            
            // Sync settings: find which engine this file belonged to to strip extension and get ID
            var modelId = name
            engineKeys.forEach { key ->
                val ext = RemoteModelRegistry.getExtension(key)
                if (ext.isNotBlank() && name.endsWith(ext)) {
                    modelId = name.removeSuffix(ext)
                }
            }
            
            settingsManager.setModelDownloaded(modelId, false)
            if (settingsManager.getDefaultVoiceFallbackModel() == modelId) settingsManager.clearDefaultVoiceFallback()
            if (settingsManager.getDefaultIntentFallbackModel() == modelId) settingsManager.clearDefaultIntentFallback()
        }

        // 3. Clean temporary Downloads
        downloadsDir?.listFiles()?.forEach { file ->
            val isKnownZip = engineKeys.any { key ->
                val ext = RemoteModelRegistry.getExtension(key)
                ext.isNotBlank() && file.name.endsWith(ext)
            }
            if (isKnownZip) {
                file.delete()
                Logger.log("Deleted ZIP from downloads: ${file.name}", CLEANUP_TAG)
            }
        }

        Logger.log("Cleanup complete.", CLEANUP_TAG)
        appStateManager?.refreshAll()
    }

    companion object {
        private const val TAG = "ModelDownloader"
        private const val CLEANUP_TAG = "ModelCleanup"
    }
}
