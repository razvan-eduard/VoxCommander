package com.voxcommander.app.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
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

    fun downloadNluModel(modelId: String, url: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading NLU Model: $modelId")
            .setDescription("NLU Engine processing model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "nlu-model-$modelId.bin")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

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

    fun deleteUnusedModels(
        protectedVoskModels: Set<String>,
        protectedWhisperModels: Set<String>,
        protectedLlamaModels: Set<String>
    ) {
        val rootDir = context.getExternalFilesDir(null) ?: run {
            Log.w(CLEANUP_TAG, "rootDir (getExternalFilesDir) is null; aborting cleanup")
            return
        }

        Log.d(CLEANUP_TAG, "Cleanup start. rootDir=${rootDir.absolutePath}")
        Log.d(CLEANUP_TAG, "Protected -> vosk=$protectedVoskModels whisper=$protectedWhisperModels llama=$protectedLlamaModels")
        val allNames = rootDir.listFiles()?.map { it.name } ?: emptyList()
        Log.d(CLEANUP_TAG, "Files in rootDir (${allNames.size}): $allNames")

        rootDir.listFiles()?.forEach { file ->
            val name = file.name
            
            // 1. Vosk Protection (Directories)
            if (name.startsWith(VOSK_MODEL_DIR_PREFIX) && file.isDirectory) {
                val modelName = name.removePrefix(VOSK_MODEL_DIR_PREFIX)
                val protectedModel = protectedVoskModels.contains(modelName)
                Log.d(CLEANUP_TAG, "VOSK dir '$name' id='$modelName' protected=$protectedModel")
                if (!protectedModel) {
                    val deleted = file.deleteRecursively()
                    Log.d(CLEANUP_TAG, "VOSK delete '$name' result=$deleted")
                }
            }
            
            // 2. Whisper Protection (Files)
            if (name.startsWith(WHISPER_MODEL_FILENAME_PREFIX) && name.endsWith(WHISPER_MODEL_EXTENSION)) {
                val modelId = name.removePrefix(WHISPER_MODEL_FILENAME_PREFIX).removeSuffix(WHISPER_MODEL_EXTENSION)
                val protectedModel = protectedWhisperModels.contains(modelId)
                Log.d(CLEANUP_TAG, "WHISPER file '$name' id='$modelId' protected=$protectedModel")
                if (!protectedModel) {
                    val deleted = file.delete()
                    Log.d(CLEANUP_TAG, "WHISPER delete '$name' result=$deleted exists=${file.exists()}")
                }
            }
            
            // 3. Llama Protection (Files)
            if (name.startsWith(NLU_MODEL_FILENAME_PREFIX) && name.endsWith(NLU_MODEL_EXTENSION)) {
                val modelId = name.removePrefix(NLU_MODEL_FILENAME_PREFIX).removeSuffix(NLU_MODEL_EXTENSION)
                val protectedModel = protectedLlamaModels.contains(modelId)
                Log.d(CLEANUP_TAG, "NLU file '$name' id='$modelId' protected=$protectedModel size=${file.length()}")
                if (!protectedModel) {
                    val deleted = file.delete()
                    Log.d(CLEANUP_TAG, "NLU delete '$name' result=$deleted exists=${file.exists()}")
                }
            }
        }
        Log.d(CLEANUP_TAG, "Cleanup done.")
    }

    companion object {
        private const val CLEANUP_TAG = "ModelCleanup"
        private const val WHISPER_MODEL_FILENAME_PREFIX = "whisper-model-"
        private const val WHISPER_MODEL_EXTENSION = ".bin"
        private const val VOSK_MODEL_ZIP_PREFIX = "vosk-model-"
        private const val VOSK_MODEL_DIR_PREFIX = "vosk-model-"
        private const val ZIP_EXTENSION = ".zip"
        private const val NLU_MODEL_FILENAME_PREFIX = "nlu-model-"
        private const val NLU_MODEL_EXTENSION = ".bin"
    }
}
