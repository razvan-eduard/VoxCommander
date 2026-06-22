package com.voxcommander.app.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Helper class for file operations.
 * Handles copying URIs to internal storage and deleting model files.
 */
object FileHelper {

    /**
     * Copies a URI to internal storage.
     * @param context Application context
     * @param uri Source URI
     * @param targetName Target filename
     * @return Absolute path of the copied file, or null if failed
     */
    fun copyUriToInternal(context: Context, uri: Uri, targetName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.getExternalFilesDir(null), targetName)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a model file from external storage.
     * @param context Application context
     * @param modelId Model identifier
     * @param type Model type (whisper, llama, vosk)
     */
    fun deleteModelFile(context: Context, modelId: String, type: String) {
        val fileName = when (type) {
            "whisper" -> "whisper-model-$modelId.bin"
            "llama" -> "llama-model-$modelId.bin"
            "vosk" -> "vosk-model-$modelId"
            else -> return
        }
        val file = File(context.getExternalFilesDir(null), fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Deletes a partial download file.
     * @param context Application context
     * @param fileName Filename to delete
     */
    fun deletePartialDownload(context: Context, fileName: String) {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }
}
