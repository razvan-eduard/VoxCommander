package com.voxcommander.app.data.remote

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.voxcommander.app.utils.Logger
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logger.log("DownloadCompleteReceiver onReceive called", TAG)
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (id == -1L) {
            Logger.log("Download complete: invalid id=$id", TAG)
            return
        }

        Logger.log("Download complete: $id", TAG)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (uriIndex != -1) {
                val uriString = cursor.getString(uriIndex)
                val filePath = uriString?.removePrefix("file://")

                if (filePath != null) {
                    Logger.log("Downloaded file: $filePath", TAG)

                    // Check if it's a Vosk ZIP file and unzip it
                    val voskExt = RemoteModelRegistry.getExtension("vosk")
                    if (filePath.endsWith(voskExt, ignoreCase = true)) {
                        val modelName = File(filePath).name.removeSuffix(voskExt)
                        val downloader = ModelDownloader(context)
                        downloader.unzipVoskModel(modelName) { success ->
                            Logger.log("Unzip ${if (success) "success" else "failed"} for $modelName", TAG)
                            // Send local broadcast AFTER unzip completes for Vosk
                            val localIntent = Intent(ACTION_DOWNLOAD_COMPLETE_LOCAL).apply {
                                putExtra(EXTRA_DOWNLOAD_ID, id)
                                putExtra(EXTRA_FILE_PATH, filePath)
                                putExtra("directory_name", modelName)
                                putExtra("model_type", "vosk")
                            }
                            Logger.log("Sending local broadcast for Vosk: action=$ACTION_DOWNLOAD_COMPLETE_LOCAL, id=$id, dir=$modelName", TAG)
                            context.sendBroadcast(localIntent)
                        }
                    } else {
                        // Infer type for non-Vosk (Whisper or Llama)
                        val whisperExt = RemoteModelRegistry.getExtension("whisper")
                        val llmExt = RemoteModelRegistry.getExtension("nlu")
                        val type = when {
                            filePath.endsWith(whisperExt, ignoreCase = true) -> "whisper"
                            filePath.endsWith(llmExt, ignoreCase = true) -> "nlu"
                            else -> "unknown"
                        }
                        
                        val localIntent = Intent(ACTION_DOWNLOAD_COMPLETE_LOCAL).apply {
                            putExtra(EXTRA_DOWNLOAD_ID, id)
                            putExtra(EXTRA_FILE_PATH, filePath)
                            putExtra("model_type", type)
                        }
                        Logger.log("Sending local broadcast for $type: action=$ACTION_DOWNLOAD_COMPLETE_LOCAL, id=$id", TAG)
                        context.sendBroadcast(localIntent)
                    }
                }
            }
        }
        cursor.close()
    }

    companion object {
        private const val TAG = "DownloadCompleteReceiver"
        const val ACTION_DOWNLOAD_COMPLETE_LOCAL = "com.voxcommander.app.DOWNLOAD_COMPLETE_LOCAL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_FILE_PATH = "file_path"
    }
}
