package com.voxcommander.app.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ModelDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadWhisperModel(modelId: String, url: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Whisper Model ($modelId)")
            .setDescription("Downloading offline voice model: $modelId")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, WHISPER_MODEL_FILENAME_PREFIX + modelId + WHISPER_MODEL_EXTENSION)
        return downloadManager.enqueue(request)
    }

    fun downloadVoskModel(langCode: String, url: String, modelName: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Vosk Model ($langCode)")
            .setDescription("Downloading $modelName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, VOSK_MODEL_ZIP_PREFIX + modelName + ZIP_EXTENSION)

        return downloadManager.enqueue(request)
    }

    fun unzipVoskModel(modelName: String, onComplete: (Boolean) -> Unit) {
        val zipFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), VOSK_MODEL_ZIP_PREFIX + modelName + ZIP_EXTENSION)
        val targetDir = File(context.getExternalFilesDir(null), VOSK_MODEL_DIR_PREFIX + modelName)

        if (!zipFile.exists()) {
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
            onComplete(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }


    fun verifyWhisperModel(modelId: String, onComplete: (Boolean) -> Unit) {
        val modelFile = File(context.getExternalFilesDir(null), WHISPER_MODEL_FILENAME_PREFIX + modelId + WHISPER_MODEL_EXTENSION)
        onComplete(modelFile.exists())
    }

    fun deleteUnusedModels(activeVoskLang: String, activeWhisperId: String) {
        val rootDir = context.getExternalFilesDir(null) ?: return
        
        val activeVoskDirName = VOSK_MODEL_DIR_PREFIX + activeVoskLang
        val activeWhisperFileName = WHISPER_MODEL_FILENAME_PREFIX + activeWhisperId + WHISPER_MODEL_EXTENSION
        
        rootDir.listFiles()?.forEach { file ->
            val name = file.name
            // Delete Vosk models that are not active
            if (name.startsWith(VOSK_MODEL_DIR_PREFIX) && name != activeVoskDirName) {
                file.deleteRecursively()
            }
            // Delete Whisper models that are not active
            if (name.startsWith(WHISPER_MODEL_FILENAME_PREFIX) && 
                name.endsWith(WHISPER_MODEL_EXTENSION) && 
                name != activeWhisperFileName) {
                file.delete()
            }
        }
    }

    companion object {
        private const val WHISPER_MODEL_FILENAME_PREFIX = "whisper-model-"
        private const val WHISPER_MODEL_EXTENSION = ".bin"
        private const val VOSK_MODEL_ZIP_PREFIX = "vosk-model-"
        private const val VOSK_MODEL_DIR_PREFIX = "vosk-model-"
        private const val ZIP_EXTENSION = ".zip"
    }
}
