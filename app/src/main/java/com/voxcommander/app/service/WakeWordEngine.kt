package com.voxcommander.app.service

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    /**
     * Initialize the engine with a model path
     */
    suspend fun initialize(modelPath: String, wakeWord: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing WakeWordEngine with model: $modelPath, wakeWord: $wakeWord")
            
            val modelDir = File(modelPath)
            if (!modelDir.exists()) {
                Log.e(TAG, "Model directory does not exist: $modelPath")
                return@withContext false
            }

            // Find the actual model directory (sometimes it's inside the main folder)
            val actualModelDir = findModelDir(modelDir) ?: modelDir
            
            model = Model(actualModelDir.absolutePath)
            recognizer = Recognizer(model, sampleRate.toFloat())
            recognizer?.setWords(true) // We need words to detect wake word
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WakeWordEngine", e)
            return@withContext false
        }
    }

    /**
     * Helper to find the actual Vosk model directory (the one containing 'am' and 'conf')
     */
    private fun findModelDir(dir: File, depth: Int = 0): File? {
        if (depth > 2) return null
        
        if (File(dir, "am").exists() && File(dir, "conf").exists()) {
            return dir
        }
        
        dir.listFiles()?.forEach {
            if (it.isDirectory) {
                val found = findModelDir(it, depth + 1)
                if (found != null) return found
            }
        }
        
        return null
    }

    /**
     * Start listening for the wake word
     */
    fun startListening(): Boolean {
        if (isListening) return true

        try {
            Log.d(TAG, "Starting WakeWordEngine listening")
            
            // Check for record permission
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "RECORD_AUDIO permission not granted")
                return false
            }

            // STABILITY FIX: Release any existing AudioRecord before creating new one
            audioRecord?.release()
            audioRecord = null

            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            // Important: Change state FIRST to ensure loop is valid
            isListening = true
            appStateManager.setVoiceState(VoiceState.LISTENING_WAKEWORD)

            audioRecord?.startRecording()
            
            // Start the listening loop in a background thread
            CoroutineScope(Dispatchers.IO).launch {
                listenLoop()
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WakeWordEngine", e)
            isListening = false
            audioRecord?.release()
            audioRecord = null
            return false
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        Log.d(TAG, "Stopping WakeWordEngine listening")
        isListening = false
        
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord?.release()
            audioRecord = null
        }
        
        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    /**
     * Main listening loop
     */
    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)

        while (isListening && appStateManager.voiceState.value == VoiceState.LISTENING_WAKEWORD) {
            try {
                val currentAudioRecord = audioRecord
                // CRITICAL STABILITY CHECK: Ensure AudioRecord is still valid and recording
                if (currentAudioRecord == null || 
                    currentAudioRecord.state != AudioRecord.STATE_INITIALIZED ||
                    currentAudioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    
                    if (isListening) {
                        Log.w(TAG, "AudioRecord became invalid during loop, attempting restart in 1s")
                        delay(1000)
                        continue 
                    } else {
                        break
                    }
                }

                val bytesRead = try {
                    currentAudioRecord.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during AudioRecord.read", e)
                    -1
                }

                if (bytesRead > 0) {
                    // Convert ByteArray to ShortArray for Vosk
                    for (i in 0 until bytesRead / 2) {
                        val sample = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
                        shortBuffer[i] = sample
                    }

                    val currentRecognizer = recognizer
                    if (currentRecognizer != null) {
                        // Secure execution via AppStateManager's mutex to prevent SIGSEGV
                        appStateManager.executeSecureVoiceAction {
                            try {
                                if (currentRecognizer.acceptWaveForm(shortBuffer, bytesRead / 2)) {
                                    processResult(currentRecognizer.result)
                                } else {
                                    processPartialResult(currentRecognizer.partialResult)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Vosk recognizer error", e)
                            }
                        }
                    }
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "AudioRecord read error: $bytesRead")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in WakeWord listen loop", e)
                if (isListening) delay(1000) else break
            }
        }
        
        Log.d(TAG, "WakeWord listen loop finished")
    }

    private fun processResult(jsonResult: String) {
        try {
            val json = JSONObject(jsonResult)
            val text = json.optString("text", "")
            val wakeWord = settingsManager.getWakeWord().lowercase()
            
            Log.d(TAG, "Full Result: $text (target: $wakeWord)")
            
            if (text.contains(wakeWord)) {
                Log.i(TAG, "Wake word detected in full result!")
                onWakeWordDetected()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing result", e)
        }
    }

    private fun processPartialResult(jsonResult: String) {
        try {
            val json = JSONObject(jsonResult)
            val partial = json.optString("partial", "")
            val wakeWord = settingsManager.getWakeWord().lowercase()
            
            if (partial.isNotEmpty()) {
                Log.v(TAG, "Partial Result: $partial")
            }

            if (partial.contains(wakeWord)) {
                Log.i(TAG, "Wake word detected in partial result!")
                onWakeWordDetected()
                recognizer?.reset()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing partial result", e)
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stopListening()
        
        try {
            recognizer?.close()
            model?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing engine", e)
        } finally {
            recognizer = null
            model = null
        }
    }
}
