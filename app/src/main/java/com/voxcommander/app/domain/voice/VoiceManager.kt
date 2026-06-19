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
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt

/**
 * Singleton VoiceManager to handle real audio capture and STT lifecycle.
 * Serializes access to native resources via AppStateManager's Mutex.
 * Reactively manages engine instances based on AppStateManager settings.
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
    private var stateObservationJob: Job? = null

    @Volatile
    private var isListening = false
    private val _isListeningFlow = MutableStateFlow(false)
    val isListeningFlow = _isListeningFlow.asStateFlow()

    private var settingsManager: SettingsManager? = null
    private var appStateManager: AppStateManager? = null

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

    private var googleResultCallback: ((String) -> Unit)? = null

    fun init(
        context: Context,
        whisperCpp: WhisperCppSttEngine?,
        whisperApi: WhisperSttEngine?,
        google: GoogleSttEngine?,
        vosk: VoskSttEngine?,
        launchGoogleIntent: (String) -> Unit,
        settingsManager: SettingsManager,
        appStateManager: AppStateManager
    ) {
        this.context = context.applicationContext
        this.whisperCppEngine = whisperCpp
        this.whisperApiEngine = whisperApi
        this.googleSttEngine = google
        this.voskSttEngine = vosk
        this.launchGoogleIntentCallback = launchGoogleIntent
        this.settingsManager = settingsManager
        this.appStateManager = appStateManager
        
        Log.d(TAG, "VoiceManager initialized")
        
        // Start reactive observation of processor changes
        startProcessorObservation()
    }

    /**
     * Reactively observes the AppStateManager. When the user changes the processor,
     * this manager automatically cleans up and re-initializes engines.
     */
    private fun startProcessorObservation() {
        stateObservationJob?.cancel()
        val hub = appStateManager ?: return
        
        stateObservationJob = scope.launch {
            hub.voiceProcessor.collectLatest { processor ->
                Log.d(TAG, "Processor change detected: $processor. Updating engines...")
                reinitializeEngines(processor)
            }
        }
    }

    private suspend fun reinitializeEngines(processor: String) = withContext(Dispatchers.Main) {
        val hub = appStateManager ?: return@withContext
        val settings = settingsManager ?: return@withContext
        val ctx = context ?: return@withContext

        // 1. Enter CLEANING state
        hub.setVoiceState(VoiceState.CLEANING)
        
        // 2. RELEASE all current hardware and resources
        release()
        
        // 3. RE-INITIALIZE based on new selection
        val apiKey = settings.getApiKey()
        val voiceLang = settings.getVoiceLanguage()
        
        whisperCppEngine = WhisperCppSttEngine(
            ctx, 
            settings, 
            forceGpu = (processor == Strings.Processors.WHISPER_VULKAN)
        )
        
        whisperApiEngine = if (!apiKey.isNullOrBlank()) WhisperSttEngine(apiKey) else null
        googleSttEngine = GoogleSttEngine(ctx)
        voskSttEngine = VoskSttEngine(ctx, settings, voiceLang)
        
        // 4. Return to IDLE state
        hub.setVoiceState(VoiceState.IDLE)
        Log.d(TAG, "Engines updated successfully for $processor")
    }

    fun handleIntentResult(text: String) {
        _isListeningFlow.value = false // Clear listening state for UI
        googleResultCallback?.invoke(text)
        googleResultCallback = null
        appStateManager?.setVoiceState(VoiceState.IDLE)
    }

    fun setOfflineFallbackSettings(timeout: Int, model: String) {
        // Update settings
    }

    fun release() {
        stopListening()
        
        // Use the new common release interface for all engines
        whisperCppEngine?.release()
        whisperCppEngine = null
        
        whisperApiEngine?.release()
        whisperApiEngine = null
        
        googleSttEngine?.release()
        googleSttEngine = null
        
        voskSttEngine?.release()
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
        
        if (processor == Strings.Processors.GOOGLE) {
            googleResultCallback = onResult
            _isListeningFlow.value = true // Show listening state for UI
            appStateManager?.setVoiceState(VoiceState.LISTENING_COMMAND)
            launchGoogleIntentCallback?.invoke(languageCode)
            return
        }

        val engine = selectEngine(processor)
        if (engine == null) {
            Log.e(TAG, "No STT engine available")
            onResult("Error: No STT engine")
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
        appStateManager?.setVoiceState(VoiceState.LISTENING_COMMAND)

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

                // Finalize STT
                if (audioData.isNotEmpty()) {
                    withContext(Dispatchers.Main) { 
                        _partialTranscriptionFlow.value = "Transcribing..." 
                        _isListeningFlow.value = false // Close the "Talking" overlay
                    }
                    
                    // Secure access to native engine via AppStateManager's mutex
                    val result = appStateManager?.executeSecureVoiceAction {
                        engine.transcribe(audioData.toByteArray())
                    } ?: "Error: Sync failed"
                    
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
            appStateManager?.setVoiceState(VoiceState.IDLE)
            _volumeFlow.value = 0f
        }
    }

    fun stopListening() {
        Log.d(TAG, "Manual stop requested")
        // Setting isListening to false will break the loop gracefully 
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
