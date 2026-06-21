package com.voxcommander.app.domain.engine.vosk

import android.content.Context
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.engine.SttEngine
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * A real implementation of the local offline STT engine using Vosk.
 */
class VoskSttEngine(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val langCode: String = DEFAULT_LANG
) : SttEngine {
    private var model: Model? = null
    private var activeRecognizer: Recognizer? = null

    private suspend fun ensureModelLoaded() = withContext(Dispatchers.IO) {
        if (model == null) {
            try {
                val customPath: String? = settingsManager.getCustomVoskModelPath(langCode)
                if (!customPath.isNullOrBlank() && File(customPath).exists()) {
                    val actualPath = findModelDir(File(customPath))?.absolutePath ?: customPath
                    model = Model(actualPath)
                    return@withContext
                }

                val rootDir = context.getExternalFilesDir(null)
                
                // TIER 1: Specific selected model
                val selectedModelName = settingsManager.getSelectedVoskModelName()
                val specificModelDir = if (!selectedModelName.isNullOrBlank()) {
                    File(rootDir, selectedModelName)
                } else null

                val modelDir = if (specificModelDir?.exists() == true) {
                    specificModelDir
                } else {
                    // TIER 2: Fallback to any model for this language
                    rootDir?.listFiles()?.find { 
                        it.isDirectory && it.name.startsWith(MODEL_DIR_PREFIX) && it.name.contains(langCode, ignoreCase = true) 
                    }
                }
                
                if (modelDir != null && modelDir.exists()) {
                    val actualPath = findModelDir(modelDir)?.absolutePath ?: modelDir.absolutePath
                    model = Model(actualPath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun findModelDir(dir: File): File? {
        if (File(dir, AM_FILE).exists() || File(dir, CONF_FILE).exists()) return dir
        return dir.listFiles()?.firstOrNull { it.isDirectory }?.let { findModelDir(it) }
    }

    override suspend fun processChunk(audio: ByteArray): String? = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val currentModel = model ?: return@withContext null
        
        if (activeRecognizer == null) {
            activeRecognizer = Recognizer(currentModel, SAMPLE_RATE)
        }
        
        activeRecognizer?.let {
            it.acceptWaveForm(audio, audio.size)
            val partialJson = it.partialResult
            return@withContext try {
                JSONObject(partialJson).optString(JSON_KEY_PARTIAL, "")
            } catch (e: Exception) {
                ""
            }
        }
        return@withContext null
    }

    override suspend fun transcribe(audio: ByteArray): String = withContext(Dispatchers.IO) {
        ensureModelLoaded()
        val currentModel = model ?: return@withContext "Error: Vosk Model ($langCode) not found."
        
        val result = try {
            // Vosk expects 16-bit PCM Mono. Convert to ShortArray for cleaner processing if needed,
            // but the recognizer accepts ByteArray and handles it if the format matches SAMPLE_RATE.
            // Ensure we are passing correct byte length.
            val recognizer = activeRecognizer ?: Recognizer(currentModel, SAMPLE_RATE)
            
            // Critical: If we just created the recognizer, we need to feed it the whole buffer
            if (activeRecognizer == null) {
                recognizer.acceptWaveForm(audio, audio.size)
            }

            val resultJson = recognizer.finalResult
            JSONObject(resultJson).optString(JSON_KEY_TEXT, "")
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: Transcription failed - ${e.message}"
        } finally {
            activeRecognizer?.close()
            activeRecognizer = null
        }
        return@withContext result
    }

    override fun releaseHardware() {
        try {
            activeRecognizer?.close()
            model?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun releaseResources() {
        activeRecognizer = null
        model = null
    }

    companion object {
        private const val DEFAULT_LANG = Strings.Vosk.DEFAULT_LANG
        private const val MODEL_DIR_PREFIX = "vosk-model-"
        private const val AM_FILE = Strings.Vosk.AM_FILE
        private const val CONF_FILE = Strings.Vosk.CONF_FILE
        private const val JSON_KEY_TEXT = Strings.Vosk.JSON_KEY_TEXT
        private const val JSON_KEY_PARTIAL = Strings.Vosk.JSON_KEY_PARTIAL
        private const val SAMPLE_RATE = 16000.0f
    }
}
