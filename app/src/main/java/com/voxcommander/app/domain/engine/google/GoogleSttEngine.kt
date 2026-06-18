package com.voxcommander.app.domain.engine.google

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log
import com.voxcommander.app.domain.engine.SttEngine
import com.voxcommander.app.utils.Strings

/**
 * Google STT Engine using Intent-based approach to avoid rate limiting.
 * This engine delegates to MainActivity's speechLauncher for actual speech recognition.
 */
class GoogleSttEngine(private val context: Context) : SttEngine {

    var isAvailable = false
    private val TAG = Strings.Tags.GOOGLE_STT_ENGINE

    init {
        isAvailable = checkAvailability()
        Log.d(TAG, "GoogleSttEngine initialized, isAvailable: $isAvailable")
    }

    private fun checkAvailability(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking availability", e)
            false
        }
    }

    override suspend fun transcribe(audio: ByteArray): String {
        return ""
    }

    /**
     * This method is no longer used directly.
     * Intent-based speech is handled by MainActivity's speechLauncher.
     */
    fun startListening(langCode: String, onResult: (String) -> Unit) {
        Log.d(TAG, "startListening called - should use Intent-based approach via MainActivity")
        // This is now handled by VoiceManager.startIntentListening() and MainActivity.startGoogleVoiceIntent()
    }

    fun stopListening() {
        // No-op for Intent-based approach
    }

    fun destroy() {
        // No-op for Intent-based approach
    }
}