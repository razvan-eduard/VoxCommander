package com.voxcommander.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.voxcommander.app.data.preferences.SettingsRepository
import com.voxcommander.app.state.AppStateManager
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

class WakeWordEngine(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val appStateManager: AppStateManager,
    private val onWakeWordDetected: () -> Unit
) {
    private val TAG = Strings.Tags.WAKE_WORD_ENGINE
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false

   private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2

    suspend fun initialize(modelPath: String, wakeWord: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.log("Init model: $modelPath", TAG)

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

            val wakeWordClean = wakeWord.lowercase().trim()
            val individualWords = wakeWordClean.split(Regex("\\s+"))
            val vocab = mutableSetOf<String>().apply {
                addAll(individualWords)
                addAll(listOf("hey", "vox", "wake", "up", "commander", "[unknown]"))
            }
            val grammarJson = vocab.joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"$it\"" }

            appStateManager.executeSecureVoiceAction {
                model = newModel
                recognizer = Recognizer(newModel, sampleRate.toFloat(), grammarJson)
                recognizer?.setWords(true)
            }
            return@withContext true
        } catch (e: Exception) {
            Logger.log("Init failed: ${e.message}", TAG)
            return@withContext false
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Logger.log("Audio focus lost. Pausing listening.", TAG)
                        stopListening()
                    }
                }.build()

            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun startListening(): Boolean {
        if (isListening) return true
        try {
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }

            if (!requestAudioFocus()) {
                Logger.log("Could not gain audio focus. Cannot start listening.", TAG)
                return false
            }

            audioRecord?.release()
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                abandonAudioFocus()
                return false
            }

            isListening = true
            appStateManager.setWakeWordServiceListening(true)
            appStateManager.setVoiceState(VoiceState.LISTENING_WAKEWORD)
            audioRecord?.startRecording()

            CoroutineScope(Dispatchers.IO).launch { listenLoop() }
            return true
        } catch (e: Exception) {
            Logger.log("Exception starting AudioRecord: ${e.message}", TAG)
            isListening = false
            abandonAudioFocus()
            return false
        }
    }

    private suspend fun listenLoop() {
        val buffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)
        var consecutiveErrors = 0 // Anti CPU Burn logic

        while (isListening) {
            val currentAudioRecord = audioRecord ?: break
            if (currentAudioRecord.state != AudioRecord.STATE_INITIALIZED) break

            val read = try {
                currentAudioRecord.read(buffer, 0, buffer.size)
            } catch (e: Exception) {
                -1
            }

            if (read > 0 && isListening) {
                consecutiveErrors = 0 // Reset errors on successful read

                // Little Endian conversion
                for (i in 0 until read / 2) {
                    shortBuffer[i] = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
                }

                appStateManager.executeSecureVoiceAction {
                    if (isListening && recognizer != null) {
                        try {
                            if (recognizer?.acceptWaveForm(shortBuffer, read / 2) == true) {
                                handleResult(recognizer?.result)
                            } else {
                                handlePartial(recognizer?.partialResult)
                            }
                        } catch (e: Exception) {
                            Logger.log("Vosk recognizer error: ${e.message}", TAG)
                        }
                    }
                }
            } else if (read < 0) {
                consecutiveErrors++
                Logger.log("Audio read error count: $consecutiveErrors", TAG)
                if (consecutiveErrors > 5) {
                    Logger.log("Too many audio read errors. Aborting loop to prevent CPU burn.", TAG)
                    stopListening()
                    break
                }
                delay(50) // Pauză pentru a nu prăji CPU-ul
            } else if (!isListening) {
                break
            }
        }
        Logger.log("WakeWord loop exited cleanly", TAG)
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
            if (isValidWakeWordMatch(partial)) {
                Logger.log("WW Partial Match: $partial")
                onWakeWordDetected()
                recognizer?.reset() // Reset after partial match to prevent duplicate triggers
            }
        }
    }

    private fun isValidWakeWordMatch(heardText: String): Boolean {
        val target = settingsRepo.getSettingsSnapshot().wakeWord.lowercase().trim()
        val cleanHeard = heardText.lowercase()
            .replace("[unknown]", "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanHeard.isBlank()) return false
        return cleanHeard == target
    }

    fun stopListening() {
        if (!isListening) return
        Logger.log("Pausing WakeWordEngine listening", TAG)

        isListening = false

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Logger.log("Error stopping AudioRecord: ${e.message}", TAG)
        } finally {
            audioRecord = null
            abandonAudioFocus()
        }
    }

    fun stopService() {
        stopListening()
        appStateManager.setWakeWordServiceListening(false)
        appStateManager.setVoiceState(VoiceState.IDLE)
    }

    fun release() {
        stopService()

        CoroutineScope(Dispatchers.IO).launch {
            appStateManager.executeSecureVoiceAction {
                Logger.log("Releasing native resources...", TAG)
                recognizer?.close()
                model?.close()
                recognizer = null
                model = null
            }
        }
    }
}