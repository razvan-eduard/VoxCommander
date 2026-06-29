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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val assistantEngine: AssistantEngine,
    private val intentRouter: IntentRouter,
    private val appStateManager: AppStateManager,
    private val languageManager: LanguageManager
) : ViewModel() {

    private val _currentIntent = MutableStateFlow<NluIntent?>(null)
    val currentIntent = _currentIntent.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription = _transcription.asStateFlow()

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
                    _isProcessing.value = false
                    appStateManager.setVoiceState(VoiceState.IDLE)
                }
            }
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
