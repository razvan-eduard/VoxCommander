package com.voxcommander.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.model.NluIntent
import com.voxcommander.app.domain.intent.router.IntentRouter
import com.voxcommander.app.domain.localization.LanguageManager
import com.voxcommander.app.domain.voice.VoiceManager
import com.voxcommander.app.state.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.voxcommander.app.state.VoiceState
import com.voxcommander.app.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val assistantEngine: AssistantEngine,
    private val intentRouter: IntentRouter,
    private val appStateManager: AppStateManager,
    private val languageManager: LanguageManager
) : ViewModel() {

    private val TAG = "MainViewModel"
    private val _currentIntent = MutableStateFlow<NluIntent?>(null)
    val currentIntent = _currentIntent.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription = _transcription.asStateFlow()

    private val commandQueue = mutableListOf<Pair<String, String>>()
    private val queueLock = Any()

    fun processVoiceCommand(voiceLanguage: String, userPreference: String) {
        _isProcessing.value = true
        VoiceManager.startListening(voiceLanguage, userPreference) { text ->
            val cleanText = text.trim()
            _transcription.value = cleanText
            
            val errorPrefix = languageManager.getString("error_prefix")
            if (cleanText.isBlank() || cleanText.startsWith(errorPrefix)) {
                _isProcessing.value = false
                appStateManager.setVoiceState(VoiceState.IDLE)
                return@startListening
            }

            viewModelScope.launch {
                try {
                    appStateManager.setVoiceState(VoiceState.PROCESSING)
                    val result = assistantEngine.processCommand(cleanText, voiceLanguage)
                    _currentIntent.value = result
                    result?.let { withContext(Dispatchers.IO) { intentRouter.route(it) } }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    drainQueueOrIdle(voiceLanguage)
                }
            }
        }
    }

    fun enqueueVoiceCommand(voiceLanguage: String, userPreference: String) {
        Logger.log("Queuing voice command — recording while processing", TAG)
        VoiceManager.startListening(voiceLanguage, userPreference) { text ->
            val cleanText = text.trim()
            val errorPrefix = languageManager.getString("error_prefix")
            if (cleanText.isNotBlank() && !cleanText.startsWith(errorPrefix)) {
                synchronized(queueLock) {
                    commandQueue.add(Pair(cleanText, voiceLanguage))
                    Logger.log("Command queued: '$cleanText' (queue size: ${commandQueue.size})", TAG)
                }
            }
        }
    }

    private fun drainQueueOrIdle(voiceLanguage: String) {
        val next = synchronized(queueLock) {
            if (commandQueue.isEmpty()) null else commandQueue.removeAt(0)
        }

        if (next != null) {
            val (queuedText, queuedLang) = next
            Logger.log("Processing queued command: '$queuedText'", TAG)
            _transcription.value = queuedText
            viewModelScope.launch {
                try {
                    appStateManager.setVoiceState(VoiceState.PROCESSING)
                    val result = assistantEngine.processCommand(queuedText, queuedLang)
                    _currentIntent.value = result
                    result?.let { withContext(Dispatchers.IO) { intentRouter.route(it) } }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    drainQueueOrIdle(queuedLang)
                }
            }
        } else {
            _isProcessing.value = false
            appStateManager.setVoiceState(VoiceState.IDLE)
        }
    }

    fun stopVoiceCommand() {
        VoiceManager.stopListening()
    }

    fun processTextCommand(text: String) {
        _transcription.value = text
        viewModelScope.launch {
            _isProcessing.value = true
            appStateManager.setVoiceState(VoiceState.PROCESSING)
            try {
                val result = assistantEngine.processCommand(text)
                _currentIntent.value = result
                result?.let { withContext(Dispatchers.IO) { intentRouter.route(it) } }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
                appStateManager.setVoiceState(VoiceState.IDLE)
            }
        }
    }
}
