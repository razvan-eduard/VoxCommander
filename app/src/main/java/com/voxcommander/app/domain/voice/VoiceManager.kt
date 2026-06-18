package com.voxcommander.app.domain.voice

import android.content.Context
import android.media.*
import android.util.Log
import com.voxcommander.app.data.preferences.SettingsManager
import com.voxcommander.app.domain.engine.SttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperCppSttEngine
import com.voxcommander.app.domain.engine.google.GoogleSttEngine
import com.voxcommander.app.domain.engine.vosk.VoskSttEngine
import com.voxcommander.app.domain.engine.whisper.WhisperSttEngine
import com.voxcommander.app.service.VoiceStateManager
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Singleton VoiceManager to handle real audio capture and STT lifecycle.
 */
object VoiceManager {
    private const val TAG = Strings.Tags.VOICE_MANAGER

    private var whisperCppEngine: WhisperCppSttEngine? = null
    private var whisperApiEngine: WhisperSttEngine? = null
    private var googleSttEngine: GoogleSttEngine? = null
    private var voskSttEngine: VoskSttEngine? = null

    private var context: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null

    @Volatile
    private var isListening = false
    private val _isListeningFlow = MutableStateFlow(false)
    val isListeningFlow = _isListeningFlow.asStateFlow()

    private var settingsManager: SettingsManager? = null

    private val _volumeFlow = MutableStateFlow(0f)
    val volumeFlow: StateFlow<Float> = _volumeFlow.asStateFlow()

    private val _partialTranscriptionFlow = MutableStateFlow("")
    val partialTranscriptionFlow: StateFlow<String> = _partialTranscriptionFlow.asStateFlow()

    private var launchGoogleIntentCallback: ((String) -> Unit)? = null

    class RecordingQuality(val sampleRate: Int, val description: String) {
        companion object {
            val MEDIUM = RecordingQuality(16000, "16kHz Mono")
        }
    }

    private var currentQuality: RecordingQuality = RecordingQuality.MEDIUM

    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val SILENCE_THRESHOLD = 0.02f
    private const val SILENCE_TIMEOUT_MS = 2000L

    fun init(
        context: Context,
        whisperCpp: WhisperCppSttEngine?,
        whisperApi: WhisperSttEngine?,
        google: GoogleSttEngine?,
        vosk: VoskSttEngine?,
        launchGoogleIntent: (String) -> Unit,
        settingsManager: SettingsManager
    ) {
        this.context = context.applicationContext
        this.whisperCppEngine = whisperCpp
        this.whisperApiEngine = whisperApi
        this.googleSttEngine = google
        this.voskSttEngine = vosk
        this.launchGoogleIntentCallback = launchGoogleIntent
        this.settingsManager = settingsManager
        
        Log.d(TAG, "VoiceManager initialized")
    }

    fun handleIntentResult(text: String) {
        // Logic for handling Google Voice result if needed
    }

    fun setOfflineFallbackSettings(timeout: Int, model: String) {
        // Update settings
    }

    fun release() {
        stopListening()
        whisperCppEngine?.release()
        whisperCppEngine = null
        whisperApiEngine = null
        googleSttEngine = null
        voskSttEngine = null
    }

    private fun selectEngine(userPreference: String): SttEngine? {
        Log.d(TAG, "Selecting engine for preference: $userPreference")
        
        val selectedEngine = when (userPreference) {
            Strings.Processors.WHISPER_CPP, 
            Strings.Processors.WHISPER_VULKAN,
            Strings.Processors.WHISPER_NEON -> {
                if (whisperCppEngine != null) {
                    Log.d(TAG, "Selected Whisper.cpp")
                    whisperCppEngine
                } else {
                    googleSttEngine
                }
            }
            Strings.Processors.WHISPER_API -> {
                whisperApiEngine ?: whisperCppEngine ?: googleSttEngine
            }
            Strings.Processors.GOOGLE -> {
                googleSttEngine ?: whisperCppEngine
            }
            Strings.Processors.VOSK -> {
                voskSttEngine ?: whisperCppEngine ?: googleSttEngine
            }
            else -> {
                whisperCppEngine ?: googleSttEngine
            }
        }

        Logger.log("VoiceManager: Selected engine: ${selectedEngine?.javaClass?.simpleName}")
        return selectedEngine
    }

    fun startListening(languageCode: String, processor: String, onResult: (String) -> Unit) {
        if (isListening) return
        
        val engine = selectEngine(processor)
        if (engine == null) {
            Log.e(TAG, "No STT engine available")
            onResult("Error: No STT engine")
            return
        }

        if (processor == Strings.Processors.GOOGLE) {
            launchGoogleIntentCallback?.invoke(languageCode)
            return
        }

        if (context?.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            onResult("Permission Error")
            return
        }

        isListening = true
        _isListeningFlow.value = true
        _partialTranscriptionFlow.value = ""
        VoiceStateManager.startCommandListening()

        recordingJob = scope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(currentQuality.sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT) * 2
                
                @Suppress("MissingPermission")
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    currentQuality.sampleRate,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    withContext(Dispatchers.Main) {
                        onResult("Mic Error")
                        updateListeningState(false)
                    }
                    return@launch
                }

                audioRecord.startRecording()
                val audioData = mutableListOf<Byte>()
                val buffer = ByteArray(bufferSize)
                var lastVoiceTime = System.currentTimeMillis()

                // Loop continues as long as isListening is true
                while (isListening) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val chunk = buffer.copyOfRange(0, read)
                        audioData.addAll(chunk.toList())
                        
                        val rms = calculateRms(buffer, read)
                        _volumeFlow.value = rms
                        
                        if (rms > SILENCE_THRESHOLD) {
                            lastVoiceTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - lastVoiceTime > SILENCE_TIMEOUT_MS) {
                            Log.d(TAG, "Silence detected, stopping recording")
                            isListening = false // Transition to transcribing locally
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord error: $read")
                        break
                    }
                }

                audioRecord.stop()
                audioRecord.release()

                // Finalize STT - This part runs even if isListening was set to false by silence OR by manual stop
                if (audioData.isNotEmpty()) {
                    withContext(Dispatchers.Main) { 
                        _partialTranscriptionFlow.value = "Transcribing..." 
                        _isListeningFlow.value = false // Close the "Talking" overlay
                    }
                    
                    val result = engine.transcribe(audioData.toByteArray())
                    
                    withContext(Dispatchers.Main) { 
                        onResult(result) 
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult("") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during recording", e)
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) { onResult("Error: ${e.message}") }
                }
            } finally {
                withContext(Dispatchers.Main) { 
                    updateListeningState(false) 
                }
            }
        }
    }

    private fun updateListeningState(listening: Boolean) {
        isListening = listening
        _isListeningFlow.value = listening
        if (!listening) {
            VoiceStateManager.setIdle()
            _volumeFlow.value = 0f
        }
    }

    fun stopListening() {
        Log.d(TAG, "Manual stop requested")
        // Setting isListening to false will break the loop gracefully 
        // without canceling the job, allowing transcription to happen.
        isListening = false
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += sample * sample
            }
        }
        return sqrt(sum / (length / 2)).toFloat() / 32768.0f
    }
}
