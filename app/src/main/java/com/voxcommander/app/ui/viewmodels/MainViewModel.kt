package com.voxcommander.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxcommander.app.domain.intent.interpreter.AssistantEngine
import com.voxcommander.app.domain.intent.model.IntentPayload
import com.voxcommander.app.domain.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val assistantEngine: AssistantEngine
) : ViewModel() {

    private val _currentIntent = MutableStateFlow<IntentPayload?>(null)
    val currentIntent = _currentIntent.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription = _transcription.asStateFlow()

    fun processVoiceCommand(voiceLanguage: String, userPreference: String) {
        _isProcessing.value = true
        VoiceManager.startListening(voiceLanguage, userPreference) { text ->
            _transcription.value = text
            viewModelScope.launch {
                try {
                    val result = assistantEngine.processCommand(text)
                    _currentIntent.value = result
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isProcessing.value = false
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
            try {
                val result = assistantEngine.processCommand(text)
                _currentIntent.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
