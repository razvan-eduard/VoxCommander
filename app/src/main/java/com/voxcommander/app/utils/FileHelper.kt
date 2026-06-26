package com.voxcommander.app.utils

import android.content.Context
import android.net.Uri
import com.voxcommander.app.data.remote.RemoteModelRegistry
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
     * @param type Model type (whisper, nlu, vosk)
     */
    fun deleteModelFile(context: Context, modelId: String, type: String) {
        val fileName = when (type) {
            "whisper" -> "$modelId${RemoteModelRegistry.getExtension("whisper")}"
            "nlu" -> "$modelId${RemoteModelRegistry.getExtension("nlu")}"
            "vosk" -> modelId // Vosk models are directories, no extension
            else -> return
        }
        val file = File(context.getExternalFilesDir(null), fileName)
        Logger.log("Deleting model file: type=$type, modelId=$modelId, fileName=$fileName, path=${file.absolutePath}, exists=${file.exists()}", Strings.Tags.FILE_HELPER)
        if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
                Logger.log("Deleted Vosk directory: $fileName", Strings.Tags.FILE_HELPER)
            } else {
                file.delete()
                Logger.log("Deleted model file: $fileName", Strings.Tags.FILE_HELPER)
            }
        } else {
            Logger.log("Model file does not exist: $fileName", Strings.Tags.FILE_HELPER)
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
