package com.voxcommander.app.domain.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import com.voxcommander.app.utils.Logger
import com.voxcommander.app.utils.Strings
import java.util.Locale

/**
 * Android TextToSpeech wrapper implementing ITtsEngine.
 * Uses the system TTS service — no extra dependencies needed.
 */
class AndroidTtsEngine : ITtsEngine {

    companion object {
        private const val TAG = "AndroidTtsEngine"
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentLanguage: String = "en"
    private var pendingText: String? = null
    private var pendingUtteranceId: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f

    private val utteranceCallbacks = mutableMapOf<String, (() -> Unit)>()

    override fun initialize(context: Context, language: String): Boolean {
        currentLanguage = language
        ready = false

        tts?.stop()
        tts?.shutdown()
        tts = null

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = localeForLanguage(language)
                val setResult = tts?.setLanguage(locale)
                if (setResult == TextToSpeech.LANG_MISSING_DATA || setResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Logger.log("TTS language '$language' not supported (result=$setResult), falling back to default", TAG)
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)

                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        Logger.log("TTS utterance done: $utteranceId", TAG)
                        utteranceId?.let { id ->
                            utteranceCallbacks.remove(id)?.invoke()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Logger.log("TTS error for utterance: $utteranceId", TAG)
                        utteranceId?.let { id ->
                            utteranceCallbacks.remove(id)?.invoke()
                        }
                    }
                })

                ready = true
                Logger.log("Android TTS initialized for language '$language'", TAG)

                // If text was queued before init completed, speak it now
                pendingText?.let { text ->
                    pendingText = null
                    speak(text, pendingUtteranceId, pendingOnDone)
                    pendingUtteranceId = null
                    pendingOnDone = null
                }
            } else {
                Logger.log("Android TTS init failed with status=$status", TAG)
            }
        }

        return true
    }

    override fun speak(text: String, utteranceId: String?, onDone: (() -> Unit)?) {
        if (!ready) {
            Logger.log("TTS not ready yet, queuing text", TAG)
            pendingText = text
            pendingUtteranceId = utteranceId
            pendingOnDone = onDone
            return
        }

        val id = utteranceId ?: "tts_${System.currentTimeMillis()}"
        if (onDone != null) {
            utteranceCallbacks[id] = onDone
        }

        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            Logger.log("TTS speak failed with result=$result", TAG)
            utteranceCallbacks.remove(id)?.invoke()
        }
    }

    override fun stop() {
        tts?.stop()
        utteranceCallbacks.clear()
        pendingText = null
        pendingOnDone = null
    }

    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
        if (ready) tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
        if (ready) tts?.setPitch(pitch)
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        utteranceCallbacks.clear()
        pendingText = null
        pendingOnDone = null
        Logger.log("Android TTS released", TAG)
    }

    private fun localeForLanguage(language: String): Locale {
        return when {
            language.contains("_") -> {
                val parts = language.split("_")
                Locale(parts[0], parts.getOrElse(1) { "" })
            }
            language.length == 2 -> Locale(language)
            else -> Locale(language)
        }
    }
}
