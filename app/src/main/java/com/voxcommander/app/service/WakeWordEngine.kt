package com.voxcommander.app.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class WakeWordEngine(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val appStateManager: AppStateManager,
    private val onWakeWordDetected: () -> Unit
) {
    private val TAG = "WakeWordEngine"
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2

    suspend fun initialize(modelPath: String, wakeWord: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Init model: $modelPath")
            
            // Secure cleanup of previous instances
            appStateManager.executeSecureVoiceAction {
                recognizer?.close()
                model?.close()
                recognizer = null
                model = null
            }
            
            val dir = File(modelPath)
            val actualPath = if (File(dir, "am").exists()) dir.absolutePath 
                             else dir.listFiles()?.find { File(it, "am").exists() }?.absolutePath ?: modelPath

            val newModel = Model(actualPath)
            
            // Build vocabulary for static grammar
            val wakeWordClean = wakeWord.lowercase().trim()
            val individualWords = wakeWordClean.split(Regex("\\s+"))
            val vocab = mutableSetOf<String>()
            vocab.addAll(individualWords)
            vocab.addAll(listOf("hey", "vox", "wake", "up", "commander", "[unknown]"))
            val grammarJson = vocab.joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"$it\"" }
            
            appStateManager.executeSecureVoiceAction {
                model = newModel
                recognizer = Recognizer(newModel, sampleRate.toFloat(), grammarJson)
                recognizer?.setWords(true)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            return@withContext false
        }
    }

    fun startListening(): Boolean {
        if (isListening) return true
        try {
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
            
            audioRecord?.release()
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return false

            isListening = true
            appStateManager.setWakeWordServiceListening(true)
            appStateManager.setVoiceState(VoiceState.LISTENING_WAKEWORD)
            audioRecord?.startRecording()
            
            CoroutineScope(Dispatchers.IO).launch { listenLoop() }
            return true
        } catch (e: Exception) {
            isListening = false
            return false
        }
    }

    private suspend fun listenLoop() {
        val buffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)

        while (isListening) {
            val currentAudioRecord = audioRecord ?: break
            if (currentAudioRecord.state != AudioRecord.STATE_INITIALIZED) break

            val read = try {
                currentAudioRecord.read(buffer, 0, buffer.size)
            } catch (e: Exception) {
                -1
            }

            if (read > 0 && isListening) {
                // Little Endian conversion
                for (i in 0 until read / 2) {
                    shortBuffer[i] = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
                }

                // CRITICAL: Synchronize with Mutex to prevent processing while closing
                appStateManager.executeSecureVoiceAction {
                    if (isListening && recognizer != null) {
                        try {
                            if (recognizer?.acceptWaveForm(shortBuffer, read / 2) == true) {
                                handleResult(recognizer?.result)
                            } else {
                                handlePartial(recognizer?.partialResult)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Vosk recognizer error", e)
                        }
                    }
                }
            } else if (read < 0 || !isListening) break
        }
        Log.d(TAG, "WakeWord loop exited cleanly")
    }

    private fun handleResult(json: String?) {
        val text = json?.let { JSONObject(it).optString("text", "") } ?: ""
        if (text.isNotBlank()) {
            Logger.log("WW Full: $text")
            if (isValidWakeWordMatch(text)) onWakeWordDetected()
        }
    }

    private fun handlePartial(json: String?) {
        val partial = json?.let { JSONObject(it).optString("partial", "") } ?: ""
        if (partial.isNotBlank()) {
            Logger.log("WW Partial: $partial")
            if (isValidWakeWordMatch(partial)) {
                onWakeWordDetected()
                recognizer?.reset()
            }
        }
    }

    private fun isValidWakeWordMatch(heardText: String): Boolean {
        val target = settingsManager.getWakeWord().lowercase().trim()
        val cleanHeard = heardText.lowercase()
            .replace("[unknown]", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        if (cleanHeard.isBlank()) return false
        return cleanHeard == target
    }

    fun stopListening() {
        if (!isListening) return
        Log.d(TAG, "Stopping WakeWordEngine listening")
        
        // 1. Signal loop to stop first
        isListening = false
        appStateManager.setWakeWordServiceListening(false)
        
        // 2. Stop audio capture
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
        
        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    fun release() {
        stopListening()
        
        // 3. SECURE NATIVE CLEANUP: Wait for mutex to ensure loop has exited
        CoroutineScope(Dispatchers.IO).launch {
            appStateManager.executeSecureVoiceAction {
                Log.d(TAG, "Releasing native resources...")
                recognizer?.close()
                model?.close()
                recognizer = null
                model = null
            }
        }
    }
}
